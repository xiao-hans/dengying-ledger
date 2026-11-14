package com.meow.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meow.ledger.shared.auto.AutoRepository
import com.meow.ledger.shared.auto.CaptureResult
import com.meow.ledger.shared.auto.DeepLinkTarget
import com.meow.ledger.shared.auto.KEY_AUTO_BILL
import com.meow.ledger.shared.auto.KEY_KEEP_ALIVE
import com.meow.ledger.shared.auto.SOURCE_OCR
import com.meow.ledger.shared.auto.SettingsStore
import com.meow.ledger.shared.auto.paymentStatusOf
import com.meow.ledger.shared.auto.snapshot
import com.meow.ledger.shared.bills.BillsRepository
import com.meow.ledger.shared.bills.CategoryRepository
import com.meow.ledger.shared.bills.RefundRepository
import com.meow.ledger.shared.bills.RefundTooLarge
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.clearLocalData
import com.meow.ledger.shared.bills.seedCategoriesIfEmpty
import com.meow.ledger.shared.bills.seedIfEmpty
import com.meow.ledger.shared.db.Rule
import com.meow.ledger.shared.manage.AccountRepository
import com.meow.ledger.shared.manage.AssetRepository
import com.meow.ledger.shared.manage.BookRepository
import com.meow.ledger.shared.manage.BudgetRepository
import com.meow.ledger.shared.manage.CatalogRepository
import com.meow.ledger.shared.manage.ExchangeRepository
import com.meow.ledger.shared.manage.FixedBillRepository
import com.meow.ledger.shared.manage.PlanRepository
import com.meow.ledger.shared.manage.TagRepository
import com.meow.ledger.shared.manage.WidgetRepository
import com.meow.ledger.shared.stats.StatsRepository
import com.meow.ledger.shared.stats.TagDrill
import com.meow.ledger.shared.stats.TrendPeriod
import com.meow.ledger.shared.stats.periodSpan
import com.meow.ledger.ui.auto.AppsPage
import com.meow.ledger.ui.auto.AutoHome
import com.meow.ledger.ui.auto.AutoSettingsPage
import com.meow.ledger.ui.auto.CapturePage
import com.meow.ledger.ui.auto.GuidePage
import com.meow.ledger.ui.auto.KeepAlivePage
import com.meow.ledger.ui.auto.OcrPage
import com.meow.ledger.ui.auto.PendingPage
import com.meow.ledger.ui.auto.RulesPage
import com.meow.ledger.ui.bills.BillDetail
import com.meow.ledger.ui.bills.BillEntry
import com.meow.ledger.ui.home.BillsHome
import com.meow.ledger.ui.home.Dock
import com.meow.ledger.ui.manage.AccountPage
import com.meow.ledger.ui.manage.AssetsPage
import com.meow.ledger.ui.manage.BatchPage
import com.meow.ledger.ui.manage.BooksPage
import com.meow.ledger.ui.manage.BudgetPage
import com.meow.ledger.ui.manage.CatalogPage
import com.meow.ledger.ui.manage.ExchangePage
import com.meow.ledger.ui.manage.FixedBillsPage
import com.meow.ledger.ui.manage.HelpPage
import com.meow.ledger.ui.manage.LockGate
import com.meow.ledger.ui.manage.MineHome
import com.meow.ledger.ui.manage.PlansPage
import com.meow.ledger.ui.manage.SearchPage
import com.meow.ledger.ui.manage.WidgetsPage
import com.meow.ledger.ui.stats.StatsHome
import com.meow.ledger.ui.stats.StatsPages
import com.meow.ledger.ui.theme.LocalPalette
import com.meow.ledger.ui.theme.MeowTheme
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 深链进来时预置 tab / 页面变量，让每个入口都落到已有功能，而不是说明页（交互纪律 §6.9）。 */
@Composable
fun MeowApp(
    initialTarget: DeepLinkTarget = DeepLinkTarget(DeepLinkTarget.Kind.MAIN),
    entryHost: EntryHost? = null,
) {
    val settings = SettingsStore(AppGraph.database)
    // 主题状态提升到 MeowApp：入口页与主界面读同一个三态值，切换即时生效（§6.8）。
    // 清空本机数据后由 onTheme 把这里也拉回缺省值，避免界面与库里的设置脱节。
    var theme by rememberSaveable { mutableStateOf(settings.snapshot().theme) }
    EntryNavHost(initialTarget = initialTarget, host = entryHost, theme = theme) { target ->
        MainApp(target = target, theme = theme, onTheme = { theme = it })
    }
}

@Composable
private fun MainApp(
    target: DeepLinkTarget,
    theme: String,
    onTheme: (String) -> Unit,
) {
    val settings = SettingsStore(AppGraph.database)
    // 与 EntryNavHost 用同一份三态深浅色，避免权限页与深链目标页配色错乱（§6.8）。
    val palette = LocalPalette.current
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val today = (now.year * 10000 + now.monthNumber * 100 + now.dayOfMonth).toLong()
    seedIfEmpty(AppGraph.database, today)
    seedCategoriesIfEmpty(AppGraph.database)
    val repository = BillsRepository(AppGraph.database, AppGraph.sync)
    var revision by remember { mutableIntStateOf(0) }
    val view = remember(revision) { repository.month(now.year * 100 + now.monthNumber) }
    val categories = CategoryRepository(AppGraph.database).ofType(1) + CategoryRepository(AppGraph.database).ofType(2)

    // 深链把 tab / 子页预置好；返回后是同一个带 Dock 的主界面，不会卡在孤立页。
    var tab by rememberSaveable {
        mutableIntStateOf(
            when (target.kind) {
                DeepLinkTarget.Kind.BILL_EDIT -> 0
                DeepLinkTarget.Kind.TICKET, DeepLinkTarget.Kind.CAPTURE, DeepLinkTarget.Kind.PENDING -> 2
                DeepLinkTarget.Kind.PAYMENT, DeepLinkTarget.Kind.WEBVIEW -> 3
                else -> 0
            },
        )
    }
    var composing by rememberSaveable { mutableStateOf(target.kind == DeepLinkTarget.Kind.BILL_EDIT) }
    var openBill by rememberSaveable {
        mutableStateOf(
            if (target.kind == DeepLinkTarget.Kind.BILL_EDIT && target.clientId.isNotBlank() &&
                repository.get(0, target.clientId) != null
            ) {
                target.clientId
            } else {
                null
            },
        )
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var detailMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var statsType by rememberSaveable { mutableStateOf(TYPE_EXPENSE) }
    var statsPeriod by rememberSaveable { mutableStateOf(TrendPeriod.MONTH) }
    var statsPage by rememberSaveable { mutableStateOf("") }
    val auto = AutoRepository(AppGraph.database)
    // 这些状态镜像 user_setting，开机与「清空本机数据」后都从同一份快照取值。
    val initial = remember { settings.snapshot() }
    var autoEnabled by rememberSaveable { mutableStateOf(initial.autoBill) }
    var keepAlive by rememberSaveable { mutableStateOf(initial.keepAlive) }
    var watchApps by rememberSaveable { mutableStateOf(initial.watchApps) }
    var lockPin by rememberSaveable { mutableStateOf(initial.lockPin) }
    var unlocked by rememberSaveable { mutableStateOf(initial.lockPin.isEmpty()) }
    var autoPage by rememberSaveable {
        mutableStateOf(
            when (target.kind) {
                DeepLinkTarget.Kind.TICKET -> "ocr"
                DeepLinkTarget.Kind.CAPTURE, DeepLinkTarget.Kind.PENDING -> "pending"
                else -> ""
            },
        )
    }
    var minePage by rememberSaveable {
        mutableStateOf(
            when (target.kind) {
                DeepLinkTarget.Kind.SCAN -> "book-join"
                DeepLinkTarget.Kind.PAYMENT -> "vip"
                DeepLinkTarget.Kind.WEBVIEW -> "help"
                else -> ""
            },
        )
    }
    var captureSource by rememberSaveable { mutableStateOf("") }
    var captureMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // 支付回调的状态与订单号一起显示在会员页，用户可自行核对，绝不冒充支付成功。
    var paymentNotice by rememberSaveable {
        mutableStateOf(
            if (target.kind == DeepLinkTarget.Kind.PAYMENT) {
                val order = target.clientId
                val prefix = if (order.isBlank()) "" else "订单 $order："
                prefix + paymentStatusOf(target.paymentResult)
            } else {
                ""
            },
        )
    }

    /**
     * 「清空本机数据」的完整后果：清库后设置行被整体删掉，镜像设置的内存状态必须一起
     * 回到缺省值，否则界面与库脱节（例如仍显示深色、自动记账仍开）。同时回到「我的」首页
     * 并 bump revision，强制账单视图从重建后的库重新读（AGENTS.md §6.8 / §6.9）。
     */
    fun clearAndReset() {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = (now.year * 10000 + now.monthNumber * 100 + now.dayOfMonth).toLong()
        clearLocalData(AppGraph.database, today)
        val fresh = settings.snapshot()
        onTheme(fresh.theme)
        autoEnabled = fresh.autoBill
        keepAlive = fresh.keepAlive
        watchApps = fresh.watchApps
        lockPin = fresh.lockPin
        unlocked = fresh.lockPin.isEmpty()
        tab = 3
        minePage = ""
        autoPage = ""
        statsPage = ""
        composing = false
        editing = false
        openBill = null
        detailMessage = null
        captureMessage = null
        paymentNotice = ""
        revision++
    }

    // onNewIntent 再次带深链进来时，复用同一套预置逻辑，不需要重建 Activity。
    var appliedKey by rememberSaveable { mutableStateOf("") }
    val targetKey = with(target) { "$kind|$clientId|$url|$paymentResult|$launchId" }
    LaunchedEffect(targetKey) {
        if (targetKey == appliedKey) return@LaunchedEffect
        appliedKey = targetKey
        applyTarget(
            target,
            repository,
            setTab = { tab = it },
            setComposing = { composing = it },
            setOpenBill = { openBill = it },
            setAutoPage = { autoPage = it },
            setMinePage = { minePage = it },
            setPaymentNotice = { paymentNotice = it },
        )
    }

    MeowTheme(theme) {
        if (!unlocked) {
            Box(Modifier.fillMaxSize().background(palette.appBg).statusBarsPadding()) {
                LockGate(lockPin) { unlocked = true }
            }
            return@MeowTheme
        }
        // 系统返回键：二级页回上级，根页面才交给系统退出（交互纪律 §6.9）。
        BackButton(
            enabled = composing || editing || openBill != null ||
                statsPage.isNotEmpty() || autoPage.isNotEmpty() || minePage.isNotEmpty(),
            onBack = {
                when {
                    composing || editing -> { composing = false; editing = false }
                    openBill != null -> { openBill = null; detailMessage = null }
                    statsPage.isNotEmpty() -> statsPage = ""
                    autoPage.isNotEmpty() -> autoPage = ""
                    minePage.isNotEmpty() -> minePage = ""
                }
            },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.appBg)
                .statusBarsPadding(),
        ) {
            val opened = openBill?.let { repository.get(0, it) }
            if (composing || (editing && opened != null)) {
                BillEntry(
                    today = today,
                    categories = categories,
                    existing = if (editing) opened else null,
                    onBack = { composing = false; editing = false },
                    onSave = { bill ->
                        repository.saveWithBalance(bill)
                        revision++
                        if (editing) openBill = bill.client_id
                        composing = false
                        editing = false
                    },
                )
            } else if (opened != null) {
                val refunds = RefundRepository(AppGraph.database)
                BillDetail(
                    bill = opened,
                    categoryName = categories.firstOrNull { it.id == opened.member_cate_id }?.title ?: "未分类",
                    refunds = refunds.ofBill(opened.client_id),
                    message = detailMessage,
                    onBack = { openBill = null; detailMessage = null },
                    onEdit = { editing = true },
                    onDelete = { repository.deleteWithBalance(0, opened.client_id); openBill = null; revision++ },
                    onEditRefund = { id, cents ->
                        val refund = refunds.ofBill(opened.client_id).firstOrNull { it.id == id }
                        detailMessage = try {
                            if (refund == null) "退款不存在" else { refunds.update(refund, cents, opened.money); revision++; null }
                        } catch (e: RefundTooLarge) {
                            "退款合计不能超过原账单金额"
                        }
                    },
                    onAddRefund = { cents ->
                        detailMessage = try {
                            refunds.add(opened.client_id, opened.money, cents, today)
                            revision++
                            null
                        } catch (e: RefundTooLarge) {
                            "退款合计不能超过原账单金额"
                        }
                    },
                    onRevokeRefund = { id -> refunds.remove(id); detailMessage = null; revision++ },
                )
            } else {
                when (tab) {
                    0 -> BillsHome(
                        view.summary,
                        view.groups,
                        onAdd = { composing = true },
                        onOpen = { openBill = it; detailMessage = null },
                    )
                    1 -> {
                        val stats = StatsRepository(AppGraph.database)
                        val span = periodSpan(statsPeriod, 12)
                        val names = categories.associate { it.id to it.title }
                        val month = today / 100
                        val year = today / 10000
                        if (statsPage.isNotEmpty()) {
                            val drill = TagDrill(AppGraph.database)
                            val inMonth = if (statsPage.startsWith("tag:")) {
                                drill.billsOfTag(month * 100 + 1, month * 100 + 31, statsPage.substringAfter(':').toLongOrNull() ?: 0L, statsType)
                            } else {
                                stats.billsOfPeriod(month * 100 + 1, month * 100 + 31, statsType)
                            }
                            StatsPages(
                                page = statsPage,
                                month = month,
                                year = year,
                                type = statsType,
                                names = names,
                                bills = inMonth,
                                refunds = RefundRepository(AppGraph.database).all(),
                                tags = TagRepository(AppGraph.database).list(1),
                                months = stats.monthsOfYear(year),
                                onOpenPage = { statsPage = it },
                                onOpenBill = { openBill = it; detailMessage = null },
                                onType = { statsType = it },
                                onBack = { statsPage = "" },
                            )
                        } else {
                            StatsHome(
                                summary = stats.summary(span.first, span.second, statsType, { id -> names[id] ?: "未分类" }),
                                trend = stats.trend(statsPeriod),
                                type = statsType,
                                period = statsPeriod,
                                onType = { statsType = it },
                                onPeriod = { statsPeriod = it },
                                onOpenCate = { statsPage = "cate:$it" },
                                onOpenTags = { statsPage = "tags" },
                                onOpenType = { statsPage = "type" },
                                onOpenReport = { statsPage = "report" },
                            )
                        }
                    }
                    2 -> if (autoPage.isNotEmpty()) {
                        AutoPages(
                            page = autoPage,
                            auto = auto,
                            settings = settings,
                            captureSource = captureSource,
                            captureMessage = captureMessage,
                            categories = categories,
                            keepAlive = keepAlive,
                            watchApps = watchApps,
                            onCaptureMessage = { captureMessage = it },
                            onKeepAlive = { keepAlive = it },
                            onWatchApps = { watchApps = it },
                            onBack = { autoPage = "" },
                            onOpenPending = { autoPage = "pending" },
                        )
                    } else {
                        AutoHome(
                            enabled = autoEnabled,
                            pendingCount = auto.pending().size,
                            ruleCount = auto.rules().size,
                            watchCount = watchApps.size,
                            onToggle = { on ->
                                autoEnabled = on
                                settings.setFlag(KEY_AUTO_BILL, on)
                                if (on && watchApps.isEmpty()) autoPage = "guide"
                            },
                            onCapture = { source -> captureSource = source; captureMessage = null; autoPage = "capture" },
                            onOcr = { autoPage = "ocr" },
                            onPending = { autoPage = "pending" },
                            onRules = { autoPage = "rules" },
                            onApps = { autoPage = "apps" },
                            onKeepAlive = { autoPage = "keepalive" },
                            onGuide = { autoPage = "guide" },
                            onSettings = { autoPage = "settings" },
                        )
                    }
                    else -> if (minePage.isNotEmpty()) {
                        MinePages(
                            minePage,
                            settings,
                            onTheme,
                            ::clearAndReset,
                            { minePage = it },
                            { minePage = "" },
                            { openBill = it },
                            paymentNotice,
                        )
                    } else {
                        MineHome(AccountRepository(AppGraph.database).profile()) { minePage = it }
                    }
                }
                if ((tab != 2 || autoPage.isEmpty()) && (tab != 3 || minePage.isEmpty())) Dock(
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                )
            }
        }
    }
}

/** 把深链目标落到状态机；冷启动与 onNewIntent 共用，避免两套不一致的跳转。 */
private fun applyTarget(
    target: DeepLinkTarget,
    repository: BillsRepository,
    setTab: (Int) -> Unit,
    setComposing: (Boolean) -> Unit,
    setOpenBill: (String?) -> Unit,
    setAutoPage: (String) -> Unit,
    setMinePage: (String) -> Unit,
    setPaymentNotice: (String) -> Unit,
) {
    when (target.kind) {
        DeepLinkTarget.Kind.BILL_EDIT -> {
            setTab(0)
            val existing = target.clientId.takeIf { it.isNotBlank() }?.let { repository.get(0, it) }
            if (existing == null) {
                // 没有 clientId 或账单不存在 → 新建一笔；带 clientId 的旧账单 → 打开详情可编辑。
                setOpenBill(null)
                setComposing(true)
            } else {
                setComposing(false)
                setOpenBill(target.clientId)
            }
        }
        DeepLinkTarget.Kind.TICKET -> { setTab(2); setAutoPage("ocr") }
        // 截屏采集由平台侧服务拉起，App 内落到待确认队列，用户确认后才入账。
        DeepLinkTarget.Kind.CAPTURE -> { setTab(2); setAutoPage("pending") }
        // 截屏结果通知只回待确认队列，不再拉起授权。
        DeepLinkTarget.Kind.PENDING -> { setTab(2); setAutoPage("pending") }
        DeepLinkTarget.Kind.SCAN -> { setTab(3); setMinePage("book-join") }
        DeepLinkTarget.Kind.PAYMENT -> {
            setTab(3)
            setMinePage("vip")
            val prefix = if (target.clientId.isBlank()) "" else "订单 ${target.clientId}："
            setPaymentNotice(prefix + paymentStatusOf(target.paymentResult))
        }
        DeepLinkTarget.Kind.WEBVIEW -> { setTab(3); setMinePage("help") }
        DeepLinkTarget.Kind.PERMISSION, DeepLinkTarget.Kind.MAIN -> {
            setTab(0)
            setComposing(false)
            setOpenBill(null)
            setAutoPage("")
            setMinePage("")
            setPaymentNotice("")
        }
    }
}

@Composable
private fun AutoPages(
    page: String,
    auto: AutoRepository,
    settings: SettingsStore,
    captureSource: String,
    captureMessage: String?,
    categories: List<com.meow.ledger.shared.db.Category>,
    keepAlive: Boolean,
    watchApps: Set<String>,
    onCaptureMessage: (String) -> Unit,
    onKeepAlive: (Boolean) -> Unit,
    onWatchApps: (Set<String>) -> Unit,
    onBack: () -> Unit,
    onOpenPending: () -> Unit,
) {
    when (page) {
        "capture" -> CapturePage(captureSource, captureMessage, onBack) { text ->
            val result = auto.capture(text, captureSource)
            onCaptureMessage(captureSummary(result))
        }
        "ocr" -> OcrPage(
            onBack,
            onCaptureText = { text -> captureSummary(auto.captureTicket(text, SOURCE_OCR)) },
            onOpenPending = onOpenPending,
        )
        "pending" -> PendingPage(
            events = auto.pending(),
            categories = categories,
            onBack = onBack,
            onConfirmOne = { id, edit -> auto.pendingById(id)?.let { auto.confirm(it, edit = edit) } },
            onBatchConfirm = { ids, edits -> auto.confirmAll(ids, edits) },
            onBatchIgnore = { ids -> auto.ignoreAll(ids) },
        )
        "rules" -> RulesPage(auto.rules(), onBack, onAdd = { keyword, type ->
            auto.saveRule(
                Rule(
                    id = auto.nextRuleId(),
                    key_word = keyword,
                    source = 0,
                    member_id = 0,
                    type = type,
                    book_id = 0,
                    member_cate_id = if (type == TYPE_EXPENSE) 9 else 101,
                    assets_id = 1,
                    tag_ids = "",
                    create_date = 0,
                    update_date = 0,
                ),
            )
        }, onDelete = { auto.deleteRule(it) })
        "apps" -> AppsPage(watchApps, onBack) { pkg ->
            val next = if (pkg in watchApps) watchApps - pkg else watchApps + pkg
            onWatchApps(next)
            settings.setWatchApps(next)
        }
        "keepalive" -> KeepAlivePage(keepAlive, onBack) { on ->
            onKeepAlive(on)
            settings.setFlag(KEY_KEEP_ALIVE, on)
        }
        "guide" -> GuidePage(onBack)
        "settings" -> AutoSettingsPage(onBack)
    }
}

@Composable
private fun MinePages(
    page: String,
    settings: SettingsStore,
    onTheme: (String) -> Unit,
    onClear: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onOpenBill: (String) -> Unit,
    paymentNotice: String = "",
) {
    val database = AppGraph.database
    val month = com.meow.ledger.shared.stats.periodSpan(com.meow.ledger.shared.stats.TrendPeriod.MONTH, 1).second / 100
    when {
        page.startsWith("budget") -> BudgetPage(BudgetRepository(database), month, page, onOpen, onBack)
        page == "search" -> SearchPage(ExchangeRepository(database), onOpenBill, onBack)
        page == "batch" -> BatchPage(onBack)
        page == "export" || page.startsWith("import") -> ExchangePage(ExchangeRepository(database), AccountRepository(database), page, onOpen, onBack)
        page.startsWith("book") -> BooksPage(BookRepository(database), page, onOpen, onBack)
        page.startsWith("asset") -> AssetsPage(AssetRepository(database), page, onOpen, onBack)
        page.startsWith("categor") || page.startsWith("tag") -> CatalogPage(CatalogRepository(database), page, onOpen, onBack)
        page.startsWith("fixed") -> FixedBillsPage(FixedBillRepository(database), page, onOpen, onBack)
        page.startsWith("installment") || page.startsWith("save-plan") -> PlansPage(PlanRepository(database), page, onOpen, onBack)
        page.startsWith("widget") -> WidgetsPage(WidgetRepository(database), month, page, onOpen, onBack)
        page == "help" -> HelpPage(onBack)
        else -> AccountPage(
            page,
            database,
            AccountRepository(database),
            settings,
            onTheme,
            onClear,
            onOpen,
            onBack,
            paymentNotice,
        )
    }
}

private fun captureSummary(result: CaptureResult): String {
    val parts = mutableListOf<String>()
    if (result.created > 0) parts += "${result.created} 条已加入待确认"
    if (result.duplicate > 0) parts += "${result.duplicate} 条重复已跳过"
    if (result.unsupported > 0) parts += "${result.unsupported} 条没有识别出金额"
    return if (parts.isEmpty()) "没有可识别的内容" else parts.joinToString("，")
}
