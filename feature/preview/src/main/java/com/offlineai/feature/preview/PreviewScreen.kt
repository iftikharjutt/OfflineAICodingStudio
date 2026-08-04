package com.offlineai.feature.preview

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val consoleLogs by viewModel.consoleLogs.collectAsState()

    var inputUrl by remember(currentUrl) {
        mutableStateOf(currentUrl.ifEmpty { "http://127.0.0.1:8080/index.html" })
    }

    var showLogs by remember { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Editable Address Bar & Server Controls
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    readOnly = false,
                    singleLine = true,
                    label = { Text("Address Bar (URL)") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            var target = inputUrl.trim()
                            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                target = "http://$target"
                            }
                            viewModel.updateUrl(target)
                            activeWebView?.loadUrl(target)
                        }
                    )
                )

                IconButton(
                    onClick = {
                        var target = inputUrl.trim()
                        if (!target.startsWith("http://") && !target.startsWith("https://")) {
                            target = "http://$target"
                        }
                        viewModel.updateUrl(target)
                        activeWebView?.loadUrl(target)
                    }
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Go to URL", tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = {
                    activeWebView?.reload()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }

                IconButton(onClick = { showLogs = !showLogs }) {
                    BadgedBox(
                        badge = {
                            if (consoleLogs.isNotEmpty()) {
                                Badge { Text("${consoleLogs.size}") }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = "Console Logs")
                    }
                }
            }
        }

        HorizontalDivider()

        // WebView Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                val log = "Error: ${error?.description} on ${request?.url}"
                                viewModel.addConsoleLog(log)
                            }
                        }
                        activeWebView = this
                        loadUrl(inputUrl)
                    }
                },
                update = { webView ->
                    activeWebView = webView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Optional Console Logs Drawer
            if (showLogs) {
                Surface(
                    color = Color(0xEE1E1E1E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Preview Console", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { showLogs = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                        LazyColumn {
                            items(consoleLogs) { log ->
                                Text(
                                    text = log,
                                    color = if (log.contains("Error")) Color.Red else Color.Green,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
