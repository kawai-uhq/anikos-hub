package com.anikoshub.app.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.anikoshub.app.data.Media
import com.anikoshub.app.data.Providers
import com.anikoshub.app.ui.theme.Purple
import com.anikoshub.app.util.AdBlocker
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    media: Media,
    providerId: String,
    episode: Pair<Int, Int>?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val provider = Providers.byId(providerId)

    val url = if (media.type == "movie") {
        provider.movieUrl(media.id)
    } else {
        val (s, e) = episode ?: (1 to 1)
        provider.tvUrl(media.id, s, e)
    }

    var pageError by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showChrome by remember { mutableStateOf(true) }
    var isFullscreenVideo by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Tap screen → show controls; auto-hide after a few seconds
    LaunchedEffect(showChrome, isFullscreenVideo) {
        if (showChrome && !isFullscreenVideo) {
            delay(4000)
            showChrome = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (!isFullscreenVideo) showChrome = !showChrome
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val container = FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.BLACK)
                }

                val webView = WebView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.BLACK)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        // Desktop UA — VidLink / VidFast embeds work more reliably
                        userAgentString =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/121.0.0.0 Safari/537.36"
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val target = request.url?.toString() ?: return true
                            val lower = target.lowercase()

                            // Block leaving the app via external schemes
                            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                                return true
                            }

                            // Block pure ad landing hosts from navigating the main frame
                            if (request.isForMainFrame && AdBlocker.isBlockedHost(request.url?.host)) {
                                return true
                            }

                            // All other https — stay inside WebView (needed for stream CDNs)
                            return false
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val host = request?.url?.host
                            // Only block well-known ad/tracker hosts — do NOT block unknown CDNs
                            if (AdBlocker.isBlockedHost(host)) {
                                return AdBlocker.emptyResponse()
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?
                        ) {
                            pageError = null
                            view?.evaluateJavascript(AdBlocker.ANTI_POPUP_JS, null)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                pageError = "Failed to load player: ${error.description}"
                            }
                        }

                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            pageError = null
                            view?.evaluateJavascript(AdBlocker.ANTI_POPUP_JS, null)
                            view?.evaluateJavascript(
                                """
                                (function() {
                                  try {
                                    document.documentElement.style.background = '#000';
                                    document.body.style.background = '#000';
                                    document.body.style.margin = '0';
                                    var vids = document.querySelectorAll('video');
                                    for (var i = 0; i < vids.length; i++) {
                                      vids[i].setAttribute('playsinline', 'true');
                                      vids[i].setAttribute('webkit-playsinline', 'true');
                                      vids[i].style.maxWidth = '100%';
                                      vids[i].style.maxHeight = '100%';
                                    }
                                  } catch (e) {}
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        private var customView: View? = null
                        private var customViewCallback: CustomViewCallback? = null

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean {
                            // Kill popup windows (ads), but don't block in-page video
                            return false
                        }

                        override fun onShowCustomView(
                            view: View?,
                            callback: CustomViewCallback?
                        ) {
                            if (customView != null) {
                                callback?.onCustomViewHidden()
                                return
                            }
                            customView = view
                            customViewCallback = callback
                            isFullscreenVideo = true
                            showChrome = false
                            view?.layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            container.addView(view)
                            this@apply.visibility = View.GONE
                        }

                        override fun onHideCustomView() {
                            customView?.let { container.removeView(it) }
                            customView = null
                            customViewCallback?.onCustomViewHidden()
                            customViewCallback = null
                            this@apply.visibility = View.VISIBLE
                            isFullscreenVideo = false
                        }

                        override fun getDefaultVideoPoster(): Bitmap? {
                            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                        }
                    }

                    loadUrl(url)
                }

                webViewRef = webView
                container.addView(webView)
                container
            },
            update = { }
        )

        // Top controls: Exit (top-left) + title + Reload / Browser — tap screen to show
        AnimatedVisibility(
            visible = showChrome && !isFullscreenVideo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC101014))
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit stream — top left
                Text(
                    "✕ Exit",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color(0xFF9B5CFF), RoundedCornerShape(20.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    media.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    fontSize = 14.sp
                )

                TextButton(onClick = { webViewRef?.reload() }) {
                    Text("Reload", color = Purple)
                }
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                ) {
                    Text("Browser", color = Purple)
                }
            }
        }

        pageError?.let {
            Text(
                it,
                color = Color(0xFFFF6B6B),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                fontSize = 13.sp
            )
        }
    }
}
