package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.backup.BackupManager
import com.example.data.preferences.ThemeMode
import com.example.receiver.FocusDeviceAdminReceiver
import com.example.ui.common.CheatProtectionAuthDialog
import com.example.ui.common.PendingLooseningAction
import com.example.ui.common.StrictModeActivationDialog
import com.example.ui.viewmodel.FocusViewModel
import com.example.util.PermissionHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: FocusViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val baseThemeMode by viewModel.baseThemeMode.collectAsStateWithLifecycle()
    val secondaryPalette by viewModel.secondaryPalette.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isCheatProtectionEnabled by viewModel.isCheatProtectionEnabled.collectAsStateWithLifecycle()
    val isInvincibleModeEnabled by viewModel.isInvincibleModeEnabled.collectAsStateWithLifecycle()
    val isClockTamperProtectionEnabled by viewModel.isClockTamperProtectionEnabled.collectAsStateWithLifecycle()
    val isClockTampered by viewModel.isClockTampered.collectAsStateWithLifecycle()
    val cognitivePassphrase by viewModel.cognitivePassphrase.collectAsStateWithLifecycle()
    val emergencyBreaksRemaining by viewModel.emergencyBreaksRemaining.collectAsStateWithLifecycle()
    val activeEmergencyBreakUntil by viewModel.activeEmergencyBreakUntil.collectAsStateWithLifecycle()

    val connectedGmailAccount by viewModel.connectedGmailAccount.collectAsStateWithLifecycle()
    val lastCloudBackupTime by viewModel.lastCloudBackupTime.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    var hasAccessibility by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var hasUsageStats by remember { mutableStateOf(PermissionHelper.isUsageStatsPermissionGranted(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }
    var hasBattery by remember { mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context)) }

    var showThemesSheet by remember { mutableStateOf(false) }
    var showBackupRestoreModal by remember { mutableStateOf(false) }
    var showStrictModeDialog by remember { mutableStateOf(false) }
    var showInvincibleConfirmDialog by remember { mutableStateOf(false) }
    var showEmergencyBreakConfirmDialog by remember { mutableStateOf(false) }
    var pendingCheatAction by remember { mutableStateOf<PendingLooseningAction?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportOfflineBackup(uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (isCheatProtectionEnabled) {
                pendingImportUri = uri
                pendingCheatAction = PendingLooseningAction(
                    title = "Authorize Backup Import",
                    description = "Importing an offline backup replaces current limits and schedules. Enter your verbatim passphrase to authorize this action.",
                    onAuthorized = {
                        pendingImportUri?.let { targetUri ->
                            viewModel.importOfflineBackup(targetUri) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                        pendingImportUri = null
                    }
                )
            } else {
                viewModel.importOfflineBackup(uri) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var triedWithoutIdToken by remember { mutableStateOf(false) }
    var launchFallbackWithoutIdToken by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email ?: "User"
            val idToken = account?.idToken
            viewModel.onGoogleSignInSuccess(email, idToken) {
                Toast.makeText(context, "Connected to $email", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            if (!triedWithoutIdToken && (e.statusCode == 10 || e.statusCode == 12500)) {
                triedWithoutIdToken = true
                launchFallbackWithoutIdToken = true
                return@rememberLauncherForActivityResult
            }
            val last = runCatching { GoogleSignIn.getLastSignedInAccount(context) }.getOrNull()
            if (last?.email != null) {
                viewModel.onGoogleSignInSuccess(last.email!!, last.idToken) {
                    Toast.makeText(context, "Connected to ${last.email}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Sign-in cancelled", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            val last = runCatching { GoogleSignIn.getLastSignedInAccount(context) }.getOrNull()
            if (last?.email != null) {
                viewModel.onGoogleSignInSuccess(last.email!!, last.idToken) {
                    Toast.makeText(context, "Connected to ${last.email}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Sign-in cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(launchFallbackWithoutIdToken) {
        if (launchFallbackWithoutIdToken) {
            launchFallbackWithoutIdToken = false
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(context, gso)
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }

    fun launchGoogleSignIn() {
        triedWithoutIdToken = false
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
        val webClientIdRes = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (webClientIdRes != 0) {
            val webClientId = context.getString(webClientIdRes)
            if (webClientId.isNotBlank()) {
                gsoBuilder.requestIdToken(webClientId)
            }
        }
        val googleSignInClient = GoogleSignIn.getClient(context, gsoBuilder.build())
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    val isBreakActive by viewModel.isEmergencyBreakActive.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(context)
                hasUsageStats = PermissionHelper.isUsageStatsPermissionGranted(context)
                hasOverlay = PermissionHelper.canDrawOverlays(context)
                hasBattery = PermissionHelper.isBatteryOptimizationIgnored(context)
                
                // If returning from Device Admin screen and user wanted to enable invincible
                val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, FocusDeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(adminComponent) && !isInvincibleModeEnabled && showInvincibleConfirmDialog) {
                    // Do nothing, dialog will show
                } else if (!dpm.isAdminActive(adminComponent)) {
                    showInvincibleConfirmDialog = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showStrictModeDialog) {
        StrictModeActivationDialog(
            initialPassphrase = cognitivePassphrase,
            onConfirm = { definedPassphrase ->
                viewModel.setCognitivePassphrase(definedPassphrase)
                viewModel.setCheatProtection(definedPassphrase)
                showStrictModeDialog = false
            },
            onDismiss = { showStrictModeDialog = false }
        )
    }

    if (showInvincibleConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showInvincibleConfirmDialog = false },
            title = { Text("Point of No Return") },
            text = { Text("This will make the app completely invincible. You will not be able to uninstall the app, remove its permissions, or disable its services while a focus session is active. Do you accept this strict commitment?") },
            confirmButton = {
                Button(
                    onClick = {
                        showInvincibleConfirmDialog = false
                        viewModel.setInvincibleModeEnabled(true)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Make Invincible")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showInvincibleConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val view = androidx.compose.ui.platform.LocalView.current

    if (showEmergencyBreakConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyBreakConfirmDialog = false },
            title = { Text("Take Emergency Break (10m)") },
            text = { Text("This will consume 1 of your 3 weekly emergency breaks for an immediate 10-minute window. All app and website blocking will be paused for exactly 10 minutes without countdowns or warnings. Do you wish to proceed?") },
            confirmButton = {
                Button(
                    onClick = {
                        com.example.util.FocusHaptics.rejectOrDelete(view)
                        showEmergencyBreakConfirmDialog = false
                        viewModel.takeEmergencyBreak()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Start Break (10m)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEmergencyBreakConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingCheatAction?.let { action ->
        CheatProtectionAuthDialog(
            action = action,
            onVerify = { viewModel.verifyCheatPassphrase(it) },
            onDismiss = { pendingCheatAction = null }
        )
    }

    if (showThemesSheet) {
        ThemesBottomSheet(
            currentBaseMode = baseThemeMode,
            currentPalette = secondaryPalette,
            onSelectBaseMode = { viewModel.setBaseThemeMode(it) },
            onSelectPalette = { viewModel.setSecondaryPalette(it) },
            onDismiss = { showThemesSheet = false }
        )
    }

    if (showBackupRestoreModal) {
        BackupRestoreDialog(
            connectedGmail = connectedGmailAccount,
            lastCloudBackupTime = lastCloudBackupTime,
            isSyncing = isSyncing,
            onSignInGoogle = { launchGoogleSignIn() },
            onSignOutGoogle = {
                val performSignOut = {
                    val webClientId = BackupManager.getDefaultWebClientId(context)
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build()
                    GoogleSignIn.getClient(context, gso).signOut()
                    viewModel.signOutGmail()
                    Toast.makeText(context, "Signed out of Google account", Toast.LENGTH_SHORT).show()
                }
                if (isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Sign Out Google Account",
                        description = "Disconnecting your cloud backup account during active Strict Mode requires passphrase verification.",
                        onAuthorized = { performSignOut() }
                    )
                } else {
                    performSignOut()
                }
            },
            onSyncNow = {
                viewModel.syncNow { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onRestoreBackup = {
                viewModel.restoreBackupNow { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onExportJson = {
                exportDocumentLauncher.launch("focus_lock_backup_${System.currentTimeMillis()}.json")
            },
            onImportJson = {
                importDocumentLauncher.launch(arrayOf("application/json", "*/*"))
            },
            onDismiss = { showBackupRestoreModal = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Section: Themes & Appearance
        item {
            Text(
                text = "Appearance & Themes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("themes_setting_card")
                    .clickable { showThemesSheet = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Themes",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Themes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${baseThemeMode.displayName} • ${secondaryPalette.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 3 Color Preview Dots
                    val dots = remember(secondaryPalette, baseThemeMode) {
                        secondaryPalette.getPreviewDots(baseThemeMode)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        dots.forEach { dotColor ->
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(dotColor, androidx.compose.foundation.shape.CircleShape)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Themes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Section: Backup & Restore
        item {
            Text(
                text = "Data & Storage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBackupRestoreModal = true }
                    .testTag("backup_restore_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_cloud_backup),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Backup & Restore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (connectedGmailAccount != null) "Cloud: $connectedGmailAccount" else "Gmail Cloud Sync & Offline JSON",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectedGmailAccount != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Backup & Restore",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Section: Emergency Failsafe System
        item {
            Text(
                text = "Emergency Failsafe System",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("emergency_failsafe_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.HourglassBottom,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "The Emergency Failsafe System",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$emergencyBreaksRemaining of 3 breaks remaining this week",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (emergencyBreaksRemaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val buttonText = when {
                        isBreakActive -> "Emergency Break Active (Resumes Automatically)"
                        emergencyBreaksRemaining > 0 -> "Take Emergency Break (10m)"
                        else -> "No Emergency Breaks Left This Week"
                    }
                    val buttonEnabled = !isBreakActive && emergencyBreaksRemaining > 0

                    Button(
                        onClick = {
                            com.example.util.FocusHaptics.rejectOrDelete(view)
                            val started = viewModel.startEmergencyBreak(context)
                            if (started) {
                                Toast.makeText(
                                    context,
                                    "10-minute emergency break started. All restrictions paused.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, "Could not start emergency break.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = buttonEnabled,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("take_emergency_break_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassBottom,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buttonText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Section: Cheat Protection Lock (Change 4)
        item {
            Text(
                text = "Cheating Protection & Strict Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cheat_protection_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isCheatProtectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCheatProtectionEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isCheatProtectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Strict Mode / Anti-Cheat Lock",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isCheatProtectionEnabled) "Active • Verbatim passphrase required to loosen" else "Disabled • Rules can be changed freely",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCheatProtectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isCheatProtectionEnabled,
                            onCheckedChange = { willEnable ->
                                if (willEnable) {
                                    showStrictModeDialog = true
                                } else {
                                    pendingCheatAction = PendingLooseningAction(
                                        title = "Disable Strict Mode",
                                        description = "Disabling Strict Mode allows all rules and limits to be modified freely. Enter your reflective passphrase verbatim to authorize.",
                                        onAuthorized = { viewModel.disableCheatProtection() }
                                    )
                                }
                            },
                            modifier = Modifier.testTag("cheat_protection_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "When active, any action that deletes a rule, increases/removes a screen-time limit, or pauses protection strictly requires typing a 600–1000 character passphrase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (isCheatProtectionEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Strict Mode (Device Administrator)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isInvincibleModeEnabled) "Active • Device Admin active (anti-uninstall barrier)" else "Disabled • Device Admin privileges inactive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isInvincibleModeEnabled,
                                onCheckedChange = { willEnable ->
                                    if (willEnable) {
                                        val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                                        val adminComponent = ComponentName(context, FocusDeviceAdminReceiver::class.java)
                                        if (dpm.isAdminActive(adminComponent)) {
                                            showInvincibleConfirmDialog = true
                                        } else {
                                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for Invincible Mode to prevent uninstallation.")
                                            }
                                            context.startActivity(intent)
                                            showInvincibleConfirmDialog = true
                                        }
                                    } else {
                                        pendingCheatAction = PendingLooseningAction(
                                            title = "Disable Invincible Mode",
                                            description = "Disabling Invincible Mode will allow uninstalling the app and changing system settings.",
                                            onAuthorized = { viewModel.setInvincibleModeEnabled(false) }
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onError,
                                    checkedTrackColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
            }
        }

        // Section: Clock Tamper Protection Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clock_tamper_protection_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isClockTamperProtectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isClockTamperProtectionEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = if (isClockTamperProtectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Clock Tamper Protection",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isClockTamperProtectionEnabled) "Active • Monitored via hardware CPU uptime" else "Disabled • System clock untracked",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isClockTamperProtectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isClockTamperProtectionEnabled,
                            onCheckedChange = { willEnable ->
                                if (willEnable) {
                                    viewModel.setClockTamperProtectionEnabled(true)
                                } else {
                                    if (isCheatProtectionEnabled) {
                                        pendingCheatAction = PendingLooseningAction(
                                            title = "Disable Clock Tamper Protection",
                                            description = "Disabling clock tamper protection allows device time adjustments to affect schedules. Enter your verbatim passphrase to authorize.",
                                            onAuthorized = { viewModel.setClockTamperProtectionEnabled(false) }
                                        )
                                    } else {
                                        viewModel.setClockTamperProtectionEnabled(false)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("clock_tamper_protection_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    if (isClockTampered) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Clock drift detected! Rules are running on hardware True Time.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: System Permissions Status
        item {
            Text(
                text = "System Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    PermissionStatusRow(
                        title = "Accessibility Service",
                        isGranted = hasAccessibility,
                        onClick = { PermissionHelper.openAccessibilitySettings(context) }
                    )
                    PermissionStatusRow(
                        title = "Usage Access",
                        isGranted = hasUsageStats,
                        onClick = { PermissionHelper.openUsageAccessSettings(context) }
                    )
                    PermissionStatusRow(
                        title = "Display Over Other Apps",
                        isGranted = hasOverlay,
                        onClick = { PermissionHelper.openOverlaySettings(context) }
                    )
                    PermissionStatusRow(
                        title = "Battery Optimization Exemption",
                        isGranted = hasBattery,
                        onClick = { PermissionHelper.openBatteryOptimizationSettings(context) }
                    )
                }
            }
        }

        // Section: OEM Tips if applicable
        if (PermissionHelper.isAggressiveOem()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${PermissionHelper.getOemName()} Device Notice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Some phone manufacturers automatically kill background services. Ensure Focus Lock has 'Autostart' enabled in phone settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Section: About
        item {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Focus Lock",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0 • 100% Local & Private",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No analytics, no ads, and no external servers. All rules, logs, and database records remain exclusively on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isGranted) "Granted" else "Fix in Settings",
            style = MaterialTheme.typography.labelMedium,
            color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun BackupRestoreDialog(
    connectedGmail: String?,
    lastCloudBackupTime: Long,
    isSyncing: Boolean,
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onSyncNow: () -> Unit,
    onRestoreBackup: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val displayName = remember(connectedGmail) {
        if (connectedGmail != null) {
            runCatching {
                val acct = GoogleSignIn.getLastSignedInAccount(context)
                if (acct?.email.equals(connectedGmail, ignoreCase = true)) {
                    acct?.displayName?.takeIf { it.isNotBlank() }
                } else null
            }.getOrNull()
        } else null
    }

    val formattedLastBackup = remember(lastCloudBackupTime) {
        if (lastCloudBackupTime > 0) {
            val diffMs = System.currentTimeMillis() - lastCloudBackupTime
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastCloudBackupTime))
            if (diffMs in 0..60_000L) {
                "Just now ($timeStr)"
            } else {
                SimpleDateFormat("HH:mm (MMM d)", Locale.getDefault()).format(Date(lastCloudBackupTime))
            }
        } else {
            "Just now"
        }
    }

    LaunchedEffect(connectedGmail, lastCloudBackupTime) {
        if (connectedGmail != null && lastCloudBackupTime == 0L && !isSyncing) {
            onSyncNow()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp)
                .testTag("backup_restore_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_cloud_backup),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Backup & Restore",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Google Cloud Sync & Offline Storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // OPTION 1: Cloud Backup (Gmail)
                Text(
                    text = "OPTION 1: CLOUD BACKUP (GMAIL)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (connectedGmail != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (!displayName.isNullOrBlank()) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = connectedGmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = connectedGmail,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (isSyncing) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 1.8.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Syncing...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Auto-sync active • Last cloud sync: $formattedLastBackup",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedButton(
                                onClick = onSignOutGoogle,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sign_out_google_button")
                            ) {
                                Text("Sign Out", fontSize = 13.sp)
                            }
                        } else {
                            Text(
                                text = "Sign in with Google to automatically back up and restore your rules and limits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = onSignInGoogle,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_google_signin_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = "Google",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Sign in with Google", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // OPTION 2: Offline Backup (.json file)
                Text(
                    text = "OPTION 2: OFFLINE BACKUP (.JSON)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Export your rules and settings to a JSON file, or restore from a previously exported backup file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onExportJson,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_backup_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Export .json",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onImportJson,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_backup_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Import .json",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Note: Active screen-time usage and cooldown timers are preserved during restore.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("close_backup_dialog_button")
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

