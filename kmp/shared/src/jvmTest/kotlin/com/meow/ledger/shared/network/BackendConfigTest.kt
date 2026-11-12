package com.meow.ledger.shared.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 设置页自建同步地址的校验规则（见 `SettingsHome` 调用的 [validateSyncUrl]）。 */
class BackendConfigTest {
    @Test
    fun blankMeansDisabledAndIsValid() {
        assertNull(validateSyncUrl(""))
        assertNull(validateSyncUrl("   "))
    }

    @Test
    fun httpsUrlIsValid() {
        assertNull(validateSyncUrl("https://ledger.example.com"))
        assertNull(validateSyncUrl("http://192.168.1.10:8080/api/"))
    }

    @Test
    fun trailingSpaceIsTrimmed() {
        assertNull(validateSyncUrl("https://ledger.example.com/api/ "))
    }

    @Test
    fun missingSchemeIsRejected() {
        assertEquals("必须以 http:// 或 https:// 开头", validateSyncUrl("ledger.example.com"))
        assertEquals("必须以 http:// 或 https:// 开头", validateSyncUrl("ftp://ledger.example.com"))
    }

    @Test
    fun missingHostIsRejected() {
        assertEquals("缺少主机名", validateSyncUrl("http://"))
        assertEquals("缺少主机名", validateSyncUrl("https:///api/"))
    }

    @Test
    fun sampleProductionHostsAreRejected() {
        assertEquals("不能填写样本原生产域名", validateSyncUrl(HttpConfig.MAIN_BASE))
        assertEquals("不能填写样本原生产域名", validateSyncUrl(HttpConfig.CONFIG_BASE))
        assertEquals("不能填写样本原生产域名", validateSyncUrl("https://puppy-api.cqmiaoa.com:8443/api/"))
    }
}
