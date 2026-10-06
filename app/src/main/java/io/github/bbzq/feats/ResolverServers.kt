package io.github.bbzq.feats

import android.content.SharedPreferences
import io.github.bbzq.ModuleSettings

/**
 * The regions a resolver server can unlock. Declaration order is the order in which servers are
 * tried when the answer is not known yet: Hong Kong/Macau, Taiwan, Southeast Asia, mainland China.
 */
enum class ResolverRegion(val prefKey: String, val cdnKey: String) {
    HK(ModuleSettings.KEY_RESOLVER_SERVER_HK, ModuleSettings.KEY_RESOLVER_CDN_HK),
    TW(ModuleSettings.KEY_RESOLVER_SERVER_TW, ModuleSettings.KEY_RESOLVER_CDN_TW),
    SEA(ModuleSettings.KEY_SEA_RESOLVER_SERVER, ModuleSettings.KEY_RESOLVER_CDN_SEA),
    CN(ModuleSettings.KEY_RESOLVER_SERVER_CN, ModuleSettings.KEY_RESOLVER_CDN_CN);

    companion object {
        /** The region named by [value] (case-insensitive), or null for blank or unknown text. */
        fun parse(value: String?): ResolverRegion? =
            values().firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

data class ResolverServer(val region: ResolverRegion, val baseUrl: String)

object ResolverServers {
    /** Servers the user filled in, in trial order; entries that do not look like a host are ignored. */
    fun configured(read: (ResolverRegion) -> String?): List<ResolverServer> =
        ResolverRegion.values().mapNotNull { region ->
            ModuleSettings.normalizeResolverBaseUrl(read(region))?.let { ResolverServer(region, it) }
        }

    fun configured(prefs: SharedPreferences): List<ResolverServer> =
        configured { prefs.getString(it.prefKey, null) }

    /** [servers] with [preferred] moved to the front, so a region that worked before is asked first. */
    fun preferring(servers: List<ResolverServer>, preferred: ResolverRegion?): List<ResolverServer> =
        if (preferred == null) servers else servers.sortedBy { if (it.region == preferred) 0 else 1 }
}
