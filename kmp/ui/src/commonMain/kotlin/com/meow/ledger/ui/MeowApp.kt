package com.meow.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.meow.ledger.shared.auto.KEY_APP_LOCK
import com.meow.ledger.shared.auto.KEY_AUTO_BILL
import com.meow.ledger.shared.auto.KEY_AUTO_CONFIRM
import com.meow.ledger.shared.auto.KEY_KEEP_ALIVE
import com.meow.ledger.shared.auto.SettingsStore
import com.meow.ledger.shared.bills.BillsRepository
import com.meow.ledger.shared.db.Rule
import com.meow.ledger.shared.bills.CategoryRepository
import com.meow.ledger.shared.bills.RefundRepository
import com.meow.ledger.ui.manage.BatchPage
import com.meow.ledger.shared.bills.RefundTooLarge
import com.meow.ledger.shared.bills.seedCategoriesIfEmpty
import com.meow.ledger.shared.bills.seedIfEmpty
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.stats.StatsRepository
import com.meow.ledger.shared.manage.TagRepository
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
import com.meow.ledger.shared.auto.KEY_THEME
import com.meow.ledger.shared.auto.THEME_DARK
import com.meow.ledger.shared.auto.THEME_LIGHT
import com.meow.ledger.shared.auto.THEME_SYSTEM
import com.meow.ledger.shared.manage.AccountRepository
import com.meow.ledger.shared.manage.AssetRepository
import com.meow.ledger.shared.manage.BookRepository
import com.meow.ledger.shared.manage.CatalogRepository
import com.meow.ledger.shared.manage.BudgetRepository
import com.meow.ledger.shared.manage.ExchangeRepository
import com.meow.ledger.shared.manage.FixedBillRepository
import com.meow.ledger.shared.manage.PlanRepository
import com.meow.ledger.shared.manage.WidgetRepository
import com.meow.ledger.ui.manage.AccountPage
import com.meow.ledger.ui.manage.AssetsPage
import com.meow.ledger.ui.manage.BooksPage
import com.meow.ledger.ui.manage.CatalogPage
import com.meow.ledger.ui.manage.BudgetPage
import com.meow.ledger.ui.manage.ExchangePage
import com.meow.ledger.ui.manage.FixedBillsPage
import com.meow.ledger.ui.manage.HelpPage
import com.meow.ledger.ui.manage.LockGate
import com.meow.ledger.ui.manage.MineHome
import com.meow.ledger.ui.manage.SearchPage
import com.meow.ledger.ui.manage.WidgetsPage
import com.meow.ledger.ui.manage.PlansPage
import com.meow.ledger.ui.stats.StatsHome
import com.meow.ledger.ui.stats.StatsPages
import com.meow.ledger.ui.theme.DarkPalette
import com.meow.ledger.ui.theme.LightPalette
import com.meow.ledger.ui.theme.LocalPalette
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun MeowApp(
    initialRoute: String = "",
    entryHost: EntryHost? = null,
) {
    EntryNavHost(initialRoute = initialRoute, host = entryHost) {
        MainApp()
    }
}

@Composable
private fun MainApp() {
    val settings = SettingsStore(AppGraph.database)
    var theme by rememberSaveable { mutableStateOf(settings.text(KEY_THEME, THEME_SYSTEM)) }
    val dark = when (theme) {
        THEME_DARK -> true
        THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val today = (now.year * 10000 + now.monthNumber * 100 + now.dayOfMonth).toLong()
    seedIfEmpty(AppGraph.database, today)
    seedCategoriesIfEmpty(AppGraph.database)
    val repository = BillsRepository(AppGraph.database)
    var revision by remember { mutableIntStateOf(0) }
    val view = remember(revision) { repository.month(now.year * 100 + now.monthNumber) }
    val categories = CategoryRepository(AppGraph.database).ofType(1) + CategoryRepository(AppGraph.database).ofType(2)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var composing by rememberSaveable { mutableStateOf(false) }
    var openBill by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var detailMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var statsType by rememberSaveable { mutableStateOf(TYPE_EXPENSE) }
    var statsPeriod by rememberSaveable { mutableStateOf(TrendPeriod.MONTH) }
    var statsPage by rememberSaveable { mutableStateOf("") }
    val auto = AutoRepository(AppGraph.database)
    var autoEnabled by rememberSaveable { mutableStateOf(settings.flag(KEY_AUTO_BILL, false)) }
    var autoConfirm by rememberSaveable { mutableStateOf(settings.flag(KEY_AUTO_CONFIRM, false)) }
    var keepAlive by rememberSaveable { mutableStateOf(settings.flag(KEY_KEEP_ALIVE, false)) }
    var watchApps by rememberSaveable { mutableStateOf(settings.watchApps()) }
    var autoPage by rememberSaveable { mutableStateOf("") }
    var minePage by rememberSaveable { mutableStateOf("") }
    var captureSource by rememberSaveable { mutableStateOf("") }
    var captureMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val lockPin = settings.text(KEY_APP_LOCK)
    var unlocked by rememberSaveable { mutableStateOf(lockPin.isEmpty()) }

    CompositionLocalProvider(LocalPalette provides palette) {
        if (!unlocked) {
            Box(Modifier.fillMaxSize().background(palette.appBg).statusBarsPadding()) {
                LockGate(lockPin) { unlocked = true }
            }
            return@CompositionLocalProvider
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
                        repository.save(bill)
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
                    onDelete = { repository.delete(0, opened.client_id); openBill = null; revision++ },
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
                            autoConfirm = autoConfirm,
                            keepAlive = keepAlive,
                            watchApps = watchApps,
                            onCaptureMessage = { captureMessage = it },
                            onAutoConfirm = { autoConfirm = it },
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
                        MinePages(minePage, settings, { theme = it }, { minePage = it }, { minePage = "" }, { openBill = it })
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

@Composable
private fun AutoPages(
    page: String,
    auto: AutoRepository,
    settings: SettingsStore,
    captureSource: String,
    captureMessage: String?,
    autoConfirm: Boolean,
    keepAlive: Boolean,
    watchApps: Set<String>,
    onCaptureMessage: (String) -> Unit,
    onAutoConfirm: (Boolean) -> Unit,
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
            onCaptureText = { text -> captureSummary(auto.capture(text, "ocr")) },
            onOpenPending = onOpenPending,
        )
        "pending" -> PendingPage(auto.pending(), onBack, onConfirm = { auto.confirm(it) }, onIgnore = { auto.ignore(it) })
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
        "settings" -> AutoSettingsPage(autoConfirm, onBack) { on ->
            onAutoConfirm(on)
            settings.setFlag(KEY_AUTO_CONFIRM, on)
        }
    }
}

@Composable
private fun MinePages(
    page: String,
    settings: SettingsStore,
    onTheme: (String) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onOpenBill: (String) -> Unit,
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
        else -> AccountPage(page, database, AccountRepository(database), settings, onTheme, onClear = {
            database.transaction {
                database.billQueries.selectByRange(0, 0, 99999999).executeAsList()
                    .forEach { database.billQueries.deleteOne(it.member_id, it.client_id) }
            }
        }, onOpen, onBack)
    }
}

private fun captureSummary(result: CaptureResult): String {
    val parts = mutableListOf<String>()
    if (result.created > 0) parts += "${result.created} 条已加入待确认"
    if (result.duplicate > 0) parts += "${result.duplicate} 条重复已跳过"
    if (result.unsupported > 0) parts += "${result.unsupported} 条没有识别出金额"
    return if (parts.isEmpty()) "没有可识别的内容" else parts.joinToString("，")
}

@Composable
private fun Placeholder(tab: Int) {
    val palette = LocalPalette.current
    val title = listOf("账单", "统计", "自动记账", "我的")[tab]
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(title, color = palette.textSecondary)
    }
}
