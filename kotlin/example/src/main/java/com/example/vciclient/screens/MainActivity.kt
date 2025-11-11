
@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.vciclient.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vciclient.ui.theme.VCIClientTheme
import com.example.vciclient.util.ConfigConstants.Companion.credentialIssuer
import com.example.vciclient.util.NotificationCenter
import com.example.vciclient.util.VCIClientWrapper
import com.example.vciclient.model.StoredCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VCIClientTheme {
                VCIClientDemoUI()
            }
        }
    }
}

@Composable
fun VCIClientDemoUI() {
    var resultText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var credentialKeys by remember { mutableStateOf(listOf<String>()) }
    var storedCredentials by remember { mutableStateOf(listOf<StoredCredential>()) }
    var selectedCredential by remember { mutableStateOf<StoredCredential?>(null) }
    var authUrl by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedNavIndex by remember { mutableStateOf(0) }
    var pendingOfferData by remember { mutableStateOf<com.example.vciclient.model.CredentialOfferData?>(null) }
    var showConsentScreen by remember { mutableStateOf(false) }
    var scannedOfferUrl by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val navigationItems = remember {
        listOf(
            WalletNavItem("Wallet", Icons.Outlined.Wallet),
            WalletNavItem("Settings", Icons.Outlined.Settings),
            WalletNavItem("Scan", Icons.Outlined.QrCodeScanner)
        )
    }

    val startCredentialOfferFlow = {
        clearResults(
            onClear = { isScanning = true },
            resultTextUpdater = { resultText = it },
            keyUpdater = { credentialKeys = it }
        )
    }

    LaunchedEffect(Unit) {
        NotificationCenter.observe("ShowAuthWebView") { url ->
            authUrl = url
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { WalletTopAppBar(isLoading = isLoading) },
            bottomBar = {
                WalletBottomBar(
                    items = navigationItems,
                    selectedIndex = selectedNavIndex,
                    onSelected = { index ->
                        if (index == 2) { // Scan button
                            startCredentialOfferFlow()
                        } else {
                            selectedNavIndex = index
                        }
                    }
                )
            }
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)

            when (selectedNavIndex) {
                0 -> WalletHomeContent(
                    modifier = contentModifier,
                    credentials = storedCredentials,
                    resultText = resultText,
                    isLoading = isLoading,
                    onScanClicked = startCredentialOfferFlow,
                    onCredentialClick = { credential ->
                        selectedCredential = credential
                    }
                )

                1 -> PlaceholderSection(
                    modifier = contentModifier,
                    title = "Wallet Settings"
                )

                else -> {} // Scan handled in bottom bar
            }
        }

        // Show credential detail if one is selected
        selectedCredential?.let { credential ->
            CredentialDetailScreen(
                credential = credential,
                onBack = { selectedCredential = null }
            )
        }

        // Show consent screen if offer data is available
        if (showConsentScreen && pendingOfferData != null) {
            CredentialOfferConsentScreen(
                offerData = pendingOfferData!!,
                isLoading = isLoading,
                onProceed = {
                    showConsentScreen = false
                    isLoading = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            scannedOfferUrl?.let { offerUrl ->
                                VCIClientWrapper().startCredentialOfferFlow(
                                    scanned = offerUrl
                                ) { response ->
                                    isLoading = false
                                    if (response != null) {
                                        // Store the credential
                                        val newCredential = StoredCredential(
                                            id = UUID.randomUUID().toString(),
                                            configurationId = response.credentialConfigurationId,
                                            issuer = response.credentialIssuer,
                                            credentialData = response.credential,
                                            rawResponse = response.toJsonString()
                                        )
                                        storedCredentials = storedCredentials + newCredential
                                        credentialKeys = storedCredentials.map { it.configurationId }
                                        resultText = "✅ Credential Issued: ${response.credentialConfigurationId}"
                                        pendingOfferData = null
                                        scannedOfferUrl = null
                                    } else {
                                        resultText = "❌ Failed to receive credential"
                                        pendingOfferData = null
                                        scannedOfferUrl = null
                                    }
                                }
                            }
                        }
                    }
                },
                onDecline = {
                    showConsentScreen = false
                    pendingOfferData = null
                    scannedOfferUrl = null
                    resultText = "❌ Credential offer declined"
                }
            )
        }

        authUrl?.let { url ->
            AuthOverlay(
                authUrl = url,
                onAuthCodeReceived = { code ->
                    NotificationCenter.post("AuthCodeReceived", code)
                },
                onClose = { authUrl = null }
            )
        }

        if (isScanning) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                ScanScreen(
                    onQRCodeScanned = { scannedData ->
                        isScanning = false
                        isLoading = true
                        scannedOfferUrl = scannedData
                        
                        // Fetch offer metadata first
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val offerData = VCIClientWrapper().fetchCredentialOfferData(scannedData)
                                isLoading = false
                                
                                if (offerData != null) {
                                    pendingOfferData = offerData
                                    showConsentScreen = true
                                } else {
                                    resultText = "❌ Failed to fetch credential offer"
                                    scannedOfferUrl = null
                                }
                            }
                        }
                    },
                    onClose = { isScanning = false }
                )
            }
        }
    }
}

@Composable
private fun WalletHomeContent(
    modifier: Modifier,
    credentials: List<StoredCredential>,
    resultText: String,
    isLoading: Boolean,
    onScanClicked: () -> Unit,
    onCredentialClick: (StoredCredential) -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (credentials.isEmpty() && resultText.isEmpty() && !isLoading) {
            EmptyWalletState(onScanClicked = onScanClicked)
        } else {
            CredentialShelf(
                credentials = credentials,
                onCredentialClick = onCredentialClick
            )
            ResponsePanel(resultText = resultText, isLoading = isLoading)
        }
    }
}

@Composable
private fun EmptyWalletState(onScanClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Wallet,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Your wallet is empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Scan a QR code from an issuer to add your first verifiable credential",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CredentialShelf(
    credentials: List<StoredCredential>,
    onCredentialClick: (StoredCredential) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        credentials.forEachIndexed { index, credential ->
            CredentialCard(
                credential = credential,
                index = index,
                onClick = { onCredentialClick(credential) }
            )
        }
    }
}

@Composable
private fun CredentialCard(
    credential: StoredCredential,
    index: Int,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFFFF6B35) // Orange color similar to the image
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Top section
            Column(
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = credential.configurationId,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "WSO2 Demo",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ResponsePanel(
    resultText: String,
    isLoading: Boolean
) {
    if (resultText.isEmpty() && !isLoading) return

    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = "Latest response",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Raw issuer payload",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            Divider()
            SelectionContainer {
                Text(
                    text = if (resultText.isNotEmpty()) resultText else "Awaiting issuer response...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun PlaceholderSection(
    modifier: Modifier,
    title: String
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Wallet,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Design for this destination is coming soon. Use the Wallet tab to explore the OpenID4VCI demo.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WalletTopAppBar(isLoading: Boolean) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Wallet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}

@Composable
private fun WalletBottomBar(
    items: List<WalletNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    NavigationBar {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelected(index) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}

@Composable
private fun AuthOverlay(
    authUrl: String,
    onAuthCodeReceived: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SmallTopAppBar(
                    title = { Text("Authorize with issuer") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close authorization"
                            )
                        }
                    }
                )
                Divider()
                AuthWebViewScreen(
                    authUrl = authUrl,
                    onAuthCodeReceived = onAuthCodeReceived,
                    onClose = onClose,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private data class WalletNavItem(
    val label: String,
    val icon: ImageVector
)

private fun clearResults(
    onClear: () -> Unit,
    resultTextUpdater: (String) -> Unit,
    keyUpdater: (List<String>) -> Unit
) {
    resultTextUpdater("")
    keyUpdater(emptyList())
    onClear()
}
