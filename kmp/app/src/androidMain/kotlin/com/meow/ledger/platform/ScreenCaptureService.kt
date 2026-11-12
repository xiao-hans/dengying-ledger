package com.meow.ledger.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.meow.ledger.MainActivity
import com.meow.ledger.R
import com.meow.ledger.shared.auto.SOURCE_SCREENSHOT
import com.meow.ledger.ui.AndroidHost
import com.meow.ledger.ui.AppGraph
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 截屏采集（第三路）：前台服务创建 VirtualDisplay + ImageReader，只抓一帧，
 * 交给 ML Kit 中文识别，再把文本喂给 CaptureBridge → 待确认队列（绝不自动入账）。
 * 抓完立即释放投影并停止服务；任何一步失败都有可读兜底，不静默吞错。
 *
 * 每个采集会话的全部状态都在 [CaptureSession] 里：投影、回调、超时、帧标记、
 * finished CAS 和本次的 startId 都随会话走。服务只持有 [current]，收尾凭会话
 * 身份判断，不再读取可被下一次 onStartCommand 改写的全局 startId/generation。
 */
class ScreenCaptureService : Service() {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var mainHandler: Handler? = null

    /** 服务级线程/Handler 可跨会话复用；资源与状态一律归 [CaptureSession]。 */
    @Volatile
    private var current: CaptureSession? = null

    /** 一个会话 = 一次抓帧。字段只由采集线程/主线程在所属会话内存取。 */
    private inner class CaptureSession(val startId: Int) {
        val finished = AtomicBoolean(false)
        val frameReceived = AtomicBoolean(false)
        var projection: MediaProjection? = null
        var projectionCallback: MediaProjection.Callback? = null
        var frameTimeout: Runnable? = null
        var totalTimeout: Runnable? = null
        var virtualDisplay: VirtualDisplay? = null
        var imageReader: ImageReader? = null
        /** 帧到手后释放 VirtualDisplay/ImageReader 的 main-thread 任务，收尾时摘掉。 */
        var pendingRelease: Runnable? = null
    }

    override fun onCreate() {
        super.onCreate()
        AndroidHost.appContext = applicationContext
        createChannel()
    }

    /** 主线程执行：每次 onStartCommand 串行到这里，finished CAS 与资源释放可跨线程。 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val session = current
            if (session != null && !session.finished.get()) {
                stopGracefully("已停止截屏采集", session)
            } else {
                // 没有进行中的会话：摘掉前台通知，按本次 startId 停止即可。
                stopForegroundRemove()
                stopSelfResult(startId)
            }
            return START_NOT_STICKY
        }
        // 通知在 5 秒内必须挂上，避免 mediaProjection 类型前台服务被系统直接杀掉。
        val notification = runCatching { foregroundNotification() }.getOrElse {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(getString(R.string.capture_service_title))
                .build()
        }
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        if (started.isFailure) {
            failStart("前台服务启动失败，截屏采集未执行", startId)
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, ActivityResultNone) ?: ActivityResultNone
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (code == ActivityResultNone || data == null) {
            failStart("没有拿到截屏授权，本次采集已取消", startId)
            return START_NOT_STICKY
        }
        val manager = runCatching {
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        }.getOrNull()
        if (manager == null) {
            failStart("系统不提供截屏能力，本次采集已取消", startId)
            return START_NOT_STICKY
        }
        val mp = runCatching { manager.getMediaProjection(code, data) }.getOrNull()
        if (mp == null) {
            failStart("截屏授权已失效，请重新点磁贴授权", startId)
            return START_NOT_STICKY
        }
        val alive = current
        if (alive != null && !alive.finished.get()) {
            // 会话进行中：停掉本次新拿到的投影，不打断正在进行的采集，也不覆盖 current。
            runCatching { mp.stop() }
            runCatching { notifyResult("已有截屏采集在进行中") }
            return START_NOT_STICKY
        }
        ensureCaptureThread() ?: run {
            runCatching { mp.stop() }
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        // 无活会话才启动新会话。startId 只在真正启动时捕获，且随会话不可变。
        val session = CaptureSession(startId)
        session.projection = mp
        // 先赋值 current，再释放旧会话资源：旧投影的 onStop 只会打到已 finished 的旧会话。
        val previous = current
        current = session
        // Android 14+ 要求在 VirtualDisplay 创建前注册回调，投影销毁时一并清理。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    // 回调绑定本会话：finished 已 true 时 CAS 失败，收尾自然成为 no-op。
                    stopGracefully("截屏已停止", session)
                }
            }
            session.projectionCallback = callback
            mp.registerCallback(callback, handler)
        }
        previous?.finishQuietly()
        previous?.let { releaseSession(it) }

        if (!startCapture(session, mp)) stopGracefully("当前屏幕无法创建截屏画面，请解锁屏幕后重试", session)
        return START_NOT_STICKY
    }

    /** 服务级采集线程只建一次；创建失败时调用方回退 stopSelfResult，不启动会话。 */
    private fun ensureCaptureThread(): Handler? {
        handler?.let { return it }
        val h = runCatching {
            val t = HandlerThread("meow-capture").also { it.start() }
            thread = t
            Handler(t.looper)
        }.getOrNull() ?: return null
        handler = h
        if (mainHandler == null) mainHandler = Handler(Looper.getMainLooper())
        return h
    }

    /** 只取一帧；尺寸取真实屏幕但不超过 1280，避免大 bitmap 触发 OOM。主线程执行。 */
    private fun startCapture(session: CaptureSession, mp: MediaProjection): Boolean {
        val metrics = DisplayMetrics()
        val wm = runCatching {
            getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        }.getOrNull() ?: return false
        @Suppress("DEPRECATION")
        runCatching { wm.defaultDisplay?.getRealMetrics(metrics) }
            .onFailure { return false }
        val scale = if (metrics.widthPixels > 1280) 1280f / metrics.widthPixels else 1f
        val width = maxOf(320, (metrics.widthPixels * scale).toInt())
        val height = maxOf(320, (metrics.heightPixels * scale).toInt())
        val density = metrics.densityDpi.takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT

        val reader = runCatching {
            ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        }.getOrNull() ?: return false
        session.imageReader = reader
        handler?.let {
            reader.setOnImageAvailableListener({ r -> onFrame(session, r) }, it)
        } ?: run { reader.close(); session.imageReader = null; return false }

        val display = runCatching {
            mp.createVirtualDisplay(
                "meow-capture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            )
        }.getOrNull()
        if (display == null) {
            reader.close()
            session.imageReader = null
            return false
        }
        session.virtualDisplay = display
        // 兜底：3 秒还没有帧（锁屏/黑屏）就自己收尾，避免通知常驻。
        // 拿到帧后立刻取消这个超时，给 ML Kit 异步识别留足时间。
        session.frameTimeout = Runnable {
            if (!session.finished.get() && !session.frameReceived.get()) {
                stopGracefully("没有捕获到屏幕内容，请解锁屏幕后重试", session)
            }
        }
        session.frameTimeout?.let { handler?.postDelayed(it, FRAME_TIMEOUT_MS) }
        // 再加一道总超时：即使 ML Kit 回调因机型差异没触发，通知也不会常驻。
        session.totalTimeout = Runnable {
            if (!session.finished.get()) stopGracefully("识别超时，本次未入账，请在待确认队列核对", session)
        }
        session.totalTimeout?.let { handler?.postDelayed(it, TOTAL_TIMEOUT_MS) }
        return true
    }

    private fun onFrame(session: CaptureSession, reader: ImageReader) {
        if (session.finished.get() || !session.frameReceived.compareAndSet(false, true)) return
        session.frameTimeout?.let { handler?.removeCallbacks(it) }
        val image = runCatching { reader.acquireLatestImage() }.getOrNull()
        if (image == null) {
            stopGracefully("没有捕获到屏幕内容，请解锁屏幕后重试", session)
            return
        }
        val bitmap = runCatching { imageToBitmap(image) }.also {
            runCatching { image.close() }
        }.getOrNull()
        // 一帧到手立即停投影，绝不持续录屏；释放统一交给主线程，避免与收尾竞态。
        // 只关帧监听并释放 VirtualDisplay / ImageReader，不 stop 投影（stop 会触发 onStop 回调）。
        val release = Runnable { releaseCaptureResources(session) }
        session.pendingRelease = release
        mainHandler?.post(release)
        if (bitmap == null) {
            stopGracefully("截屏画面转换失败，本次未入账", session)
            return
        }
        recognize(session, bitmap)
    }

    /** ImageReader 的 RGBA plane 可能带 row padding，用 rowStride 逐行拷进 Bitmap。 */
    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun recognize(session: CaptureSession, bitmap: Bitmap) {
        val recognizer = runCatching {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }.getOrElse {
            stopGracefully("文字识别引擎不可用，本次未入账", session)
            bitmap.recycle()
            return
        }
        val image = runCatching { InputImage.fromBitmap(bitmap, 0) }.getOrElse {
            stopGracefully("截屏画面无法交给文字识别，本次未入账", session)
            runCatching { recognizer.close() }
            bitmap.recycle()
            return
        }
        // 显式把 ML Kit 的 Task 回调派发到主线程，服务进程里也能保证一定触发。
        val mainExecutor = java.util.concurrent.Executor { runnable -> Handler(mainLooper).post(runnable) }
        val done = {
            runCatching { recognizer.close() }
            bitmap.recycle()
        }
        runCatching {
            recognizer.process(image)
                .addOnSuccessListener(mainExecutor) { vision ->
                    val raw = vision.text.trim()
                    if (raw.isEmpty()) {
                        stopGracefully("截图里没有识别到文字，本次未入账", session)
                    } else {
                        CaptureBridge.submitScreenCapture(raw)
                        stopGracefully("截图已识别，结果进入待确认队列，确认后才入账", session)
                    }
                    done()
                }
                .addOnFailureListener(mainExecutor) {
                    stopGracefully("文字识别失败，本次未入账", session)
                    done()
                }
        }.onFailure {
            stopGracefully("文字识别失败，本次未入账", session)
            done()
        }
    }

    /**
     * 任意线程：只对指定会话 CAS 一次，判定与收尾原子地绑定在会话上。
     * CAS 赢了才把真正的收尾派发到主线程串行执行。
     */
    private fun stopGracefully(message: String, session: CaptureSession) {
        if (!session.finished.compareAndSet(false, true)) return
        val h = mainHandler
        if (h != null && h.looper != Looper.myLooper()) {
            h.post { finishSessionOnMain(session, message) }
        } else {
            finishSessionOnMain(session, message)
        }
    }

    /** 主线程收尾：只清本会话资源；仅当它仍是 current 时才清 current / 发通知 / 停服务。 */
    private fun finishSessionOnMain(session: CaptureSession, message: String) {
        session.cancelTimeouts()
        releaseSession(session)
        if (current !== session) return // 已被新会话接替：旧收尾到此为止，不碰服务状态
        current = null
        // 非用户主动停止、且时间还够时才发结果通知，避免覆盖前台通知或重复提示。
        if (message != "已停止截屏采集") runCatching { notifyResult(message) }
        if (message.isNotEmpty() && !message.contains("待确认")) {
            // 没有识别结果时也留一条本地埋点，方便在「我的」页核对失败原因。
            runCatching {
                com.meow.ledger.shared.manage.AccountRepository(AppGraph.database)
                    .track("ac_capture_failed", "$SOURCE_SCREENSHOT|$message")
            }
        }
        stopForegroundRemove()
        // 用本会话不可变的 startId 条件停止：若期间已有更新的 onStartCommand，
        // 这次 stopSelfResult 会变成 no-op，不会误杀新会话。
        stopSelfResult(session.startId)
    }

    /** 早退失败路径（本次未启动新会话）：清掉残留旧会话，通知用户并按本次 startId 停服务。 */
    private fun failStart(message: String, startId: Int) {
        val stale = current
        if (stale != null && stale.finished.compareAndSet(false, true)) {
            stale.cancelTimeouts()
            releaseSession(stale)
            current = null
        }
        runCatching { notifyResult(message) }
        runCatching {
            com.meow.ledger.shared.manage.AccountRepository(AppGraph.database)
                .track("ac_capture_failed", "$SOURCE_SCREENSHOT|$message")
        }
        stopForegroundRemove()
        stopSelfResult(startId)
    }

    /** 旧会话被新会话接替时静默淘汰：直接置 finished，其投影 onStop 回调便成 no-op。 */
    private fun CaptureSession.finishQuietly() {
        finished.set(true)
        cancelTimeouts()
    }

    private fun CaptureSession.cancelTimeouts() {
        frameTimeout?.let { handler?.removeCallbacks(it) }
        totalTimeout?.let { handler?.removeCallbacks(it) }
        pendingRelease?.let { mainHandler?.removeCallbacks(it) }
        frameTimeout = null
        totalTimeout = null
        pendingRelease = null
    }

    /** 只由主线程（或 mainHandler 为空的兜底）调用。 */
    private fun releaseCaptureResources(session: CaptureSession) {
        runCatching { session.imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { session.virtualDisplay?.release() }
        session.virtualDisplay = null
        runCatching { session.imageReader?.close() }
        session.imageReader = null
    }

    /** 收尾 / 重启淘汰时调用：释放资源后 stop 投影，其 onStop 由该会话的 finished 兜住。 */
    private fun releaseSession(session: CaptureSession) {
        releaseCaptureResources(session)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            session.projectionCallback?.let { runCatching { session.projection?.unregisterCallback(it) } }
            session.projectionCallback = null
        }
        runCatching { session.projection?.stop() }
        session.projection = null
    }

    private fun stopForegroundRemove() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    override fun onDestroy() {
        // 先清空采集线程上所有待执行消息，再退出 Looper，避免残留超时回调触碰已置空的 handler。
        runCatching { handler?.removeCallbacksAndMessages(null) }
        current?.let { session ->
            session.finished.set(true)
            session.cancelTimeouts()
            releaseSession(session)
        }
        current = null
        runCatching { thread?.quitSafely() }
        thread = null
        handler = null
        mainHandler = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.capture_channel_name), NotificationManager.IMPORTANCE_LOW)
            runCatching {
                (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.createNotificationChannel(channel)
            }
        }
    }

    private fun foregroundNotification(): Notification {
        // 用 PendingIntent.getService 直接停服务，避免后台启动 Activity 被系统拦截。
        val cancel = PendingIntent.getService(
            this,
            REQUEST_CANCEL,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .putExtra(ScreenCaptureTileService.EXTRA_ROUTE, ScreenCaptureTileService.ROUTE_PENDING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.capture_service_title))
            .setContentText(getString(R.string.capture_service_body))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.capture_service_stop), cancel).build())
            .setOngoing(true)
            .build()
    }

    private fun notifyResult(message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val open = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .putExtra(ScreenCaptureTileService.EXTRA_ROUTE, ScreenCaptureTileService.ROUTE_PENDING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val note = builder
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.capture_service_title))
            .setContentText(message)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID + 1, note)
    }

    companion object {
        const val EXTRA_RESULT_CODE = "com.meow.ledger.capture.resultCode"
        const val EXTRA_RESULT_DATA = "com.meow.ledger.capture.resultData"
        const val ACTION_STOP = "com.meow.ledger.capture.stop"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 3301
        private const val REQUEST_CANCEL = 3302
        private const val REQUEST_OPEN = 3303
        private const val FRAME_TIMEOUT_MS = 3000L
        private const val TOTAL_TIMEOUT_MS = 15000L
        private const val ActivityResultNone = Int.MIN_VALUE
    }
}
