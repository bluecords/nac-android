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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()

        val settingsUrl = "https://community.nac.social/server/$serverId/settings"
        val sessionToken = StoatAPI.sessionToken
        val userId = StoatAPI.selfId ?: ""

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

                                    var injected = false

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            if (!injected) {
                                                injected = true
                                                val authJson = """{"session":{"_id":"android-session","token":"$sessionToken","userId":"$userId","valid":true}}"""
                                                val js = """
                                                    (function() {
                                                        // localforage stores real JS objects in IndexedDB, not
                                                        // JSON strings - storing the raw string here means the
                                                        // web app's Auth store schema check (clean()) sees a
                                                        // string where it expects an object and treats it as
                                                        // no session at all, landing on the public login page.
                                                        var authData = JSON.parse('$authJson');
                                                        var req = indexedDB.open('localforage');
                                                        req.onupgradeneeded = function(e) {
                                                            e.target.result.createObjectStore('keyvaluepairs');
                                                        };
                                                        req.onsuccess = function(e) {
                                                            var db = e.target.result;
                                                            try {
                                                                var tx = db.transaction('keyvaluepairs', 'readwrite');
                                                                tx.objectStore('keyvaluepairs').put(authData, 'auth');
                                                                tx.oncomplete = function() { window.location.href = '$settingsUrl'; };
                                                            } catch(err) {}
                                                        };
                                                    })();
                                                """.trimIndent()
                                                view?.evaluateJavascript(js, null)
                                            }
                                        }

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
