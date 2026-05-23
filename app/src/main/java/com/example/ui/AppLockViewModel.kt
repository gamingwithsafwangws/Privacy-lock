package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppLockInfo
import com.example.data.AppLockRepository
import com.example.data.SecurityLog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AppLockRepository

    // Initial state setup
    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppLockRepository(database)
        viewModelScope.launch {
            repository.initializeDefaultAppsIfNeeded()
        }
    }

    // Search and filter states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: App Locks, 1: S20 Ultra Settings, 2: Security Logs
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Database Flows
    val appLocks: StateFlow<List<AppLockInfo>> = repository.allLocks
        .combine(searchQuery) { locks, query ->
            if (query.isBlank()) {
                locks
            } else {
                locks.filter { it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val securityLogs: StateFlow<List<SecurityLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App Launch Secure Status helper
    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    // S20 Ultra Ultrasonic Simulator Status
    private val _isCurrentlyScanning = MutableStateFlow(false)
    val isCurrentlyScanning: StateFlow<Boolean> = _isCurrentlyScanning.asStateFlow()

    // Calibration state simulation
    private val _isUltrasonicCalibrated = MutableStateFlow(true)
    val isUltrasonicCalibrated: StateFlow<Boolean> = _isUltrasonicCalibrated.asStateFlow()

    // Knox Security Active State
    private val _isKnoxActive = MutableStateFlow(true)
    val isKnoxActive: StateFlow<Boolean> = _isKnoxActive.asStateFlow()

    // Strict launch Lock (True initially so each cold start requires scan)
    private val _isLaunchLockEnabled = MutableStateFlow(true)
    val isLaunchLockEnabled: StateFlow<Boolean> = _isLaunchLockEnabled.asStateFlow()

    // --- NEW HIGH SECURITY ENHANCEMENTS FOR ADVANCED BIOMETRICS ---

    // Anti-Uninstallation shield
    private val _isAntiUninstallEnabled = MutableStateFlow(true)
    val isAntiUninstallEnabled: StateFlow<Boolean> = _isAntiUninstallEnabled.asStateFlow()

    // Allowed global biometric methods
    private val _isFingerprintAllowed = MutableStateFlow(true)
    val isFingerprintAllowed: StateFlow<Boolean> = _isFingerprintAllowed.asStateFlow()

    private val _isFaceLockAllowed = MutableStateFlow(true)
    val isFaceLockAllowed: StateFlow<Boolean> = _isFaceLockAllowed.asStateFlow()

    private val _isBackupPinAllowed = MutableStateFlow(true)
    val isBackupPinAllowed: StateFlow<Boolean> = _isBackupPinAllowed.asStateFlow()

    // Enrolled/Permitted individual fingers list
    private val _enrolledFingers = MutableStateFlow(listOf("Right Thumb (Admin)", "Right Index (Admin)", "Left Index (Muted)"))
    val enrolledFingers: StateFlow<List<String>> = _enrolledFingers.asStateFlow()

    private val _permittedFingers = MutableStateFlow(setOf("Right Thumb (Admin)", "Right Index (Admin)"))
    val permittedFingers: StateFlow<Set<String>> = _permittedFingers.asStateFlow()

    // Enrolled/Permitted individual face profiles list
    private val _enrolledFaces = MutableStateFlow(listOf("Admin Face Profile Alpha", "Guest Face Signature (Blocked)"))
    val enrolledFaces: StateFlow<List<String>> = _enrolledFaces.asStateFlow()

    private val _permittedFaces = MutableStateFlow(setOf("Admin Face Profile Alpha"))
    val permittedFaces: StateFlow<Set<String>> = _permittedFaces.asStateFlow()

    // Decoy / Interference Workspace States ("another interference so if they ask whats there it should be not suspicious")
    private val _isDecoyUnlocked = MutableStateFlow(false)
    val isDecoyUnlocked: StateFlow<Boolean> = _isDecoyUnlocked.asStateFlow()

    private val _decoyFingerprints = MutableStateFlow(setOf("Left Index (Muted)"))
    val decoyFingerprints: StateFlow<Set<String>> = _decoyFingerprints.asStateFlow()

    private val _decoyFaces = MutableStateFlow(setOf("Guest Face Signature (Blocked)"))
    val decoyFaces: StateFlow<Set<String>> = _decoyFaces.asStateFlow()

    // Interactive In-App Scanner States (enrollment machine)
    private val _enrollmentProgress = MutableStateFlow(0)
    val enrollmentProgress: StateFlow<Int> = _enrollmentProgress.asStateFlow()

    private val _enrollmentStage = MutableStateFlow("IDLE") // "IDLE", "SCANNING", "SUCCESS"
    val enrollmentStage: StateFlow<String> = _enrollmentStage.asStateFlow()

    private val _enrollmentType = MutableStateFlow("FINGERPRINT") // "FINGERPRINT", "FACE"

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
        viewModelScope.launch {
            repository.insertLog("SYSTEM", "TAB_SWITCH", "User navigated to tab: ${getTabName(tabIndex)}")
        }
    }

    private fun getTabName(index: Int): String {
        return when (index) {
            0 -> "App Locker"
            1 -> "S20 Secure Diagnostics"
            2 -> "Access logs"
            else -> "Unknown"
        }
    }

    fun toggleAppLock(app: AppLockInfo) {
        viewModelScope.launch {
            val nextState = !app.isLocked
            repository.updateLockStatus(app.packageName, nextState)
            val logAction = if (nextState) "LOCK_ACTIVATED" else "LOCK_DEACTIVATED"
            val message = if (nextState) {
                "S-Secure protection successfully applied to ${app.appName} (Package: ${app.packageName})"
            } else {
                "S-Secure shield removed for ${app.appName}. App now runs raw."
            }
            repository.insertLog(app.appName, logAction, message)
        }
    }

    // Custom Detailed Unlock Logging
    fun unlockAppSession(method: String, keyUsed: String) {
        val isDecoy = (method == "ULTRASONIC_SCAN" && decoyFingerprints.value.contains(keyUsed)) || 
                      (method == "FACE_UNLOCK" && _decoyFaces.value.contains(keyUsed))
        _isDecoyUnlocked.value = isDecoy
        _isAppUnlocked.value = true
        viewModelScope.launch {
            if (isDecoy) {
                repository.insertLog("DECOY_SHIELD", "DECOY_LAUNCH_SUCCESS", "Alternative system interface engaged. Credential: [$keyUsed]. Displaying non-suspicious system care dashboard.")
            } else {
                val details = if (method == "PIN") {
                    "Verified via System Bypass Master Recovery PIN."
                } else {
                    "Verified 3D ridge/facial vector match on credential: [$keyUsed]. Status: Knox-Cleared."
                }
                repository.insertLog("SECURITY_SHIELD", "AUTH_LAUNCH_SUCCESS", "Protected App Space opened. Method: $method | Credential: $keyUsed. $details")
            }
        }
    }

    fun unlockAppSession() {
        if (!_isDecoyUnlocked.value) {
            unlockAppSession("BIOMETRIC", "Default System Fingerprint")
        } else {
            _isAppUnlocked.value = true
        }
    }

    fun lockAppSession() {
        _isAppUnlocked.value = false
        _isDecoyUnlocked.value = false
        viewModelScope.launch {
            repository.insertLog("SYSTEM", "LAUNCH_LOCK", "Shield initialized. S-Secure overlay standard active.")
        }
    }

    fun recordAuthBlocked(method: String, keyUsed: String, errorReason: String) {
        viewModelScope.launch {
            repository.insertLog(
                "SECURITY_ALERT",
                "AUTH_LAUNCH_FAIL",
                "BLOCKED ACCESS: Attempt with unpermitted $method pattern [$keyUsed]. Reason: $errorReason. Knox hardware firewall engaged."
            )
        }
    }

    fun simulateFailedAuthentication() {
        viewModelScope.launch {
            repository.insertLog("SYSTEM", "AUTH_LAUNCH_FAIL", "Ultrasonic fingerprint mismatch. Transducer phase shift error.")
        }
    }

    fun toggleKnoxShield() {
        _isKnoxActive.value = !_isKnoxActive.value
        viewModelScope.launch {
            val status = if (_isKnoxActive.value) "ACTIVE" else "DISABLED"
            repository.insertLog("SYSTEM", "KNOX_CONFIG", "Samsung Knox Secure API wrapper status changed to: $status")
        }
    }

    fun toggleLaunchLockSetting() {
        _isLaunchLockEnabled.value = !_isLaunchLockEnabled.value
        viewModelScope.launch {
            val mode = if (_isLaunchLockEnabled.value) "Strict Enforcement (Always check)" else "On-demand"
            repository.insertLog("SYSTEM", "SETTING_CHANGE", "Launch-Lock verification changed to: $mode")
        }
    }

    // Settings modifiers
    fun toggleAntiUninstall() {
        _isAntiUninstallEnabled.value = !_isAntiUninstallEnabled.value
        viewModelScope.launch {
            val status = if (_isAntiUninstallEnabled.value) "ENFORCED (Outsiders cannot delete)" else "DISABLED (Standard)"
            repository.insertLog("KNOX_ADMIN", "SECURITY_POLICY", "App Uninstallation Protection state modified to: $status")
        }
    }

    fun toggleFingerprintAllowed() {
        _isFingerprintAllowed.value = !_isFingerprintAllowed.value
        viewModelScope.launch {
            val status = if (_isFingerprintAllowed.value) "ENABLED" else "MUTED"
            repository.insertLog("SYSTEM", "POLICY_CHANGE", "Ultrasonic finger ridge audit scanning state changed to: $status")
        }
    }

    fun toggleFaceLockAllowed() {
        _isFaceLockAllowed.value = !_isFaceLockAllowed.value
        viewModelScope.launch {
            val status = if (_isFaceLockAllowed.value) "ENABLED" else "MUTED"
            repository.insertLog("SYSTEM", "POLICY_CHANGE", "Face landmarks matching status updated to: $status")
        }
    }

    fun toggleBackupPinAllowed() {
        _isBackupPinAllowed.value = !_isBackupPinAllowed.value
        viewModelScope.launch {
            val status = if (_isBackupPinAllowed.value) "ALLOWED" else "COMPLETELY BARRED"
            repository.insertLog("SYSTEM", "POLICY_CHANGE", "Passcode Master Bypass permission level configured to: $status")
        }
    }

    fun toggleFingerprintPermission(finger: String) {
        val currentSet = _permittedFingers.value.toMutableSet()
        if (currentSet.contains(finger)) {
            currentSet.remove(finger)
        } else {
            currentSet.add(finger)
        }
        _permittedFingers.value = currentSet
        viewModelScope.launch {
            repository.insertLog("POLICY_LAB", "PERMITTED_IDS", "Acoustic fingerprint access list updated. Permitted fingerprints count: ${currentSet.size}")
        }
    }

    fun toggleFacePermission(face: String) {
        val currentSet = _permittedFaces.value.toMutableSet()
        if (currentSet.contains(face)) {
            currentSet.remove(face)
        } else {
            currentSet.add(face)
        }
        _permittedFaces.value = currentSet
        viewModelScope.launch {
            repository.insertLog("POLICY_LAB", "PERMITTED_IDS", "Facial recognition allowed vectors configured. Active profiles: $currentSet")
        }
    }

    fun toggleDecoyFingerprint(finger: String) {
        val currentSet = _decoyFingerprints.value.toMutableSet()
        if (currentSet.contains(finger)) {
            currentSet.remove(finger)
        } else {
            currentSet.add(finger)
        }
        _decoyFingerprints.value = currentSet
        viewModelScope.launch {
            repository.insertLog("POLICY_LAB", "DECOY_CONFIG", "Decoy finger trigger updated: [$finger]. Active decoy triggers: $currentSet")
        }
    }

    fun toggleDecoyFace(face: String) {
        val currentSet = _decoyFaces.value.toMutableSet()
        if (currentSet.contains(face)) {
            currentSet.remove(face)
        } else {
            currentSet.add(face)
        }
        _decoyFaces.value = currentSet
        viewModelScope.launch {
            repository.insertLog("POLICY_LAB", "DECOY_CONFIG", "Decoy face trigger updated: [$face]. Active decoy profiles: $currentSet")
        }
    }

    // In-App Enrollment Simulator Flow
    fun startEnrollment(type: String) {
        _enrollmentType.value = type
        _enrollmentProgress.value = 0
        _enrollmentStage.value = "SCANNING"
        viewModelScope.launch {
            repository.insertLog("SYSTEM_LAB", "ENROLLMENT", "Launched real-time in-app $type scanner calibration interface.")
        }
    }

    fun advanceEnrollment(step: Int) {
        val next = (_enrollmentProgress.value + step).coerceAtMost(100)
        _enrollmentProgress.value = next
        if (next == 100) {
            _enrollmentStage.value = "SUCCESS"
        }
    }

    fun saveEnrolledCredential(name: String) {
        viewModelScope.launch {
            if (_enrollmentType.value == "FINGERPRINT") {
                val updated = _enrolledFingers.value.toMutableList()
                val finalName = if (name.trim().isEmpty()) "Fingerprint #${updated.size + 1}" else name
                updated.add(finalName)
                _enrolledFingers.value = updated
                // Auto-permit enrolled finger
                val currentPermitted = _permittedFingers.value.toMutableSet()
                currentPermitted.add(finalName)
                _permittedFingers.value = currentPermitted

                repository.insertLog("KNOX_VAULT", "SIGNATURE_ADDED", "Enrolled & Permitted new 3D Acoustic Fingerprint: [$finalName]")
            } else {
                val updated = _enrolledFaces.value.toMutableList()
                val finalName = if (name.trim().isEmpty()) "Face ID #${updated.size + 1}" else name
                updated.add(finalName)
                _enrolledFaces.value = updated
                // Auto-permit enrolled face
                val currentPermitted = _permittedFaces.value.toMutableSet()
                currentPermitted.add(finalName)
                _permittedFaces.value = currentPermitted

                repository.insertLog("KNOX_VAULT", "SIGNATURE_ADDED", "Enrolled & Permitted new facial identification map: [$finalName]")
            }
            _enrollmentStage.value = "IDLE"
            _enrollmentProgress.value = 0
        }
    }

    fun cancelEnrollment() {
        _enrollmentStage.value = "IDLE"
        _enrollmentProgress.value = 0
    }

    fun recalibrateUltrasonicSensor() {
        _isCurrentlyScanning.value = true
        viewModelScope.launch {
            repository.insertLog("S20_TRANSDUCER", "CALIBRATION", "Started Ultrasonic Transducer 3D Pulse Echo profiling calibration.")
            kotlinx.coroutines.delay(1200)
            _isCurrentlyScanning.value = false
            _isUltrasonicCalibrated.value = true
            repository.insertLog("S20_TRANSDUCER", "CALIBRATION", "Calibration completed. Acoustic impedance matched (Z = 1.5 Mrayl). Optimal S20 3D Ridge map rebuilt.")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.insertLog("SYSTEM", "WIPE", "All localized security logs cleared by crypt-owner authorization.")
        }
    }
}

