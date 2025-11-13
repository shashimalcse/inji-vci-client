package com.example.vciclient.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vciclient.model.StoredCredential
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialDetailScreen(
    credential: StoredCredential,
    onBack: () -> Unit
) {
    val parsedData = remember(credential) { parseCredentialData(credential.credentialData.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credential Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* More options */ }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Credential Header Card
            CredentialHeaderCard(credential = credential)

            Spacer(modifier = Modifier.height(16.dp))

            // Credential Info Section
            CredentialInfoSection(
                credential = credential,
                parsedData = parsedData
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CredentialHeaderCard(credential: StoredCredential) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFF6B35))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = credential.configurationId,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = credential.issuer.substringAfter("://").substringBefore("/"),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun CredentialInfoSection(
    credential: StoredCredential,
    parsedData: Map<String, Any>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active from (using iat)
            val iatTimestamp = parsedData["iat"] as? Long
            InfoRow(
                label = "Active from",
                value = if (iatTimestamp != null) formatTimestamp(iatTimestamp * 1000) else "N/A"
            )

            Divider()

            // Expiry (using exp)
            val expTimestamp = parsedData["exp"] as? Long
            InfoRow(
                label = "Expiry",
                value = if (expTimestamp != null) formatTimestamp(expTimestamp * 1000) else "N/A"
            )

            Divider()

            // Claims from vc.credentialSubject
            val credentialSubjectClaims = parsedData.filterKeys { it.startsWith("vc.credentialSubject.") && it != "vc.credentialSubject.id" }
            credentialSubjectClaims.forEach { (key, value) ->
                InfoRow(
                    label = formatLabel(key),
                    value = value.toString()
                )
                Divider()
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

private fun parseCredentialData(credentialJson: String): Map<String, Any> {
    return try {
        // Check if it's a JWT (starts with quotes and contains dots)
        val cleanJson = credentialJson.trim('"')
        
        if (cleanJson.contains(".") && cleanJson.split(".").size >= 2) {
            // It's a JWT, decode the payload
            val parts = cleanJson.split(".")
            if (parts.size >= 2) {
                val payload = String(Base64.getUrlDecoder().decode(parts[1]))
                val jsonObject = JsonParser.parseString(payload).asJsonObject
                parseJsonObject(jsonObject)
            } else {
                emptyMap()
            }
        } else {
            // It's already JSON
            val jsonObject = JsonParser.parseString(credentialJson).asJsonObject
            parseJsonObject(jsonObject)
        }
    } catch (e: Exception) {
        mapOf("error" to "Failed to parse credential: ${e.message}")
    }
}

private fun parseJsonObject(jsonObject: JsonObject): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    jsonObject.entrySet().forEach { (key, value) ->
        when {
            value.isJsonPrimitive -> {
                val primitive = value.asJsonPrimitive
                map[key] = when {
                    primitive.isNumber -> primitive.asLong
                    primitive.isBoolean -> primitive.asBoolean
                    else -> primitive.asString
                }
            }
            value.isJsonObject -> {
                // Flatten nested objects
                val nested = parseJsonObject(value.asJsonObject)
                nested.forEach { (nestedKey, nestedValue) ->
                    map["$key.$nestedKey"] = nestedValue
                }
            }
            value.isJsonArray -> map[key] = value.toString()
            else -> map[key] = value.toString()
        }
    }
    return map
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a • d MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatLabel(key: String): String {
    return key.split(".")
        .last()
        .replace("_", " ")
        .replaceFirstChar { it.uppercase() }
}
