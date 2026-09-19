package com.frostai.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")

        requestPermissionsIfNeeded()

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val command = intent?.getStringExtra("voice_command")
        if (!command.isNullOrEmpty()) {
            webView.post {
                webView.evaluateJavascript(
                    "processCommand(${jsonString(command)});",
                    null
                )
            }
        }
    }

    private fun jsonString(text: String): String {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    private fun requestPermissionsIfNeeded() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 100)
        }
    }

    inner class AndroidBridge(private val activity: MainActivity) {

        @JavascriptInterface
        fun openApp(packageName: String): Boolean {
            return try {
                val launchIntent =
                    activity.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    activity.startActivity(launchIntent)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun startWakeService() {
            val serviceIntent = Intent(activity, WakeWordService::class.java)
            ContextCompat.startForegroundService(activity, serviceIntent)
        }

        @JavascriptInterface
        fun stopWakeService() {
            val serviceIntent = Intent(activity, WakeWordService::class.java)
            activity.stopService(serviceIntent)
        }
    }
}
