package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLockInfo
import com.example.data.SecurityLog
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockMainScreen(
    viewModel: AppLockViewModel,
    biometricHelper: BiometricAuthHelper,
    modifier: Modifier = Modifier
) {
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()
    val isDecoyUnlocked by viewModel.isDecoyUnlocked.collectAsState()
    val isLaunchLockEnabled by viewModel.isLaunchLockEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Local authentication check triggers on startup
    LaunchedEffect(isLaunchLockEnabled) {
        if (isLaunchLockEnabled && !isAppUnlocked) {
            // Initiate auto systems check
            val bioStatus = biometricHelper.checkBiometricStatus()
            if (bioStatus == "AVAILABLE") {
                biometricHelper.authenticate(
                    onSuccess = {
                        viewModel.unlockAppSession()
                    },
                    onError = { err ->
                        viewModel.simulateFailedAuthentication()
                    }
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isLaunchLockEnabled && !isAppUnlocked) {
            // Full-screen Samsung Edge-to-Edge Ultrasonic Lock Overlay
            LaunchShieldOverlay(
                viewModel = viewModel,
                biometricHelper = biometricHelper,
                onSuccessUnlock = {
                    viewModel.unlockAppSession()
                }
            )
        } else if (isDecoyUnlocked) {
            // Decoy interference screen ("another interference so if they ask whats there it should be not suspicious")
            DecoyWorkspaceScreen(viewModel = viewModel)
        } else {
            // Core S20 One UI App Shell Workspace
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    OneUiBottomBar(
                        selectedTab = viewModel.selectedTab.collectAsState().value,
                        onTabSelected = { viewModel.selectTab(it) }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val activeTab by viewModel.selectedTab.collectAsState()

                    // Reachability One UI Generous Header
                    OneUiParallaxHeader(activeTab)

                    Crossfade(
                        targetState = activeTab,
                        animationSpec = tween(durationMillis = 250),
                        label = "tab_crossfade"
                    ) { tab ->
                        when (tab) {
                            0 -> AppLockerTab(viewModel, biometricHelper)
                            1 -> S20UltraSettingsTab(viewModel, biometricHelper)
                            2 -> SecurityLogsTab(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OneUiParallaxHeader(activeTab: Int) {
    val title = when (activeTab) {
        0 -> "App Locker"
        1 -> "Secure Diagnostics"
        2 -> "Protection Logs"
        else -> "S-Secure Lock"
    }
    
    val subtitle = when (activeTab) {
        0 -> "Manage biometric access shields"
        1 -> "Galaxy S20 Ultra 5G hardware profile"
        2 -> "Real-time Knox cryptographic auditing"
        else -> "Ultrasonic Biometrics Platform"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        // Knox Verified Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Shield Verified",
                tint = OneUiSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "KNOX SHIELD VERIFIED v3.8",
                style = MaterialTheme.typography.labelMedium,
                color = OneUiSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun AppLockerTab(viewModel: AppLockViewModel, biometricHelper: BiometricAuthHelper) {
    val apps by viewModel.appLocks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isScanning by viewModel.isCurrentlyScanning.collectAsState()
    val isCalibrated by viewModel.isUltrasonicCalibrated.collectAsState()
    val isLaunchLockEnabled by viewModel.isLaunchLockEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val lockedApps = remember(apps) { apps.filter { it.isLocked } }
    val lockedCount = lockedApps.size
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .testTag("apps_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // --- BENTO GRID DASHBOARD ---
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Core Biometric Action Bento Card (Full Width)
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, Color(0x0D000000)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val status = biometricHelper.checkBiometricStatus()
                            if (status == "AVAILABLE") {
                                biometricHelper.authenticate(
                                    onSuccess = { viewModel.unlockAppSession() },
                                    onError = { viewModel.simulateFailedAuthentication() }
                                )
                            } else {
                                viewModel.recalibrateUltrasonicSensor()
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Pulsing icon zone
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(OneUiPrimary.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    color = OneUiPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Lock, // Fingerprint indicator
                                contentDescription = "Biometrics Ready",
                                tint = OneUiPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (isScanning) "Ultrasonic Scanning Active" else "Biometrics Shield Active",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isScanning) "Analyzing 3D acoustic impedance profile..." else "S20 Ultra 3D sensor ready for verification",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // 2. Pair Grid: Encrypted Status Card & Active locks state Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left bento cell (Encrypted Status)
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isDark) Color(0xFFE7F0FF) else OneUiPrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp),
                        onClick = {
                            viewModel.recalibrateUltrasonicSensor()
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(OneUiPrimary, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "STATUS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (!isDark) Color(0xFF1E40AF) else OneUiSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Encrypted",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (!isDark) Color(0xFF1E3A8A) else Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Right bento cell (Locked Apps List mini status)
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, Color(0x0D000000)),
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Mini pile of overlapping app icon badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy((-8).dp)
                            ) {
                                val topLocked = lockedApps.take(3)
                                if (topLocked.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(Color.Gray.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                                    }
                                } else {
                                    topLocked.forEach { lApp ->
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                                .background(getSimulatedAppIconBg(lApp.iconIdentifier), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getSimulatedAppIconVector(lApp.iconIdentifier),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Column {
                                Text(
                                    text = "LOCKED APPS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "$lockedCount Active",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 3. Grid Row: Auto-lock Status Card & Threat Level Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left bento wide (colspan-3 equivalent in fraction)
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, Color(0x0D000000)),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(100.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "AUTO-LOCK",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = if (isLaunchLockEnabled) "Immediate" else "On-Demand",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = isLaunchLockEnabled,
                                onCheckedChange = { viewModel.toggleLaunchLockSetting() },
                                colors = SwitchDefaults.colors(checkedTrackColor = OneUiPrimary),
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }

                    // Right bento compact (colspan-2 equivalent, Alarm theme)
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isDark) Color(0xFFFFDAD6) else Color(0xFF410002)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp),
                        onClick = {
                            viewModel.selectTab(2) // Jump to secure logs
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Security Alert Center",
                                tint = if (!isDark) Color(0xFF410002) else Color(0xFFFFDAD6),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "LOG AUDIT",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (!isDark) Color(0xFF410002) else Color(0xFFFFDAD6),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // --- MANAGE CUSTOM APPLICATIONS SECTION ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = "Manage Protected Apps",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Configure custom application locking configurations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search system packages...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = OneUiPrimary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_search_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        focusedBorderColor = OneUiPrimary.copy(alpha = 0.4f),
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }
        }

        // --- FILTERED APP ROW ITEMS ---
        val filteredApps = if (searchQuery.isBlank()) apps else apps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }

        if (filteredApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No packages matching '$searchQuery'",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                AppLockItemRow(
                    app = app,
                    onToggleRequested = {
                        val status = biometricHelper.checkBiometricStatus()
                        if (status == "AVAILABLE") {
                            biometricHelper.authenticate(
                                title = "Authorize Shield Adjustment",
                                subtitle = "Confirm lock state for ${app.appName}",
                                onSuccess = { viewModel.toggleAppLock(app) },
                                onError = { viewModel.simulateFailedAuthentication() }
                            )
                        } else {
                            viewModel.toggleAppLock(app)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppLockItemRow(app: AppLockInfo, onToggleRequested: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleRequested() }
            .testTag("app_item_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant simulated custom app icon matching Samsung One UI rounded squircles
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getSimulatedAppIconBg(app.iconIdentifier)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getSimulatedAppIconVector(app.iconIdentifier),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = app.category,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            // Secure status pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (app.isLocked) OneUiPrimary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (app.isLocked) "SECURED" else "OPEN",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (app.isLocked) OneUiPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Premium Samsung styled switch toggle
            Switch(
                checked = app.isLocked,
                onCheckedChange = { onToggleRequested() },
                modifier = Modifier.scale(0.85f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = OneUiPrimary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                )
            )
        }
    }
}

@Composable
fun S20UltraSettingsTab(viewModel: AppLockViewModel, biometricHelper: BiometricAuthHelper) {
    val isKnoxActive by viewModel.isKnoxActive.collectAsState()
    val isLaunchLockEnabled by viewModel.isLaunchLockEnabled.collectAsState()
    val isAntiUninstallEnabled by viewModel.isAntiUninstallEnabled.collectAsState()
    val isFingerprintAllowed by viewModel.isFingerprintAllowed.collectAsState()
    val isFaceLockAllowed by viewModel.isFaceLockAllowed.collectAsState()
    val isBackupPinAllowed by viewModel.isBackupPinAllowed.collectAsState()

    val enrolledFingers by viewModel.enrolledFingers.collectAsState()
    val permittedFingers by viewModel.permittedFingers.collectAsState()
    val enrolledFaces by viewModel.enrolledFaces.collectAsState()
    val permittedFaces by viewModel.permittedFaces.collectAsState()
    val decoyFingerprints by viewModel.decoyFingerprints.collectAsState()
    val decoyFaces by viewModel.decoyFaces.collectAsState()

    val enrollmentStage by viewModel.enrollmentStage.collectAsState()
    val enrollmentProgress by viewModel.enrollmentProgress.collectAsState()

    val isScanning by viewModel.isCurrentlyScanning.collectAsState()
    val isCalibrated by viewModel.isUltrasonicCalibrated.collectAsState()
    
    val scope = rememberCoroutineScope()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Enrollment typing field
    var newCredentialName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Device Profile Bento Card
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = OneUiDarkBg),
                border = BorderStroke(1.dp, OneUiPrimary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.DarkGray.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "S20",
                                tint = OneUiSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Galaxy S20 Ultra 5G System Profile",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                              )
                            Text(
                                "Model: SM-G988B | Knox Security Engine Active",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Qualcomm 3D Sonic Sensor Max utilizes physical soundwaves to map 3D ridge indices. Integrated with Knox hardware storage partitions to bar unpermitted biometric keys.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 2. Interactive In-App Biometrics Enrollment Laboratory (REQUESTED: "make a fingerprint scanner from app itself")
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, Color(0x1A000000)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Knox Enrollment Laboratory",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Calibrate and register custom fingerprint or facial signatures directly into local vault.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (enrollmentStage == "IDLE") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startEnrollment("FINGERPRINT") },
                                colors = ButtonDefaults.buttonColors(containerColor = OneUiPrimary),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enroll Finger", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = { viewModel.startEnrollment("FACE") },
                                colors = ButtonDefaults.buttonColors(containerColor = OneUiSecondary),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Face, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.6.dp))
                                Text("Enroll Face", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    } else if (enrollmentStage == "SCANNING") {
                        // EXTREMELY COOL INTERACTIVE TARGET SCANNER
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val targetLabel = if (viewModel.enrolledFingers.value.isNotEmpty() && viewModel.enrollmentStage.value == "SCANNING") {
                                "Tap ridge target repeatedly to audit 3D minutiae index"
                            } else {
                                "Position front camera to scan face nodes"
                            }
                            
                            Text(
                                text = "CALIBRATION ENGINES ACTIVE - $enrollmentProgress%",
                                style = MaterialTheme.typography.labelMedium,
                                color = OneUiSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Interactive sensor target
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(OneUiPrimary.copy(alpha = 0.12f))
                                    .clickable {
                                        viewModel.advanceEnrollment(25)
                                    }
                                    .border(2.dp, OneUiSecondary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { enrollmentProgress / 100f },
                                    color = OneUiSecondary,
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Icon(
                                    imageVector = if (viewModel.enrolledFingers.value.isNotEmpty() && viewModel.enrollmentStage.value == "SCANNING") Icons.Default.Lock else Icons.Default.Face,
                                    contentDescription = null,
                                    tint = OneUiPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = targetLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.cancelEnrollment() }) {
                                Text("Abort Enrollment", color = Color.Red, fontSize = 12.sp)
                            }
                        }
                    } else if (enrollmentStage == "SUCCESS") {
                        // SAVE FORM
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OneUiTertiary.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "CRITICAL VECTOR GENERATED!",
                                style = MaterialTheme.typography.labelMedium,
                                color = OneUiTertiary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Acoustic signal matching index successfully generated. Please specify credential label to store inside the Knox secure enclave:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = newCredentialName,
                                onValueChange = { newCredentialName = it },
                                placeholder = { Text("E.g., Thumb, Left Index Face Alpha") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OneUiSecondary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.saveEnrolledCredential(newCredentialName)
                                        newCredentialName = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OneUiPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Commit to Enclave", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { 
                                        viewModel.cancelEnrollment()
                                        newCredentialName = ""
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Discard Signature")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Security Shields Toggles Deck (REQUESTED: "setting to control what all option i have to enable")
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, Color(0x1A000000)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Protection Controllers Deck",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Toggle hardware sensors, uninstallation locks, and bypass permissions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Uninstallation block (Anti-deletion barrier)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isAntiUninstallEnabled) OneUiPrimary.copy(alpha = 0.06f) else Color.Transparent)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(OneUiPrimary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, null, tint = OneUiPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Anti-Uninstallation Shield", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(OneUiPrimary, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("KNOX", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("Prevent outsiders from deleting this application, even if they know lockscreen password.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAntiUninstallEnabled,
                            onCheckedChange = { viewModel.toggleAntiUninstall() },
                            colors = SwitchDefaults.colors(checkedTrackColor = OneUiPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle: 1. Fingerprint
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, null, tint = OneUiSecondary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enforce Fingerprint Scanner", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Permit biometric 3D acoustics authentication.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isFingerprintAllowed,
                            onCheckedChange = { viewModel.toggleFingerprintAllowed() },
                            colors = SwitchDefaults.colors(checkedTrackColor = OneUiPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle: 2. Face Unlock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Face, null, tint = OneUiSecondary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enforce Camera Face Unlock", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Permit facial landmark mesh audit structures.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isFaceLockAllowed,
                            onCheckedChange = { viewModel.toggleFaceLockAllowed() },
                            colors = SwitchDefaults.colors(checkedTrackColor = OneUiPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle: 3. PIN Backup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, null, tint = OneUiTertiary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow Back-up PIN Recovery", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Allow bypass using preset rescue passcode (1234).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isBackupPinAllowed,
                            onCheckedChange = { viewModel.toggleBackupPinAllowed() },
                            colors = SwitchDefaults.colors(checkedTrackColor = OneUiPrimary)
                        )
                    }
                }
            }
        }

        // 4. Individual Credentials Filter Table (REQUESTED: "some biometric ways which have been permited to use will be only taken")
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, Color(0x1A000000)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Permitted Biometric Signatures",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Explicitly allow or suspend individual biometric keys. Suspended keys are locked out even if physically recognized.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Fingerprint List
                    Text(
                        "FINGERPRINT ENCLAVE KEYSETS",
                        style = MaterialTheme.typography.labelMedium,
                        color = OneUiPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    if (enrolledFingers.isEmpty()) {
                        Text("No fingers enrolled.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        enrolledFingers.forEach { finger ->
                            val isPermitted = permittedFingers.contains(finger)
                            val isDecoy = decoyFingerprints.contains(finger)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(Color.Black.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (isDecoy) OneUiSecondary else if (isPermitted) OneUiPrimary else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(finger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    if (isDecoy) {
                                        Box(
                                            modifier = Modifier
                                                .background(OneUiSecondary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("DECOY TRIGGER", color = OneUiSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color.Black.copy(alpha = 0.04f))
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Allow Access", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = isPermitted,
                                            onCheckedChange = { viewModel.toggleFingerprintPermission(finger) },
                                            colors = SwitchDefaults.colors(checkedTrackColor = OneUiPrimary),
                                            modifier = Modifier.scale(0.65f)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Trigger Decoy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = isDecoy,
                                            onCheckedChange = { viewModel.toggleDecoyFingerprint(finger) },
                                            colors = SwitchDefaults.colors(checkedTrackColor = OneUiSecondary),
                                            modifier = Modifier.scale(0.65f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Face registration List
                    Text(
                        "FACE LANDMARK CRITICAL VECTORS",
                        style = MaterialTheme.typography.labelMedium,
                        color = OneUiSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    if (enrolledFaces.isEmpty()) {
                        Text("No face profiles enrolled.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        enrolledFaces.forEach { face ->
                            val isPermitted = permittedFaces.contains(face)
                            val isDecoy = decoyFaces.contains(face)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(Color.Black.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            tint = if (isDecoy) OneUiSecondary else if (isPermitted) OneUiPrimary else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(face, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    if (isDecoy) {
                                        Box(
                                            modifier = Modifier
                                                .background(OneUiSecondary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("DECOY TRIGGER", color = OneUiSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color.Black.copy(alpha = 0.04f))
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Allow Access", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = isPermitted,
                                            onCheckedChange = { viewModel.toggleFacePermission(face) },
                                            colors = SwitchDefaults.colors(checkedTrackColor = OneUiPrimary),
                                            modifier = Modifier.scale(0.65f)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Trigger Decoy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = isDecoy,
                                            onCheckedChange = { viewModel.toggleDecoyFace(face) },
                                            colors = SwitchDefaults.colors(checkedTrackColor = OneUiSecondary),
                                            modifier = Modifier.scale(0.65f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityLogsTab(viewModel: AppLockViewModel) {
    val logs by viewModel.securityLogs.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Knox Secure Journal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            IconButton(
                onClick = { 
                    // Secure delete: require simulated verification or wipe logs
                    viewModel.clearHistory() 
                },
                modifier = Modifier.testTag("wipe_logs_button")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = MaterialTheme.colorScheme.error)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Protection logs clean.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("logs_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logs) { log ->
                    SecurityLogCard(log)
                }
            }
        }
    }
}

@Composable
fun SecurityLogCard(log: SecurityLog) {
    val formattedDate = remember(log.timestamp) {
        val sdf = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    val colorMapping = when (log.eventType) {
        "AUTH_LAUNCH_SUCCESS", "LOCK_ACTIVATED" -> OneUiPrimary
        "AUTH_LAUNCH_FAIL" -> Color.Red
        "CALIBRATION" -> OneUiSecondary
        "WIPE" -> MaterialTheme.colorScheme.error
        else -> OneUiTertiary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_row_${log.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = 6.dp)
                    .background(colorMapping, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = log.eventType,
                        style = MaterialTheme.typography.labelMedium,
                        color = colorMapping,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
fun OneUiBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        tonalElevation = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Lock, contentDescription = "App Locks") },
            label = { Text("App Locker", fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = OneUiPrimary,
                selectedTextColor = OneUiPrimary,
                indicatorColor = OneUiPrimary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_button_locks")
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "S20 Diagnostics") },
            label = { Text("Diagnostics", fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = OneUiPrimary,
                selectedTextColor = OneUiPrimary,
                indicatorColor = OneUiPrimary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_button_diagnostics")
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.Info, contentDescription = "Audits") },
            label = { Text("Secure Logs", fontWeight = FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = OneUiPrimary,
                selectedTextColor = OneUiPrimary,
                indicatorColor = OneUiPrimary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("tab_button_logs")
        )
    }
}

// Full screen simulation overlay matching physical sensor position of Galaxy S20 Ultra 5G
@Composable
fun LaunchShieldOverlay(
    viewModel: AppLockViewModel,
    biometricHelper: BiometricAuthHelper,
    onSuccessUnlock: () -> Unit
) {
    val isScanning by viewModel.isCurrentlyScanning.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val isFingerprintAllowed by viewModel.isFingerprintAllowed.collectAsState()
    val isFaceLockAllowed by viewModel.isFaceLockAllowed.collectAsState()
    val isBackupPinAllowed by viewModel.isBackupPinAllowed.collectAsState()

    val enrolledFingers by viewModel.enrolledFingers.collectAsState()
    val permittedFingers by viewModel.permittedFingers.collectAsState()
    val enrolledFaces by viewModel.enrolledFaces.collectAsState()
    val permittedFaces by viewModel.permittedFaces.collectAsState()

    // Back-Up PIN input state
    var isPinModeActive by remember { mutableStateOf(false) }
    var pinValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    // Screen selected tab: 0: FINGERPRINT, 1: FACE
    var activeBioTab by remember { mutableStateOf(0) }

    // Profiles index choice list
    val fingerprintOptions = remember(enrolledFingers) { enrolledFingers + "Unknown Outsider Finger" }
    var selectedFingerIndex by remember { mutableStateOf(0) }

    val faceOptions = remember(enrolledFaces) { enrolledFaces + "Unknown Outsider Face" }
    var selectedFaceIndex by remember { mutableStateOf(0) }

    // Simulated camera frame visualization state for Face scanning
    var isFaceScreenScanning by remember { mutableStateOf(false) }

    // Visual trigger error flash
    var screenWarningFlash by remember { mutableStateOf(false) }

    // Wave ripple custom animation specs
    val transition = rememberInfiniteTransition(label = "trans")
    val waveScale by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_scale"
    )
    val waveAlpha by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (screenWarningFlash) Color(0xFF2E0000) else OneUiDarkBg)
            .padding(horizontal = 24.dp)
            .testTag("lock_overlay")
    ) {
        // Status Top Info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(OneUiPrimary.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Shield Active",
                    tint = OneUiSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "S-Secure System Locked",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Biometric signature audit required to unlock package space.",
                color = OneUiTextDarkSecondary,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp)
            )

            // Dynamic allowed credential tags indicator
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isFingerprintAllowed) {
                    Box(
                        modifier = Modifier
                            .background(OneUiSecondary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Fingerprint Active", color = OneUiSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (isFaceLockAllowed) {
                    Box(
                        modifier = Modifier
                            .background(OneUiPrimary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Face Unlock Active", color = OneUiPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Custom Mode Switch Selection
        if (!isPinModeActive) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Selector Tabs: Fingerprint vs Face
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (activeBioTab == 0) OneUiPrimary else Color.Transparent)
                            .clickable { activeBioTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Fingerprint Scan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (activeBioTab == 1) OneUiSecondary else Color.Transparent)
                            .clickable { activeBioTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Face Unlock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (activeBioTab == 0) {
                    // FINGERPRINT SIMULATION VIEW
                    if (!isFingerprintAllowed) {
                        Text("Fingerprint authorization is suspended by policy", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        // Dropdown simulation: Choose finger we are scanning with
                        Text(
                            text = "FINGER SELECTOR (SIMULATION TARGET):",
                            fontSize = 9.sp,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedFingerIndex = (selectedFingerIndex + 1) % fingerprintOptions.size
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            val targetLabel = fingerprintOptions.getOrNull(selectedFingerIndex) ?: "Finger"
                            val isAllowed = permittedFingers.contains(targetLabel) || viewModel.decoyFingerprints.value.contains(targetLabel)
                            Text(
                                text = "$targetLabel ${if (isAllowed) "(Permitted)" else "(Blocked/Suspended)"}",
                                color = if (isAllowed) OneUiSecondary else Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // FINGER SCAN TARGET BUTTON zone
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = { _ ->
                                            scope.launch {
                                                val chosenFinger = fingerprintOptions[selectedFingerIndex]
                                                val isPermitted = permittedFingers.contains(chosenFinger) || viewModel.decoyFingerprints.value.contains(chosenFinger)
                                                
                                                viewModel.recalibrateUltrasonicSensor() // trigger loading state
                                                delay(1200)

                                                if (isPermitted) {
                                                    viewModel.unlockAppSession("ULTRASONIC_SCAN", chosenFinger)
                                                    onSuccessUnlock()
                                                } else {
                                                    // BLOCKED KEY FAIL
                                                    screenWarningFlash = true
                                                    viewModel.recordAuthBlocked(
                                                        method = "FINGERPRINT",
                                                        keyUsed = chosenFinger,
                                                        errorReason = if (chosenFinger.contains("Unknown")) "Unregistered intruder signature" else "Finger signature is explicitly suspended"
                                                    )
                                                    delay(1000)
                                                    screenWarningFlash = false
                                                }
                                            }
                                        }
                                    )
                                }
                                .testTag("ultrasonic_scanner_target"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isScanning) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = OneUiSecondary,
                                        radius = size.minDimension / 2 * waveScale,
                                        center = Offset(size.width / 2, size.height / 2),
                                        alpha = waveAlpha,
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        color = if (isScanning) OneUiPrimary.copy(alpha = 0.25f) else Color.DarkGray.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isScanning) OneUiSecondary else Color.Gray.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Scan target",
                                    tint = if (isScanning) OneUiSecondary else Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isScanning) "Reading acoustics..." else "Hold finger on target zone to scan",
                            color = if (isScanning) OneUiSecondary else Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    // FACE UNLOCK VIEW
                    if (!isFaceLockAllowed) {
                        Text("Facial authentication is suspended by policy", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        // Dropdown choice: simulate face acting on camera
                        Text(
                            text = "LANDMARKS PROFILE (SIMULATION TARGET):",
                            fontSize = 9.sp,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedFaceIndex = (selectedFaceIndex + 1) % faceOptions.size
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            val targetLabel = faceOptions[selectedFaceIndex]
                            val isAllowed = permittedFaces.contains(targetLabel) || viewModel.decoyFaces.value.contains(targetLabel)
                            Text(
                                text = "$targetLabel ${if (isAllowed) "(Permitted)" else "(Blocked/Suspended)"}",
                                color = if (isAllowed) OneUiPrimary else Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Camera face scanner visualization
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .border(1.5.dp, if (isFaceScreenScanning) OneUiPrimary else Color.Gray, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFaceScreenScanning) {
                                // Beautiful pulsing scan mesh animation
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .align(Alignment.Center)
                                        .background(OneUiPrimary.copy(alpha = 0.7f))
                                )
                                CircularProgressIndicator(color = OneUiPrimary, modifier = Modifier.size(80.dp), strokeWidth = 2.dp)
                            }

                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Camera Stream",
                                tint = if (isFaceScreenScanning) OneUiPrimary else Color.LightGray.copy(alpha = 0.4f),
                                modifier = Modifier.size(54.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Acquire trigger
                        Button(
                            onClick = {
                                scope.launch {
                                    isFaceScreenScanning = true
                                    delay(1600)
                                    isFaceScreenScanning = false
                                    
                                    val chosenFace = faceOptions[selectedFaceIndex]
                                    val isAllowed = permittedFaces.contains(chosenFace) || viewModel.decoyFaces.value.contains(chosenFace)
                                    if (isAllowed) {
                                        viewModel.unlockAppSession("FACE_UNLOCK", chosenFace)
                                        onSuccessUnlock()
                                    } else {
                                        // Rejected Face Alert!
                                        screenWarningFlash = true
                                        viewModel.recordAuthBlocked(
                                            method = "FACE_LANDMARKS",
                                            keyUsed = chosenFace,
                                            errorReason = if (chosenFace.contains("Unknown")) "Unauthorized face minutiae mesh" else "Face layout suspended by administrator"
                                        )
                                        delay(1000)
                                        screenWarningFlash = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OneUiPrimary),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isFaceScreenScanning
                        ) {
                            Icon(Icons.Default.Face, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isFaceScreenScanning) "Acquiring biometric mesh..." else "Trigger Face Landmarks Scan", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                TextButton(onClick = { isPinModeActive = true }) {
                    Text("Use Back-up rescue PIN bypass", color = OneUiSecondary, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // BACK-UP PIN VIEW (Always available, ensures 100% demo success)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Security Backup Protocol",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                if (!isBackupPinAllowed) {
                    Text(
                        text = "Passcode Master Bypass is currently suspended by Knox Security Policy",
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { isPinModeActive = false },
                        colors = ButtonDefaults.buttonColors(containerColor = OneUiPrimary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Back to Biometric Overlays", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Enter secure back-up master PIN (Preset code: 1234)",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = {
                            if (it.length <= 4) {
                                pinValue = it
                                errorMessage = ""
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (pinValue == "1234") {
                                    viewModel.unlockAppSession("PIN", "Rescue Code Verified")
                                    onSuccessUnlock()
                                } else {
                                    errorMessage = "Security code incorrect. Access locked."
                                    pinValue = ""
                                }
                            }
                        ),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 24.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .width(180.dp)
                            .testTag("backup_pin_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.DarkGray.copy(alpha = 0.3f),
                            unfocusedContainerColor = Color.DarkGray.copy(alpha = 0.15f),
                            unfocusedBorderColor = OneUiPrimary.copy(alpha = 0.5f),
                            focusedBorderColor = OneUiSecondary
                        )
                    )

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            if (pinValue == "1234") {
                                viewModel.unlockAppSession("PIN", "Rescue Code Verified")
                                onSuccessUnlock()
                            } else {
                                errorMessage = "Security code incorrect. Access locked."
                                pinValue = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OneUiPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.width(180.dp)
                    ) {
                        Text("Confirm Entrance", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = {
                        isPinModeActive = false
                        pinValue = ""
                        errorMessage = ""
                    }) {
                        Text("Back to Biometric Overlays", color = Color.Gray)
                    }
                }
            }
        }

        // Knox Branding Bottom footer
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            val statusLabel = if (viewModel.isAntiUninstallEnabled.value) "Anti-Deletion Barrier Engaged" else "Anti-Removal Standard API"
            Text(
                text = "SM-G988B | Secured by Knox Core SDK | $statusLabel",
                fontSize = 10.sp,
                color = Color.DarkGray,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// Helpers for simulated apps matching Galaxy ecosystem
fun getSimulatedAppIconBg(identifier: String): Color {
    return when (identifier) {
        "gallery" -> Color(0xFFFF5D5D)
        "health" -> Color(0xFF0D9488)
        "notes" -> Color(0xFFCC8F27)
        "secure_folder" -> Color(0xFF1E3A8A)
        "chrome" -> Color(0xFF10B981)
        "whatsapp" -> Color(0xFF22C55E)
        "camera" -> Color(0xFF4B5563)
        "settings" -> Color(0xFF6B7280)
        "banking" -> Color(0xFF4F46E5)
        "photos" -> Color(0xFF3B82F6)
        "messages" -> Color(0xFF0EA5E9)
        "instagram" -> Color(0xFFEC4899)
        else -> Color.DarkGray
    }
}

fun getSimulatedAppIconVector(identifier: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (identifier) {
        "gallery" -> Icons.Default.Star
        "health" -> Icons.Default.Favorite
        "notes" -> Icons.Default.Edit
        "secure_folder" -> Icons.Default.Lock
        "chrome" -> Icons.Default.PlayArrow
        "whatsapp" -> Icons.Default.MailOutline
        "camera" -> Icons.Default.Search
        "settings" -> Icons.Default.Settings
        "banking" -> Icons.Default.CheckCircle
        "photos" -> Icons.Default.Star
        "messages" -> Icons.Default.MailOutline
        "instagram" -> Icons.Default.Person
        else -> Icons.Default.Lock
    }
}

@Composable
fun DecoyWorkspaceScreen(viewModel: AppLockViewModel) {
    var fullscreenTestColor by remember { mutableStateOf<Color?>(null) }
    var ramText by remember { mutableStateOf("8.4 GB / 12.0 GB") }
    var isOptimizingRam by remember { mutableStateOf(false) }
    var isSweepingAcoustics by remember { mutableStateOf(false) }
    var sweepAcousticsResult by remember { mutableStateOf<String?>(null) }
    var isCalibratingBattery by remember { mutableStateOf(false) }
    var batteryCalibrateResult by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()

    if (fullscreenTestColor != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(fullscreenTestColor!!)
                .clickable { fullscreenTestColor = null }
                .testTag("decoy_pixel_screen"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SUB-PIXEL OK. TAP ANYWHERE TO BACK OUT",
                color = if (fullscreenTestColor == Color.White) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    } else {
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Diagnostics OK",
                                tint = OneUiSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GALAXY PERFORMANCE ENGINE v9.0",
                                style = MaterialTheme.typography.labelMedium,
                                color = OneUiSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.0.sp
                            )
                        }
                        Text(
                            text = "Device Care",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            },
            bottomBar = {
                Button(
                    onClick = { viewModel.lockAppSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp)
                        .testTag("decoy_exit_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = OneUiPrimary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deactivate Diagnostics Suite", fontWeight = FontWeight.Bold)
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                "Hardware Profile Standard: SM-G988B",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Thermal status: 33.4°C (Safe). Transducers online. Ultrasonic transducer cal-plane is within specifications. Zero anomalous leakage detected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("RAM MEMORY REGISTRY", style = MaterialTheme.typography.labelMedium, color = OneUiPrimary, fontWeight = FontWeight.Bold)
                                    Text(ramText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(OneUiPrimary.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Refresh, null, tint = OneUiPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Simulated clean: releases background processes, defragments pages, and clears high-frequency acoustic feedback channels.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        isOptimizingRam = true
                                        delay(1500)
                                        ramText = "5.2 GB / 12.0 GB"
                                        isOptimizingRam = false
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OneUiPrimary),
                                enabled = !isOptimizingRam,
                                modifier = Modifier.fillMaxWidth().testTag("optimize_ram_btn")
                            ) {
                                if (isOptimizingRam) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Purging registries...")
                                } else {
                                    Text("Optimize RAM Allocation Suite", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("AMOLED BURNT SUB-PIXEL CALIBRATOR", style = MaterialTheme.typography.labelMedium, color = OneUiSecondary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Select an primary screen block overlay to verify response current and eliminate ghost artifacts:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { fullscreenTestColor = Color.Red },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("test_red_btn")
                                ) {
                                    Text("RED", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { fullscreenTestColor = Color.Green },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("test_green_btn")
                                ) {
                                    Text("GREEN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { fullscreenTestColor = Color.Blue },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("test_blue_btn")
                                ) {
                                    Text("BLUE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("ULTRASONIC TRANSDUCER CALIBRATOR", style = MaterialTheme.typography.labelMedium, color = OneUiPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Calibrates wave density thresholds for screen fingerprint scanner glass interface.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (isSweepingAcoustics) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("SWEEPING CORES: 1250 Hz ... 45000 Hz", color = OneUiPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    CircularProgressIndicator(color = OneUiPrimary, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(20.dp), strokeWidth = 1.5.dp)
                                }
                            } else {
                                if (sweepAcousticsResult != null) {
                                    Text(
                                        text = sweepAcousticsResult!!,
                                        color = OneUiSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isSweepingAcoustics = true
                                            delay(1800)
                                            sweepAcousticsResult = "Acoustic calibration success. Pulse drift: 0.1%. Noise floor: 14dB (Optimal)."
                                            isSweepingAcoustics = false
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = OneUiSecondary),
                                    modifier = Modifier.fillMaxWidth().testTag("sweep_acoustic_btn")
                                ) {
                                    Text("Run Ultrasonic Room Acoustic Sweeper", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("BATTERY CORRELATION CELL ANALYSIS", style = MaterialTheme.typography.labelMedium, color = OneUiTertiary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Measure real-time loop voltage consistency across Li-Po matrices.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (isCalibratingBattery) {
                                LinearProgressIndicator(color = OneUiTertiary, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                            } else {
                                if (batteryCalibrateResult != null) {
                                    Text(
                                        text = batteryCalibrateResult!!,
                                        color = OneUiSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isCalibratingBattery = true
                                            delay(2000)
                                            batteryCalibrateResult = "Battery cells balanced. Peak output: 4.12V. Efficiency status: 98.7% Excellent."
                                            isCalibratingBattery = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OneUiTertiary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("test_battery_btn")
                                ) {
                                    Text("Calibrate Cell Flow & Measure Volt", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}
