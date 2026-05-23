package com.example.data

import kotlinx.coroutines.flow.Flow

class AppLockRepository(private val database: AppDatabase) {
    private val appLockDao = database.appLockDao()
    private val securityLogDao = database.securityLogDao()

    val allLocks: Flow<List<AppLockInfo>> = appLockDao.getAllLocks()
    val allLogs: Flow<List<SecurityLog>> = securityLogDao.getAllLogs()

    suspend fun initializeDefaultAppsIfNeeded() {
        if (appLockDao.getCount() == 0) {
            val defaults = listOf(
                AppLockInfo("com.sec.android.gallery3d", "Samsung Gallery", true, "Gallery & Photos", "gallery"),
                AppLockInfo("com.sec.android.app.shealth", "Samsung Health", false, "Personal Data", "health"),
                AppLockInfo("com.sec.android.app.notes", "Samsung Notes", true, "Personal Data", "notes"),
                AppLockInfo("com.sec.knox.securefolder", "Knox Secure Folder", true, "Knox Security", "secure_folder"),
                AppLockInfo("com.android.chrome", "Google Chrome", false, "Browsers", "chrome"),
                AppLockInfo("com.whatsapp", "WhatsApp Messenger", true, "Communication", "whatsapp"),
                AppLockInfo("com.sec.android.app.camera", "Samsung Camera", false, "System Applications", "camera"),
                AppLockInfo("com.android.settings", "Settings", true, "System Applications", "settings"),
                AppLockInfo("com.chase.sig.android", "Apex Premium Banking", false, "Finance & Wealth", "banking"),
                AppLockInfo("com.google.android.apps.photos", "Google Photos", false, "Gallery & Photos", "photos"),
                AppLockInfo("com.google.android.apps.messaging", "Android Messages", false, "Communication", "messages"),
                AppLockInfo("com.instagram.android", "Instagram", false, "Communication", "instagram")
            )
            appLockDao.insertLocks(defaults)
            
            // Log initial configuration
            insertLog("SYSTEM", "SECURE_INIT", "Database initialized with Knox shield active. 12 default targets mapped.")
        }
    }

    suspend fun updateLockStatus(packageName: String, locked: Boolean) {
        val timestamp = System.currentTimeMillis()
        appLockDao.updateLockStatus(packageName, locked, timestamp)
    }

    suspend fun insertLog(appName: String, eventType: String, message: String) {
        val log = SecurityLog(
            eventType = eventType,
            appName = appName,
            message = message
        )
        securityLogDao.insertLog(log)
    }

    suspend fun clearLogs() {
        securityLogDao.clearAllLogs()
    }
}
