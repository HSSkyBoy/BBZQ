package io.github.bbzq.feats

import org.junit.Assert.assertEquals
import org.junit.Test

class ResolverServersTest {

    @Test
    fun signedQuerySortsEncodesAndSigns() {
        val query = AppSignature.signedQuery(
            mapOf(
                "ts" to "1700000000",
                "keyword" to "凡人 a~b",
                "mobi_app" to "android",
                "build" to "8110300",
            ),
        )

        assertEquals(
            "appkey=1d8b6e7d45233436&build=8110300&keyword=%E5%87%A1%E4%BA%BA%20a~b&mobi_app=android&ts=1700000000" +
                "&sign=c3fdd1af43d9f40a8f8849b8a4b7db27",
            query,
        )
    }

    @Test
    fun configuredKeepsTrialOrderAndSkipsInvalidEntries() {
        val values = mapOf(
            ResolverRegion.CN to "cn.example.com",
            ResolverRegion.SEA to "http://sea.example.com:8080/",
            ResolverRegion.TW to "not a host",
            ResolverRegion.HK to "https://hk.example.com",
        )

        val servers = ResolverServers.configured { values[it] }

        assertEquals(
            listOf(
                ResolverRegion.HK to "https://hk.example.com",
                ResolverRegion.SEA to "http://sea.example.com:8080",
                ResolverRegion.CN to "https://cn.example.com",
            ),
            servers.map { it.region to it.baseUrl },
        )
    }

    @Test
    fun preferringMovesTheKnownRegionFirstAndKeepsTheRest() {
        val servers = ResolverRegion.values().map { ResolverServer(it, "https://${it.name.lowercase()}.example.com") }

        val ordered = ResolverServers.preferring(servers, ResolverRegion.SEA).map { it.region }

        assertEquals(listOf(ResolverRegion.SEA, ResolverRegion.HK, ResolverRegion.TW, ResolverRegion.CN), ordered)
        assertEquals(servers, ResolverServers.preferring(servers, null))
    }
}
