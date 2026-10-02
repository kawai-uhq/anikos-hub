package com.anikoshub.app.util

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Lightweight in-WebView ad / tracker / popup blocker.
 * Blocks known ad hosts and non-http schemes that usually open external apps.
 */
object AdBlocker {

    private val blockedHosts = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.",
        "pagead2.googlesyndication.com",
        "adnxs.com",
        "adsrvr.org",
        "advertising.com",
        "adform.net",
        "adcash.com",
        "adcolony.com",
        "admob.com",
        "adsystem.com",
        "amazon-adsystem.com",
        "scorecardresearch.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "moatads.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "smartadserver.com",
        "exoclick.com",
        "juicyads.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerclick.com",
        "bidswitch.net",
        "casalemedia.com",
        "quantserve.com",
        "hotjar.com",
        "facebook.net",
        "connect.facebook.net",
        "analytics.twitter.com",
        "ads.yahoo.com",
        "media.net",
        "mgid.com",
        "revcontent.com",
        "zergnet.com",
        "ad-delivery.net",
        "adsafeprotected.com",
        "2mdn.net",
        "fundingchoicesmessages.google.com",
        "securepubads.g.doubleclick.net",
        "pagead",
        "ads.",
        "adserver",
        "banner",
        "popunder",
        "clickadu",
        "hilltopads",
        "adsterra",
        "trafficjunky",
        "tsyndicate",
        "yandexadexchange",
        "betweendigital",
        "onetag",
        "adskeeper",
        "pangle",
        "applovin",
        "unityads",
        "ironsrc",
        "vungle",
        "chartboost"
    )

    private val blockedSchemes = setOf(
        "intent", "market", "magnet", "ftp", "file", "blob",
        "whatsapp", "tg", "viber", "fb", "fb://", "line"
    )

    fun isBlockedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase()
        return blockedHosts.any { pattern ->
            val p = pattern.lowercase().trim('.')
            // Prefer exact / suffix match so stream CDNs are not blocked by short substrings
            h == p || h.endsWith(".$p") || h.startsWith("$p.")
        }
    }

    fun isBlockedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase()
        // External app schemes
        blockedSchemes.forEach { scheme ->
            if (lower.startsWith("$scheme:")) return true
        }
        // About / empty
        if (lower == "about:blank" || lower.startsWith("about:")) return false

        return try {
            val host = android.net.Uri.parse(url).host
            isBlockedHost(host)
        } catch (_: Exception) {
            false
        }
    }

    /** Empty response used to cancel a blocked network request. */
    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )

    /**
     * JS snippet that disables window.open / target=_blank style popups
     * and tries to strip common ad iframes after load.
     */
    const val ANTI_POPUP_JS = """
        (function() {
          try {
            window.open = function() { return null; };
            window.alert = function() {};
            window.confirm = function() { return true; };
            window.prompt = function() { return null; };
            document.addEventListener('click', function(e) {
              var a = e.target;
              while (a && a.tagName !== 'A') a = a.parentElement;
              if (a && a.target === '_blank') {
                a.target = '_self';
              }
            }, true);
          } catch (e) {}
        })();
    """
}
