package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_logs")
data class SecurityLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "AUTH_LAUNCH_SUCCESS", "AUTH_LAUNCH_FAIL", "LOCK_ACTIVATED", "LOCK_DEACTIVATED", "CALIBRATION"
    val appName: String, // Related app name, empty for system events
    val message: String
)
