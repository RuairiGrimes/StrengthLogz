package com.strengthlogz.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strengthlogz.mobile.data.GoogleSheetsClient
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentSpreadsheetId: String,
    currentServiceAccountJson: String = "",
    lastSyncTime: String,
    onSaveSpreadsheetId: (String) -> Unit,
    onSaveServiceAccountJson: (String) -> Unit = {},
    onClearCloudCredentials: () -> Unit = {},
    onTriggerSync: suspend () -> Result<String>,
    workoutCount: Int,
    setCount: Int,
    exerciseCount: Int,
    bodyWeights: List<BodyWeightEntry> = emptyList(),
    healthConnectAvailable: Boolean = false,
    onSyncHealthConnect: suspend () -> Result<String> = { Result.failure(Exception("Health Connect unavailable")) },
    onAddManualWeight: (String, Double) -> Unit = { _, _ -> },
    onNavigateExercises: () -> Unit = {}
) {
    var sheetId by remember(currentSpreadsheetId) { mutableStateOf(currentSpreadsheetId) }
    var serviceJson by remember(currentServiceAccountJson) { mutableStateOf(currentServiceAccountJson) }
    var showJsonInput by remember { mutableStateOf(currentServiceAccountJson.isBlank()) }
    var showInstructions by remember { mutableStateOf(currentSpreadsheetId.isBlank() || currentServiceAccountJson.isBlank()) }
    var syncStatusMessage by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val extractedEmail = remember(serviceJson) {
        GoogleSheetsClient.extractServiceAccountEmail(serviceJson)
    }
    val isReadyToSync = sheetId.isNotBlank() && extractedEmail != null

    val jsonFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: ""
                if (content.isNotBlank()) {
                    serviceJson = content.trim()
                    onSaveServiceAccountJson(serviceJson)
                    val email = GoogleSheetsClient.extractServiceAccountEmail(serviceJson)
                    syncStatusMessage = if (email != null) {
                        "Loaded Service Account key: $email"
                    } else {
                        "Sync failed: Selected file is not a valid Google Service Account JSON key"
                    }
                    showJsonInput = false
                }
            } catch (e: Exception) {
                syncStatusMessage = "Sync failed: Could not read file (${e.message})"
            }
        }
    }

    val spinAngle = if (isSyncing) {
        val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing)
            ),
            label = "spin"
        )
        angle
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ModernScreenHeader(
            title = "Options & Settings",
            subtitle = "Connect your own Google Sheet, manage body weight & catalog"
        )

        // 1. Google Sheets Cloud Sync & Credentials Card
        StrengthCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AccentOrange.copy(alpha = 0.2f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Google Sheets Cloud Sync",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isReadyToSync) "Configured • Last sync: ${lastSyncTime.ifBlank { "Never" }}"
                                else "Offline mode • Add your credentials below to sync",
                                fontSize = 11.5.sp,
                                color = if (isReadyToSync) IncreaseGreen else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (sheetId.isNotBlank() || serviceJson.isNotBlank()) {
                        IconButton(
                            onClick = {
                                sheetId = ""
                                serviceJson = ""
                                onClearCloudCredentials()
                                syncStatusMessage = "Cleared Google Sheets credentials. Running in local offline mode."
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Credentials",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Step A: Spreadsheet ID or URL
                OutlinedTextField(
                    value = sheetId,
                    onValueChange = {
                        sheetId = it
                        onSaveSpreadsheetId(it)
                    },
                    label = { Text("1. Your Google Spreadsheet URL or ID") },
                    placeholder = { Text("https://docs.google.com/spreadsheets/d/...", fontSize = 12.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = BorderDark,
                        focusedContainerColor = BgDark,
                        unfocusedContainerColor = BgDark
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Step B: Service Account JSON Key
                Surface(
                    color = BgDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (extractedEmail != null) IncreaseGreen.copy(alpha = 0.45f) else BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (extractedEmail != null) Icons.Default.CheckCircle else Icons.Default.Key,
                                    contentDescription = null,
                                    tint = if (extractedEmail != null) IncreaseGreen else AccentPeach,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "2. Service Account JSON Key",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = extractedEmail ?: "No valid JSON key added yet",
                                        fontSize = 11.5.sp,
                                        color = if (extractedEmail != null) IncreaseGreen else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            TextButton(
                                onClick = { showJsonInput = !showJsonInput },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = if (showJsonInput) "Hide" else if (extractedEmail != null) "Change" else "Paste Key",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentOrange
                                )
                            }
                        }

                        if (extractedEmail != null) {
                            Surface(
                                color = SurfaceCard,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.8.dp, BorderDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Share your Google Sheet (Editor) with:",
                                            fontSize = 10.5.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = extractedEmail,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = AccentPeach,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = AccentOrange.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.5f)),
                                        modifier = Modifier.clickable {
                                            clipboardManager.setText(AnnotatedString(extractedEmail))
                                            syncStatusMessage = "Copied Service Account email to clipboard!"
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy Email",
                                                tint = AccentOrange,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "Copy",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentOrange
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = showJsonInput) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            jsonFilePickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, BorderDark),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Import .json File", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text?.trim() ?: ""
                                            if (clip.isNotBlank()) {
                                                serviceJson = clip
                                                onSaveServiceAccountJson(clip)
                                                val email = GoogleSheetsClient.extractServiceAccountEmail(clip)
                                                syncStatusMessage = if (email != null) {
                                                    "Pasted valid Service Account key: $email"
                                                } else {
                                                    "Sync failed: Clipboard does not contain a valid Service Account JSON"
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, BorderDark),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentPeach, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Paste Clipboard", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }

                                OutlinedTextField(
                                    value = serviceJson,
                                    onValueChange = {
                                        serviceJson = it
                                        onSaveServiceAccountJson(it)
                                    },
                                    label = { Text("Paste Service Account JSON contents") },
                                    placeholder = { Text("{\n  \"type\": \"service_account\",\n  \"client_email\": \"...\"\n}", fontSize = 11.sp, color = TextMuted) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 95.dp, max = 160.dp),
                                    maxLines = 6,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = AccentOrange,
                                        unfocusedBorderColor = BorderDark,
                                        focusedContainerColor = SurfaceDark,
                                        unfocusedContainerColor = SurfaceDark
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }

                // Sync Now Button
                Button(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            syncStatusMessage = "Syncing with Google Sheets..."
                            val result = onTriggerSync()
                            isSyncing = false
                            syncStatusMessage = if (result.isSuccess) {
                                result.getOrNull() ?: "Sync complete!"
                            } else {
                                "Sync failed: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    enabled = !isSyncing && isReadyToSync,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color(0xFF140D07),
                        disabledContainerColor = SurfaceDark,
                        disabledContentColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier
                            .size(17.dp)
                            .then(if (isSyncing) Modifier.rotate(spinAngle) else Modifier)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isSyncing) "Syncing..." else if (isReadyToSync) "Sync Now with Google Sheets" else "Enter Sheet URL & JSON Key to Sync",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(visible = syncStatusMessage.isNotBlank()) {
                    val isErr = syncStatusMessage.startsWith("Sync failed")
                    Surface(
                        color = if (isErr) DecreaseRed.copy(alpha = 0.15f) else IncreaseGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isErr) DecreaseRed.copy(alpha = 0.4f) else IncreaseGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = syncStatusMessage,
                            color = if (isErr) DecreaseRed else IncreaseGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Built-in Step-by-Step Setup Instructions Accordion
                Surface(
                    color = BgDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, BorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showInstructions = !showInstructions }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = null,
                                    tint = GoldPR,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "How to Connect Your Own Google Sheet (5-Min Guide)",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (showInstructions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(visible = showInstructions) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                                InstructionStepRow(
                                    stepNum = "1",
                                    title = "Create a Google Cloud Project",
                                    body = "Open console.cloud.google.com, create a new project, and enable the \"Google Sheets API\" in APIs & Services → Library."
                                )
                                InstructionStepRow(
                                    stepNum = "2",
                                    title = "Generate a Service Account JSON Key",
                                    body = "Go to IAM & Admin → Service Accounts → Create Service Account. Open the account's \"Keys\" tab → Add Key → Create new key → select JSON. Save the downloaded .json file."
                                )
                                InstructionStepRow(
                                    stepNum = "3",
                                    title = "Create & Share Your Google Sheet",
                                    body = "Create a blank Google Sheet in Google Drive. Click \"Share\" and paste your Service Account's client_email (with Editor access)."
                                )
                                InstructionStepRow(
                                    stepNum = "4",
                                    title = "Paste Details Above & Tap Sync",
                                    body = "Paste your Google Sheet link or ID in Step 1 and import/paste your JSON key in Step 2. StrengthLogz automatically creates the Workouts, Sets, and Exercises tabs!"
                                )
                                Surface(
                                    color = SurfaceCard,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "💡 Offline-First Guarantee: You can use StrengthLogz 100% offline without Google Sheets. All workouts, programs, and exercises are saved locally on your device.",
                                        fontSize = 11.sp,
                                        color = AccentPeach,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Body Weight & Health Connect Card
        BodyWeightCard(
            bodyWeights = bodyWeights,
            healthConnectAvailable = healthConnectAvailable,
            onSyncHealthConnect = onSyncHealthConnect,
            onAddManualWeight = onAddManualWeight
        )

        // 3. Exercise Catalog Manager Link Card
        StrengthCard(
            shape = RoundedCornerShape(16.dp),
            onClick = onNavigateExercises,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AccentOrange.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Built-In Exercise Catalog",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "$exerciseCount movements pre-loaded • Create, edit or flag bodyweight lifts",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Manage Exercises",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 4. Local Database Summary Strip
        StrengthCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BlueAccent.copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = BlueAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "Local Device Storage",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Workouts" to workoutCount,
                        "Sets" to setCount,
                        "Exercises" to exerciseCount
                    ).forEach { (label, count) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = BgDark,
                            border = BorderStroke(0.8.dp, BorderDark)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentPeach
                                )
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun InstructionStepRow(
    stepNum: String,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = AccentOrange.copy(alpha = 0.2f),
            border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.5f)),
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNum,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentOrange
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = body,
                fontSize = 11.5.sp,
                color = TextSecondary,
                lineHeight = 15.5.sp
            )
        }
    }
}
