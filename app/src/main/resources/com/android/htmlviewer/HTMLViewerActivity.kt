/*
 * Copyright (C) 2008 The Android Open Source Project
 *               2023 Akane Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.htmlviewer;

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.provider.Browser
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.IOException
import java.io.InputStream
import java.net.URISyntaxException
import java.util.zip.GZIPInputStream

/**
 * Simple activity that shows the requested HTML page. This utility is
 * purposefully very limited in what it supports, including no network or
 * JavaScript.
 */
open class HTMLViewerActivity : Activity() {

    private var mWebView: WebView? = null
    private var mLoading: View? = null
    private var mIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView()

        mWebView = findViewById(R.id.webview)
        mLoading = findViewById(R.id.loading)
        mWebView!!.webChromeClient = ChromeClient()
        mWebView!!.webViewClient = ViewClient()

        val s = mWebView!!.settings
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.blockNetworkLoads = true
        s.allowFileAccess = true

        // Javascript is purposely disabled, so that nothing can be
        // automatically run.
        s.javaScriptEnabled = false
        s.defaultTextEncodingName = "utf-8"
        mIntent = intent
        setBackButton()
        loadUrl()
    }

    private fun setContentView() {
        setContentView(R.layout.main)
    }

    private fun loadUrl() {
        if (mIntent!!.hasExtra(Intent.EXTRA_TITLE)) {
            title = mIntent!!.getStringExtra(Intent.EXTRA_TITLE)
        }
        mWebView!!.loadUrl(mIntent!!.data.toString())
    }

    private fun setBackButton() {
        if (actionBar != null) {
            actionBar!!.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        mWebView!!.destroy()
    }

    private inner class ChromeClient : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String) {
            if (!intent.hasExtra(Intent.EXTRA_TITLE)) {
                this@HTMLViewerActivity.title = title
            }
        }
    }

    private inner class ViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            mLoading!!.visibility = View.GONE
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            // Perform generic parsing of the URI to turn it into an Intent.
            val intent: Intent = try {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } catch (ex: URISyntaxException) {
                Log.w(TAG, "Bad URI " + url + ": " + ex.message)
                Toast.makeText(
                    this@HTMLViewerActivity,
                    R.string.cannot_open_link, Toast.LENGTH_SHORT
                ).show()
                return true
            }
            // Sanitize the Intent, ensuring web pages can not bypass browser
            // security (only access to BROWSABLE activities).
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.setComponent(null)
            val selector = intent.selector
            if (selector != null) {
                selector.addCategory(Intent.CATEGORY_BROWSABLE)
                selector.setComponent(null)
            }
            // Pass the package name as application ID so that the intent from the
            // same application can be opened in the same tab.
            intent.putExtra(
                Browser.EXTRA_APPLICATION_ID,
                view.context.packageName
            )
            try {
                view.context.startActivity(intent)
            } catch (ex: ActivityNotFoundException) {
                Log.w(TAG, "No application can handle $url")
                Toast.makeText(
                    this@HTMLViewerActivity,
                    R.string.cannot_open_link, Toast.LENGTH_SHORT
                ).show()
            } catch (ex: SecurityException) {
                Log.w(TAG, "No application can handle $url")
                Toast.makeText(
                    this@HTMLViewerActivity,
                    R.string.cannot_open_link, Toast.LENGTH_SHORT
                ).show()
            }
            return true
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val uri = request.url
            if (ContentResolver.SCHEME_FILE == uri.scheme && uri.path!!.endsWith(".gz")) {
                Log.d(TAG, "Trying to decompress $uri on the fly")
                try {
                    val `in`: InputStream = GZIPInputStream(
                        contentResolver.openInputStream(uri)
                    )
                    val resp = WebResourceResponse(
                        intent.type, "utf-8", `in`
                    )
                    resp.setStatusCodeAndReasonPhrase(200, "OK")
                    return resp
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to decompress; falling back", e)
                }
            }
            return null
        }
    }

    companion object {
        private const val TAG = "HTMLViewer"
    }
}
