package nac.chat.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import nac.chat.BuildConfig
import nac.chat.R
import nac.chat.api.StoatAPI
import nac.chat.api.buildUserAgent
import nac.chat.api.settings.LoadedSettings
import nac.chat.api.settings.SyncedSettings
import nac.chat.ui.theme.NACTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

class ServerSettingsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_SERVER_NAME = "server_name"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: run { finish(); return }
        val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "Server Settings"

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()

        val sessionToken = StoatAPI.sessionToken
        val userId = StoatAPI.selfId ?: ""
        // webview_token/webview_user are consumed once by Auth.hydrate() on
        // the web side (nac-web#41) and stripped from the URL immediately —
        // replaces a previous approach that poked localforage's IndexedDB
        // record directly, which depended on guessing its internal DB/store
        // names and key shape.
        val settingsUrl =
            "https://community.nac.social/server/$serverId/settings" +
                "?webview_token=$sessionToken&webview_user=$userId"

        setContent {
            NACTheme(
                requestedTheme = LoadedSettings.theme,
                requestedUserInterfaceFont = LoadedSettings.font,
                colourOverrides = SyncedSettings.android.colourOverrides
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(serverName) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_back_24dp),
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = buildUserAgent("ServerSettings")
                                    // Same class of bug we kept hitting tonight elsewhere: without
                                    // this, the WebView happily reuses a stale cached bundle for
                                    // this URL instead of picking up a fresh web-side deploy.
                                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                                    clearCache(true)

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean = false
                                    }

                                    loadUrl(settingsUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
