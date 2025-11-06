
@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.vciclient.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vciclient.util.ConfigConstants.Companion.credentialIssuer
import com.example.vciclient.util.NotificationCenter
import com.example.vciclient.util.VCIClientWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VCIClientDemoUI()
        }
    }
}

@Composable
fun VCIClientDemoUI() {

    var resultText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var credentialKeys by remember { mutableStateOf(listOf<String>()) }
    var authUrl by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        NotificationCenter.observe("ShowAuthWebView") { url ->
            authUrl = url
        }
    }


    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
            ,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "🔐 VCIClient Demo",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold

            )
            Spacer(modifier = Modifier.height(25.dp))

            Button(
                onClick = {
                    clearResults(
                        onClear = {
                            isScanning = true
                        },
                        resultTextUpdater = { resultText = it },
                        keyUpdater = { credentialKeys = it }
                    )

                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("Start Credential Offer Flow")
            }

            Button(
                onClick = {
                    clearResults(
                        onClear = {
                            isLoading = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    VCIClientWrapper().fetchCredentialTypes(
                                        credentialIssuer = credentialIssuer
                                    ) { rawJson, keys ->
                                        resultText = rawJson
                                        credentialKeys = keys
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        resultTextUpdater = { resultText = it },
                        keyUpdater = { credentialKeys = it }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
            ) {
                Text("Fetch Credential Types")
            }

            Button(
                onClick = {
                    clearResults(
                        onClear = {
                            isLoading = true
                            scope.launch {
                                withContext(Dispatchers.IO){
                                    VCIClientWrapper().startTrustedIssuerFlow {
                                        resultText = it
                                        isLoading = false
                                    }
                                }

                            }
                        },
                        resultTextUpdater = { resultText = it },
                        keyUpdater = { credentialKeys = it }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Start Trusted Issuer Flow")
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally).padding(
                        PaddingValues(0.dp,5.dp)
                    ),
                    color = Color(0xFF2196F3)
                )
            }

            if (resultText.isNotEmpty()) {
                Text("Raw JSON", fontWeight = FontWeight.SemiBold)

                val scrollState = rememberScrollState()

                SelectionContainer {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp) // Set desired fixed height
                            .verticalScroll(scrollState)
                            .background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(resultText)
                    }
                }
            }
            if (credentialKeys.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3E5F5), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text("🔑 Credential Configuration IDs:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    credentialKeys.forEach { key ->
                        Text("• $key", color = Color(0xFF6A1B9A), fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        authUrl?.let { url ->
            AuthWebViewScreen(
                authUrl = url,
                onAuthCodeReceived = { code ->
                    // Simulate NotificationCenter.post("AuthCodeReceived", code)
                    NotificationCenter.post("AuthCodeReceived", code)
                },
                onClose = {
                    authUrl = null
                }
            )
        }

    }
    if (isScanning) {
        ScanScreen(
            onQRCodeScanned = { scannedData ->
                isScanning = false
                isLoading = true
                scope.launch {
                    withContext(Dispatchers.IO) {
                        VCIClientWrapper().startCredentialOfferFlow(
                            scanned = scannedData
                        ) {
                            resultText = it
                            isLoading = false
                        }
                    }

                }
            },
            onClose = {
                isScanning = false
            }
        )
    }

}

private fun clearResults(
    onClear: () -> Unit,
    resultTextUpdater: (String) -> Unit,
    keyUpdater: (List<String>) -> Unit
) {
    resultTextUpdater("")
    keyUpdater(emptyList())
    onClear()
}


