package com.akres.launchly.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

class LoginActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var cookieManager: CookieManager

    @Volatile private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        cookieManager = CookieManager.getInstance()
        setContentView(webView)
        setupWebView()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.stopLoading()
        setResult(RESULT_CANCELED)
        finish()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView() {
        cookieManager.apply {
            removeAllCookies(null)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.safeBrowsingEnabled = false

        webView.webViewClient =
            object : WebViewClient() {
                override fun onPageFinished(
                    view: WebView,
                    url: String,
                ) {
                    if (handled) return
                    val cookies = CookieManager.getInstance().getCookie(url) ?: return
                    val cookieMap = mutableMapOf<String, String>()
                    val matcher: Matcher = Pattern.compile("([^=]+)=([^;]*);?\\s?").matcher(cookies)
                    while (matcher.find()) {
                        cookieMap[matcher.group(1)!!.trim()] = matcher.group(2)!!
                    }
                    val token = cookieMap[AUTH_TOKEN]
                    if (!token.isNullOrEmpty()) {
                        handled = true
                        webView.evaluateJavascript(
                            "(function() { var e = document.querySelector('[data-profile-identifier]'); return e ? e.innerHTML : null; })();",
                        ) {
                            val email = it?.replace("\"", "")?.trim()
                            retrieveAc2dmToken(
                                if (email.isNullOrEmpty() || email == "null") null else email,
                                token,
                            )
                        }
                    }
                }
            }

        webView.apply {
            settings.apply {
                databaseEnabled = true
                domStorageEnabled = true
                javaScriptEnabled = true
            }
            loadUrl(EMBEDDED_SETUP_URL)
        }
    }

    private fun retrieveAc2dmToken(
        email: String?,
        oAuthToken: String?,
    ) {
        if (email == null || oAuthToken == null) {
            Toast.makeText(this, "Sign in failed: no email or token", Toast.LENGTH_LONG).show()
            setResult(-3)
            finish()
            return
        }
        Thread {
            val (result, error) = getAC2DMResponse(email, oAuthToken)
            runOnUiThread {
                if (result.isNotEmpty() && result["Token"] != null) {
                    val intent =
                        Intent().apply {
                            putExtra("accountName", result["firstName"].orEmpty() + " " + result["lastName"].orEmpty())
                            putExtra("accountEmail", result["Email"] ?: email)
                            putExtra("accountToken", result["Token"])
                        }
                    setResult(RESULT_OK, intent)
                } else {
                    Toast.makeText(this, "Sign in failed: $error", Toast.LENGTH_LONG).show()
                    setResult(-2)
                }
                finish()
            }
        }.start()
    }

    private fun getAC2DMResponse(
        email: String,
        oAuthToken: String,
    ): Pair<Map<String, String>, String> {
        val token =
            try {
                URLDecoder.decode(oAuthToken, "UTF-8")
            } catch (e: Exception) {
                oAuthToken
            }

        val body =
            FormBody
                .Builder()
                .add("lang", Locale.getDefault().toString().replace("_", "-"))
                .add("google_play_services_version", PLAY_SERVICES_VERSION_CODE.toString())
                .add("sdk_version", BUILD_VERSION_SDK.toString())
                .add("device_country", Locale.getDefault().country.lowercase(Locale.US))
                .add("Email", email)
                .add("service", "ac2dm")
                .add("get_accountid", "1")
                .add("ACCESS_TOKEN", "1")
                .add("callerPkg", "com.google.android.gms")
                .add("add_account", "1")
                .add("Token", token)
                .add("callerSig", "38918a453d07199354f8b19af05ec6562ced5788")
                .add("droidguard_results", "null")
                .build()

        val request =
            Request
                .Builder()
                .url(TOKEN_AUTH_URL)
                .post(body)
                .addHeader("app", "com.google.android.gms")
                .addHeader("User-Agent", "GoogleAuth/1.4")
                .build()

        return try {
            OkHttpClient().newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val map = mutableMapOf<String, String>()
                text.split("\n", "\r").forEach { line ->
                    val kv = line.split("=", limit = 2)
                    if (kv.size == 2) map[kv[0]] = kv[1]
                }
                if (response.isSuccessful) map to "" else map to "HTTP ${response.code} ${map["Error"].orEmpty()}"
            }
        } catch (e: IOException) {
            emptyMap<String, String>() to (e.message ?: "network error")
        }
    }

    companion object {
        private const val EMBEDDED_SETUP_URL = "https://accounts.google.com/EmbeddedSetup"
        private const val AUTH_TOKEN = "oauth_token"
        const val TOKEN_AUTH_URL = "https://android.clients.google.com/auth"
        const val BUILD_VERSION_SDK = 28
        const val PLAY_SERVICES_VERSION_CODE = 19629032
    }
}
