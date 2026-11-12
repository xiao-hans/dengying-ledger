package com.meow.ledger.shared.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 每个契约方法与 EndpointCatalog 的权威 method 对账，防止 GET 被写成默认 POST。 */
private val CONTRACT_METHODS: Map<String, String> = mapOf(
    // Bills 12
    "BillsContracts.pageList" to "Bills/PageList",
    "BillsContracts.create" to "Bills/Create",
    "BillsContracts.update" to "Bills/Update",
    "BillsContracts.delete" to "Bills/Delete",
    "BillsContracts.billsCountOfHasPic" to "Bills/BillsCountOfHasPic",
    "BillsContracts.autoBillsCallBack" to "Bills/AutoBillsCallBack",
    "BillsContracts.deleteFixedBill" to "Bills/DeleteFixedBill",
    "BillsContracts.updateFixedBill" to "Bills/UpdateFixedBill",
    "BillsContracts.getFixedBills" to "Bills/GetFixedBills",
    "BillsContracts.createFixedBill" to "Bills/CreateFixedBill",
    "BillsContracts.importBills" to "Bills/ImportBills",
    "BillsContracts.importOcrBills" to "Bills/ImportBills",
    // Cate 6, MemberTag 4, BillsBook 10
    "CateContracts.create" to "Cate/Create",
    "CateContracts.update" to "Cate/Update",
    "CateContracts.delete" to "Cate/Delete",
    "CateContracts.getMainCates" to "Cate/GetMainCates",
    "CateContracts.getCateIcons" to "Cate/GetCateIcons",
    "CateContracts.updateExtend" to "Cate/UpdateExtend",
    "MemberTagContracts.create" to "MemberTag/Create",
    "MemberTagContracts.update" to "MemberTag/Update",
    "MemberTagContracts.delete" to "MemberTag/Delete",
    "MemberTagContracts.updateSort" to "MemberTag/UpdateSort",
    "BillsBookContracts.create" to "BillsBook/Create",
    "BillsBookContracts.update" to "BillsBook/Update",
    "BillsBookContracts.delete" to "BillsBook/Delete",
    "BillsBookContracts.getList" to "BillsBook/GetList",
    "BillsBookContracts.getBillsBookIcons" to "BillsBook/GetBillsBookIcons",
    "BillsBookContracts.join" to "BillsBook/Join",
    "BillsBookContracts.getInfoByCode" to "BillsBook/GetInfoByCode",
    "BillsBookContracts.createInviteCode" to "BillsBook/CreateInviteCode",
    "BillsBookContracts.removeMember" to "BillsBook/RemoveMember",
    "BillsBookContracts.exit" to "BillsBook/Exit",
    // Budget 9
    "BudgetContracts.createBudget" to "Budget/CreateBudget",
    "BudgetContracts.getYearlyBudgets" to "Budget/GetYearlyBudgets",
    "BudgetContracts.getBudget" to "Budget/GetBudget",
    "BudgetContracts.deleteBudget" to "Budget/DeleteBudget",
    "BudgetContracts.deleteBudgetCate" to "Budget/DeleteBudgetCate",
    "BudgetContracts.updateBudgetCate" to "Budget/UpdateBudgetCate",
    "BudgetContracts.updateBudget" to "Budget/UpdateBudget",
    "BudgetContracts.createBudgetCate" to "Budget/CreateBudgetCate",
    "BudgetContracts.getBudgetList" to "Budget/GetBudgetList",
    // Rule 4, ImportFile 2, Upload 3, FundAccount 6
    "RuleContracts.create" to "KeyWordMatchingRule/Create",
    "RuleContracts.list" to "KeyWordMatchingRule/List",
    "RuleContracts.update" to "KeyWordMatchingRule/Update",
    "RuleContracts.delete" to "KeyWordMatchingRule/Delete",
    "ImportFileContracts.list" to "MemberImportBillFile/List",
    "ImportFileContracts.delete" to "MemberImportBillFile/Delete",
    "UploadContracts.uploadImages" to "Upload/UploadImages",
    "UploadContracts.uploadImg" to "Upload/UploadImg",
    "UploadContracts.uploadBills" to "Upload/UploadBills",
    "FundAccountContracts.create" to "FundAccount/Create",
    "FundAccountContracts.update" to "FundAccount/Update",
    "FundAccountContracts.delete" to "FundAccount/Delete",
    "FundAccountContracts.updateStatus" to "FundAccount/UpdateStatus",
    "FundAccountContracts.getIOU" to "FundAccount/GetIOU",
    "FundAccountContracts.getFundAccountTypes" to "FundAccount/GetFundAccountTypes",
    // SavePlan 14
    "SavePlanContracts.createSavePlan" to "SavePlan/CreateSavePlan",
    "SavePlanContracts.updateSavePlan" to "SavePlan/UpdateSavePlan",
    "SavePlanContracts.updateSavePlanItem" to "SavePlan/UpdateSavePlanItem",
    "SavePlanContracts.updateSavePlanItemStatus" to "SavePlan/UpdateSavePlanItemStatus",
    "SavePlanContracts.updateSavePlanStatus" to "SavePlan/UpdateSavePlanStatus",
    "SavePlanContracts.createSavePlanItem" to "SavePlan/CreateSavePlanItem",
    "SavePlanContracts.savePlanItemsById" to "SavePlan/SavePlanItemsById",
    "SavePlanContracts.getSavePlans" to "SavePlan/GetSavePlans",
    "SavePlanContracts.getSavePlansByQuery" to "SavePlan/GetSavePlansByQuery",
    "SavePlanContracts.getSavePlansByType" to "SavePlan/GetSavePlansByType",
    "SavePlanContracts.getFileSavePlans" to "SavePlan/GetFileSavePlans",
    "SavePlanContracts.wishes" to "SavePlan/Wishes",
    "SavePlanContracts.getIcon" to "SavePlan/GetIcon",
    "SavePlanContracts.getSavePlanType" to "SavePlan/GetSavePlanType",
    // Installment 6, Refund 3
    "InstallmentContracts.create" to "ConsumptionInstallment/Create",
    "InstallmentContracts.allPlan" to "ConsumptionInstallment/AllPlan",
    "InstallmentContracts.getByPlanId" to "ConsumptionInstallment/GetByPlanId",
    "InstallmentContracts.getByClientId" to "ConsumptionInstallment/GetByClientId",
    "InstallmentContracts.payOff" to "ConsumptionInstallment/PayOff",
    "InstallmentContracts.delete" to "ConsumptionInstallment/Delete",
    "RefundContracts.create" to "BillRefund/Create",
    "RefundContracts.update" to "BillRefund/Update",
    "RefundContracts.delete" to "BillRefund/Delete",
    // Account 10, Member 10
    "AccountContracts.sendCode" to "Account/SendCode",
    "AccountContracts.loginByPhone" to "Account/LoginByPhone",
    "AccountContracts.loginByDevice" to "Account/LoginByDevice",
    "AccountContracts.activateV3" to "Account/ActivateV3",
    "AccountContracts.bindPhone" to "Account/BindPhone",
    "AccountContracts.unbind" to "Account/Unbind",
    "AccountContracts.bindQQ" to "Account/BindQQ",
    "AccountContracts.bindWechat" to "Account/BindWechat",
    "AccountContracts.loginByQQ" to "Account/LoginByQQ",
    "AccountContracts.loginByWechat" to "Account/LoginByWechat",
    "MemberContracts.getInfo" to "Member/GetInfo",
    "MemberContracts.getContractInfo" to "Member/GetMemberContractInfo",
    "MemberContracts.getSetting" to "Member/GetSetting",
    "MemberContracts.updateSetting" to "Member/UpdateSetting",
    "MemberContracts.updateNickname" to "Member/UpdateNickname",
    "MemberContracts.refreshToken" to "Member/RefreshToken",
    "MemberContracts.createReminder" to "Member/CreateReminder",
    "MemberContracts.updateReminder" to "Member/UpdateReminder",
    "MemberContracts.reminderList" to "Member/MemberReminderList",
    "MemberContracts.initSSOldUserInfo" to "Member/InitSSOldUserInfo",
    // Order 5, VipGroupBuy 3, Platform 5
    "OrderContracts.createOrder" to "Order/v2/CreateOrder",
    "OrderContracts.vipConfigs" to "Order/v2/VipConfigs",
    "OrderContracts.checkStatus" to "Order/CheckStatus",
    "OrderContracts.rechargeDiscountConfig" to "Order/GetRechargeDiscountConfig",
    "OrderContracts.alipayUnSign" to "Order/AlipayUnSign",
    "VipGroupBuyContracts.list" to "VipGroupBuy/List",
    "VipGroupBuyContracts.bind" to "VipGroupBuy/Bind",
    "VipGroupBuyContracts.getByShowId" to "VipGroupBuy/GetByShowId",
    "PlatformContracts.getDialog" to "Operation/GetDialog",
    "PlatformContracts.getUpdateRemind" to "Operation/GetUpdateRemind",
    "PlatformContracts.behaviorAdd" to "Behavior/Add",
    "PlatformContracts.configGetV2" to "Config/GetV2",
    "PlatformContracts.personCenterIcons" to "PersonCenter/GetPersonCenterIcons",
)

class MeowApiContractTest {
    private class RecordingApi : MeowApi {
        val requests = mutableListOf<EndpointRequest>()

        override suspend fun call(request: EndpointRequest): JsonEnvelope {
            requests += request
            return Envelope(data = JsonNull)
        }
    }

    @Test
    fun `s2 wrappers register all 32 endpoints`() {
        val api = RecordingApi()
        runBlocking {
            BillsContracts(api).pageList("2026-11-11", "1")
            BillsContracts(api).create(JsonNull)
            BillsContracts(api).update(JsonNull)
            BillsContracts(api).delete(JsonNull)
            BillsContracts(api).billsCountOfHasPic()
            BillsContracts(api).autoBillsCallBack("1")
            BillsContracts(api).deleteFixedBill("1")
            BillsContracts(api).updateFixedBill(emptyMap())
            BillsContracts(api).getFixedBills()
            BillsContracts(api).createFixedBill(emptyMap())
            BillsContracts(api).importBills(JsonNull)
            BillsContracts(api).importOcrBills(JsonNull)

            CateContracts(api).create("午餐", "1", "icon")
            CateContracts(api).update(emptyMap())
            CateContracts(api).delete("1")
            CateContracts(api).getMainCates()
            CateContracts(api).getCateIcons()
            CateContracts(api).updateExtend(JsonNull)

            MemberTagContracts(api).create("book", "标签")
            MemberTagContracts(api).update("1", "标签")
            MemberTagContracts(api).delete("1")
            MemberTagContracts(api).updateSort(JsonNull)

            BillsBookContracts(api).create("日常", "icon", "1")
            BillsBookContracts(api).update(emptyMap())
            BillsBookContracts(api).delete("1")
            BillsBookContracts(api).getList()
            BillsBookContracts(api).getBillsBookIcons()
            BillsBookContracts(api).join("1")
            BillsBookContracts(api).getInfoByCode("code")
            BillsBookContracts(api).createInviteCode("1")
            BillsBookContracts(api).removeMember("m", "1")
            BillsBookContracts(api).exit("1")
        }
        assertEquals(32, api.requests.size)
        val paths = api.requests.map { it.path }.toSet()
        assertEquals(32 - 1, paths.size)
        assertTrue(paths.all { it in EndpointRegistry.uniquePaths() })
    }

    @Test
    fun `member tag create uses uppercase BillBookId`() {
        val api = RecordingApi()
        runBlocking { MemberTagContracts(api).create("book", "标签") }
        assertEquals("book", api.requests.single().fields["BillBookId"])
    }


    @Test
    fun `s5 contracts register 29 endpoints`() {
        val api = RecordingApi()
        runBlocking {
            FundAccountContracts(api).create(emptyMap())
            FundAccountContracts(api).update(emptyMap())
            FundAccountContracts(api).delete("1", "1")
            FundAccountContracts(api).updateStatus("1", "1")
            FundAccountContracts(api).getIOU("1")
            FundAccountContracts(api).getFundAccountTypes()

            SavePlanContracts(api).createSavePlan(emptyMap())
            SavePlanContracts(api).updateSavePlan(emptyMap())
            SavePlanContracts(api).updateSavePlanItem(emptyMap())
            SavePlanContracts(api).updateSavePlanItemStatus(emptyMap())
            SavePlanContracts(api).updateSavePlanStatus(emptyMap())
            SavePlanContracts(api).createSavePlanItem(emptyMap())
            SavePlanContracts(api).savePlanItemsById(emptyMap())
            SavePlanContracts(api).getSavePlans()
            SavePlanContracts(api).getSavePlansByQuery(emptyMap())
            SavePlanContracts(api).getSavePlansByType("1")
            SavePlanContracts(api).getFileSavePlans()
            SavePlanContracts(api).wishes()
            SavePlanContracts(api).getIcon()
            SavePlanContracts(api).getSavePlanType()

            InstallmentContracts(api).create(emptyMap())
            InstallmentContracts(api).allPlan()
            InstallmentContracts(api).getByPlanId("1")
            InstallmentContracts(api).getByClientId("c")
            InstallmentContracts(api).payOff(emptyMap())
            InstallmentContracts(api).delete("1", "1")

            RefundContracts(api).create(emptyMap())
            RefundContracts(api).update(emptyMap())
            RefundContracts(api).delete(emptyMap())
        }
        assertEquals(29, api.requests.size)
        val paths = api.requests.map { it.path }.toSet()
        assertEquals(29, paths.size)
        assertTrue(paths.all { it in EndpointRegistry.uniquePaths() })
    }

    private fun assertTrue(value: Boolean) = kotlin.test.assertTrue(value)
    @Test
    fun `s6 s7 contracts register 33 endpoints`() {
        val api = RecordingApi()
        runBlocking {
            AccountContracts(api).sendCode("13800138000")
            AccountContracts(api).loginByPhone("13800138000", "1234")
            AccountContracts(api).loginByDevice()
            AccountContracts(api).activateV3("d1")
            AccountContracts(api).bindPhone("13800138000", "1234", "")
            AccountContracts(api).unbind()
            AccountContracts(api).bindQQ(emptyMap())
            AccountContracts(api).bindWechat(emptyMap())
            AccountContracts(api).loginByQQ(emptyMap())
            AccountContracts(api).loginByWechat(emptyMap())

            MemberContracts(api).getInfo()
            MemberContracts(api).getContractInfo()
            MemberContracts(api).getSetting()
            MemberContracts(api).updateSetting("key", "value")
            MemberContracts(api).updateNickname("喵")
            MemberContracts(api).refreshToken("gt")
            MemberContracts(api).createReminder(emptyMap())
            MemberContracts(api).updateReminder(emptyMap())
            MemberContracts(api).reminderList()
            MemberContracts(api).initSSOldUserInfo()

            OrderContracts(api).createOrder(emptyMap())
            OrderContracts(api).vipConfigs()
            OrderContracts(api).checkStatus("o1")
            OrderContracts(api).rechargeDiscountConfig()
            OrderContracts(api).alipayUnSign()

            VipGroupBuyContracts(api).list()
            VipGroupBuyContracts(api).bind()
            VipGroupBuyContracts(api).getByShowId("s1")

            PlatformContracts(api).getDialog()
            PlatformContracts(api).getUpdateRemind()
            PlatformContracts(api).behaviorAdd()
            PlatformContracts(api).configGetV2(emptyMap())
            PlatformContracts(api).personCenterIcons()
        }
        assertEquals(33, api.requests.size)
        assertTrue(api.requests.all { it.path in EndpointRegistry.uniquePaths() })
    }

    @Test
    fun `catalog covers all 112 endpoints`() {
        // ImportBills 两个重载同路径，112 条 spec 唯一路径 111。
        assertEquals(112, EndpointRegistry.all.size)
        assertEquals(111, EndpointRegistry.uniquePaths().size)
    }

    /**
     * 表驱动：每个契约方法逐条与 EndpointCatalog 的权威 method 对账。
     * 契约类共 110 次调用（111 唯一路径中无法从包装方法区分的两个 ImportBills 重载算 2 个方法）。
     */
    @Test
    fun `every contract method matches catalog method`() {
        val api = RecordingApi()
        val invoked = mutableListOf<Triple<String, String, String>>() // methodName, path, method
        fun record(name: String, path: String, block: suspend () -> Unit) {
            val before = api.requests.size
            runBlocking { block() }
            val req = api.requests[before]
            invoked += Triple(name, req.path, req.method)
        }

        record("BillsContracts.pageList", "Bills/PageList") { BillsContracts(api).pageList("2026-11-11", "1") }
        record("BillsContracts.create", "Bills/Create") { BillsContracts(api).create(JsonNull) }
        record("BillsContracts.update", "Bills/Update") { BillsContracts(api).update(JsonNull) }
        record("BillsContracts.delete", "Bills/Delete") { BillsContracts(api).delete(JsonNull) }
        record("BillsContracts.billsCountOfHasPic", "Bills/BillsCountOfHasPic") { BillsContracts(api).billsCountOfHasPic() }
        record("BillsContracts.autoBillsCallBack", "Bills/AutoBillsCallBack") { BillsContracts(api).autoBillsCallBack("1") }
        record("BillsContracts.deleteFixedBill", "Bills/DeleteFixedBill") { BillsContracts(api).deleteFixedBill("1") }
        record("BillsContracts.updateFixedBill", "Bills/UpdateFixedBill") { BillsContracts(api).updateFixedBill(emptyMap()) }
        record("BillsContracts.getFixedBills", "Bills/GetFixedBills") { BillsContracts(api).getFixedBills() }
        record("BillsContracts.createFixedBill", "Bills/CreateFixedBill") { BillsContracts(api).createFixedBill(emptyMap()) }
        record("BillsContracts.importBills", "Bills/ImportBills") { BillsContracts(api).importBills(JsonNull) }
        record("BillsContracts.importOcrBills", "Bills/ImportBills") { BillsContracts(api).importOcrBills(JsonNull) }

        record("CateContracts.create", "Cate/Create") { CateContracts(api).create("午餐", "1", "icon") }
        record("CateContracts.update", "Cate/Update") { CateContracts(api).update(emptyMap()) }
        record("CateContracts.delete", "Cate/Delete") { CateContracts(api).delete("1") }
        record("CateContracts.getMainCates", "Cate/GetMainCates") { CateContracts(api).getMainCates() }
        record("CateContracts.getCateIcons", "Cate/GetCateIcons") { CateContracts(api).getCateIcons() }
        record("CateContracts.updateExtend", "Cate/UpdateExtend") { CateContracts(api).updateExtend(JsonNull) }

        record("MemberTagContracts.create", "MemberTag/Create") { MemberTagContracts(api).create("book", "标签") }
        record("MemberTagContracts.update", "MemberTag/Update") { MemberTagContracts(api).update("1", "标签") }
        record("MemberTagContracts.delete", "MemberTag/Delete") { MemberTagContracts(api).delete("1") }
        record("MemberTagContracts.updateSort", "MemberTag/UpdateSort") { MemberTagContracts(api).updateSort(JsonNull) }

        record("BillsBookContracts.create", "BillsBook/Create") { BillsBookContracts(api).create("日常", "icon", "1") }
        record("BillsBookContracts.update", "BillsBook/Update") { BillsBookContracts(api).update(emptyMap()) }
        record("BillsBookContracts.delete", "BillsBook/Delete") { BillsBookContracts(api).delete("1") }
        record("BillsBookContracts.getList", "BillsBook/GetList") { BillsBookContracts(api).getList() }
        record("BillsBookContracts.getBillsBookIcons", "BillsBook/GetBillsBookIcons") { BillsBookContracts(api).getBillsBookIcons() }
        record("BillsBookContracts.join", "BillsBook/Join") { BillsBookContracts(api).join("1") }
        record("BillsBookContracts.getInfoByCode", "BillsBook/GetInfoByCode") { BillsBookContracts(api).getInfoByCode("code") }
        record("BillsBookContracts.createInviteCode", "BillsBook/CreateInviteCode") { BillsBookContracts(api).createInviteCode("1") }
        record("BillsBookContracts.removeMember", "BillsBook/RemoveMember") { BillsBookContracts(api).removeMember("m", "1") }
        record("BillsBookContracts.exit", "BillsBook/Exit") { BillsBookContracts(api).exit("1") }

        record("BudgetContracts.createBudget", "Budget/CreateBudget") { BudgetContracts(api).createBudget(emptyMap()) }
        record("BudgetContracts.getYearlyBudgets", "Budget/GetYearlyBudgets") { BudgetContracts(api).getYearlyBudgets("2026") }
        record("BudgetContracts.getBudget", "Budget/GetBudget") { BudgetContracts(api).getBudget("1", "202611") }
        record("BudgetContracts.deleteBudget", "Budget/DeleteBudget") { BudgetContracts(api).deleteBudget("1", "202611") }
        record("BudgetContracts.deleteBudgetCate", "Budget/DeleteBudgetCate") { BudgetContracts(api).deleteBudgetCate("1", "202611", "2") }
        record("BudgetContracts.updateBudgetCate", "Budget/UpdateBudgetCate") { BudgetContracts(api).updateBudgetCate("1", "202611", "2", "100") }
        record("BudgetContracts.updateBudget", "Budget/UpdateBudget") { BudgetContracts(api).updateBudget(emptyMap()) }
        record("BudgetContracts.createBudgetCate", "Budget/CreateBudgetCate") { BudgetContracts(api).createBudgetCate("1", "202611", "2", "100") }
        record("BudgetContracts.getBudgetList", "Budget/GetBudgetList") { BudgetContracts(api).getBudgetList() }

        record("RuleContracts.create", "KeyWordMatchingRule/Create") { RuleContracts(api).create(emptyMap()) }
        record("RuleContracts.list", "KeyWordMatchingRule/List") { RuleContracts(api).list() }
        record("RuleContracts.update", "KeyWordMatchingRule/Update") { RuleContracts(api).update(emptyMap()) }
        record("RuleContracts.delete", "KeyWordMatchingRule/Delete") { RuleContracts(api).delete("1") }

        record("ImportFileContracts.list", "MemberImportBillFile/List") { ImportFileContracts(api).list() }
        record("ImportFileContracts.delete", "MemberImportBillFile/Delete") { ImportFileContracts(api).delete("1") }

        record("UploadContracts.uploadImages", "Upload/UploadImages") { UploadContracts(api).uploadImages("a.jpg") }
        record("UploadContracts.uploadImg", "Upload/UploadImg") { UploadContracts(api).uploadImg("1") }
        record("UploadContracts.uploadBills", "Upload/UploadBills") { UploadContracts(api).uploadBills("a.csv") }

        record("FundAccountContracts.create", "FundAccount/Create") { FundAccountContracts(api).create(emptyMap()) }
        record("FundAccountContracts.update", "FundAccount/Update") { FundAccountContracts(api).update(emptyMap()) }
        record("FundAccountContracts.delete", "FundAccount/Delete") { FundAccountContracts(api).delete("1", "1") }
        record("FundAccountContracts.updateStatus", "FundAccount/UpdateStatus") { FundAccountContracts(api).updateStatus("1", "1") }
        record("FundAccountContracts.getIOU", "FundAccount/GetIOU") { FundAccountContracts(api).getIOU("1") }
        record("FundAccountContracts.getFundAccountTypes", "FundAccount/GetFundAccountTypes") { FundAccountContracts(api).getFundAccountTypes() }

        record("SavePlanContracts.createSavePlan", "SavePlan/CreateSavePlan") { SavePlanContracts(api).createSavePlan(emptyMap()) }
        record("SavePlanContracts.updateSavePlan", "SavePlan/UpdateSavePlan") { SavePlanContracts(api).updateSavePlan(emptyMap()) }
        record("SavePlanContracts.updateSavePlanItem", "SavePlan/UpdateSavePlanItem") { SavePlanContracts(api).updateSavePlanItem(emptyMap()) }
        record("SavePlanContracts.updateSavePlanItemStatus", "SavePlan/UpdateSavePlanItemStatus") { SavePlanContracts(api).updateSavePlanItemStatus(emptyMap()) }
        record("SavePlanContracts.updateSavePlanStatus", "SavePlan/UpdateSavePlanStatus") { SavePlanContracts(api).updateSavePlanStatus(emptyMap()) }
        record("SavePlanContracts.createSavePlanItem", "SavePlan/CreateSavePlanItem") { SavePlanContracts(api).createSavePlanItem(emptyMap()) }
        record("SavePlanContracts.savePlanItemsById", "SavePlan/SavePlanItemsById") { SavePlanContracts(api).savePlanItemsById(emptyMap()) }
        record("SavePlanContracts.getSavePlans", "SavePlan/GetSavePlans") { SavePlanContracts(api).getSavePlans() }
        record("SavePlanContracts.getSavePlansByQuery", "SavePlan/GetSavePlansByQuery") { SavePlanContracts(api).getSavePlansByQuery(emptyMap()) }
        record("SavePlanContracts.getSavePlansByType", "SavePlan/GetSavePlansByType") { SavePlanContracts(api).getSavePlansByType("1") }
        record("SavePlanContracts.getFileSavePlans", "SavePlan/GetFileSavePlans") { SavePlanContracts(api).getFileSavePlans() }
        record("SavePlanContracts.wishes", "SavePlan/Wishes") { SavePlanContracts(api).wishes() }
        record("SavePlanContracts.getIcon", "SavePlan/GetIcon") { SavePlanContracts(api).getIcon() }
        record("SavePlanContracts.getSavePlanType", "SavePlan/GetSavePlanType") { SavePlanContracts(api).getSavePlanType() }

        record("InstallmentContracts.create", "ConsumptionInstallment/Create") { InstallmentContracts(api).create(emptyMap()) }
        record("InstallmentContracts.allPlan", "ConsumptionInstallment/AllPlan") { InstallmentContracts(api).allPlan() }
        record("InstallmentContracts.getByPlanId", "ConsumptionInstallment/GetByPlanId") { InstallmentContracts(api).getByPlanId("1") }
        record("InstallmentContracts.getByClientId", "ConsumptionInstallment/GetByClientId") { InstallmentContracts(api).getByClientId("c") }
        record("InstallmentContracts.payOff", "ConsumptionInstallment/PayOff") { InstallmentContracts(api).payOff(emptyMap()) }
        record("InstallmentContracts.delete", "ConsumptionInstallment/Delete") { InstallmentContracts(api).delete("1", "1") }

        record("RefundContracts.create", "BillRefund/Create") { RefundContracts(api).create(emptyMap()) }
        record("RefundContracts.update", "BillRefund/Update") { RefundContracts(api).update(emptyMap()) }
        record("RefundContracts.delete", "BillRefund/Delete") { RefundContracts(api).delete(emptyMap()) }

        record("AccountContracts.sendCode", "Account/SendCode") { AccountContracts(api).sendCode("13800138000") }
        record("AccountContracts.loginByPhone", "Account/LoginByPhone") { AccountContracts(api).loginByPhone("13800138000", "1234") }
        record("AccountContracts.loginByDevice", "Account/LoginByDevice") { AccountContracts(api).loginByDevice() }
        record("AccountContracts.activateV3", "Account/ActivateV3") { AccountContracts(api).activateV3("d1") }
        record("AccountContracts.bindPhone", "Account/BindPhone") { AccountContracts(api).bindPhone("13800138000", "1234", "") }
        record("AccountContracts.unbind", "Account/Unbind") { AccountContracts(api).unbind() }
        record("AccountContracts.bindQQ", "Account/BindQQ") { AccountContracts(api).bindQQ(emptyMap()) }
        record("AccountContracts.bindWechat", "Account/BindWechat") { AccountContracts(api).bindWechat(emptyMap()) }
        record("AccountContracts.loginByQQ", "Account/LoginByQQ") { AccountContracts(api).loginByQQ(emptyMap()) }
        record("AccountContracts.loginByWechat", "Account/LoginByWechat") { AccountContracts(api).loginByWechat(emptyMap()) }

        record("MemberContracts.getInfo", "Member/GetInfo") { MemberContracts(api).getInfo() }
        record("MemberContracts.getContractInfo", "Member/GetMemberContractInfo") { MemberContracts(api).getContractInfo() }
        record("MemberContracts.getSetting", "Member/GetSetting") { MemberContracts(api).getSetting() }
        record("MemberContracts.updateSetting", "Member/UpdateSetting") { MemberContracts(api).updateSetting("key", "value") }
        record("MemberContracts.updateNickname", "Member/UpdateNickname") { MemberContracts(api).updateNickname("喵") }
        record("MemberContracts.refreshToken", "Member/RefreshToken") { MemberContracts(api).refreshToken("gt") }
        record("MemberContracts.createReminder", "Member/CreateReminder") { MemberContracts(api).createReminder(emptyMap()) }
        record("MemberContracts.updateReminder", "Member/UpdateReminder") { MemberContracts(api).updateReminder(emptyMap()) }
        record("MemberContracts.reminderList", "Member/MemberReminderList") { MemberContracts(api).reminderList() }
        record("MemberContracts.initSSOldUserInfo", "Member/InitSSOldUserInfo") { MemberContracts(api).initSSOldUserInfo() }

        record("OrderContracts.createOrder", "Order/v2/CreateOrder") { OrderContracts(api).createOrder(emptyMap()) }
        record("OrderContracts.vipConfigs", "Order/v2/VipConfigs") { OrderContracts(api).vipConfigs() }
        record("OrderContracts.checkStatus", "Order/CheckStatus") { OrderContracts(api).checkStatus("o1") }
        record("OrderContracts.rechargeDiscountConfig", "Order/GetRechargeDiscountConfig") { OrderContracts(api).rechargeDiscountConfig() }
        record("OrderContracts.alipayUnSign", "Order/AlipayUnSign") { OrderContracts(api).alipayUnSign() }

        record("VipGroupBuyContracts.list", "VipGroupBuy/List") { VipGroupBuyContracts(api).list() }
        record("VipGroupBuyContracts.bind", "VipGroupBuy/Bind") { VipGroupBuyContracts(api).bind() }
        record("VipGroupBuyContracts.getByShowId", "VipGroupBuy/GetByShowId") { VipGroupBuyContracts(api).getByShowId("s1") }

        record("PlatformContracts.getDialog", "Operation/GetDialog") { PlatformContracts(api).getDialog() }
        record("PlatformContracts.getUpdateRemind", "Operation/GetUpdateRemind") { PlatformContracts(api).getUpdateRemind() }
        record("PlatformContracts.behaviorAdd", "Behavior/Add") { PlatformContracts(api).behaviorAdd() }
        record("PlatformContracts.configGetV2", "Config/GetV2") { PlatformContracts(api).configGetV2(emptyMap()) }
        record("PlatformContracts.personCenterIcons", "PersonCenter/GetPersonCenterIcons") { PlatformContracts(api).personCenterIcons() }

        // 覆盖全部 110 个包装方法，且 path 全部在权威清单内。
        assertEquals(CONTRACT_METHODS.size, invoked.size)
        assertEquals(CONTRACT_METHODS.keys, invoked.map { it.first }.toSet())
        val catalogMethods = EndpointCatalog.associate { it.path to it.method }
        invoked.forEach { (name, path, method) ->
            assertEquals(CONTRACT_METHODS[name], path, "path drift for $name")
            assertEquals(catalogMethods[path], method, "method mismatch for $name ($path)")
            assertTrue(EndpointRegistry.isKnown(path))
        }
        // 9 个 GET 端点的方法确实被显式写成 GET（Catalog 里 GET 的数量）。
        assertEquals(9, EndpointCatalog.count { it.method == "GET" })
        assertEquals(9, invoked.count { it.third == "GET" })
    }

    @Test
    fun `configGetV2 declares urlType and resolves config host`() {
        val api = RecordingApi()
        runBlocking { PlatformContracts(api).configGetV2(emptyMap()) }
        val request = api.requests.single()
        assertEquals("configManage", request.urlType)
        assertEquals(HttpConfig.CONFIG_BASE, HttpConfig.baseFor(request.path, request.urlType))
        assertEquals(HttpConfig.MAIN_BASE, HttpConfig.baseFor("Bills/PageList", null))
    }
}
