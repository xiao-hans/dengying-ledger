package com.meow.ledger.platform

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.meow.ledger.MainActivity

/** 快捷磁贴把「截屏记一笔」作为显式入口；没有截屏结果时不写入账单。 */
class ScreenCaptureTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        // 磁贴只把 ROUTE_CAPTURE 交给 MainActivity；由前台的 Activity 拉起
        // ScreenCaptureActivity 申请 MediaProjection，避免后台启动 Activity 被系统拦截。
        // 授权完成后 App 内会落到待确认队列（不再停在说明页）。
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_ROUTE, ROUTE_CAPTURE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this,
            REQUEST_CAPTURE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { startActivityAndCollapse(pending) }
                .onFailure { runCatching { @Suppress("DEPRECATION") startActivityAndCollapse(intent) } }
        } else {
            @Suppress("DEPRECATION")
            runCatching { startActivityAndCollapse(intent) }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "com.meow.ledger.route"
        const val ROUTE_CAPTURE = "capture"
        /** 截屏完成后的结果通知：只回待确认队列，不再拉起一次授权。 */
        const val ROUTE_PENDING = "pending"
        private const val REQUEST_CAPTURE = 1101
    }
}
