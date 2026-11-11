package com.meow.ledger.shared.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
