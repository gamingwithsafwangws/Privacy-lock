package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_lock_info")
data class AppLockInfo(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isLocked: Boolean,
    val category: String,
    val iconIdentifier: String, // name of a standard icon we use in simulation (e.g., "gallery", "notes", etc.)
    val lockedAt: Long = System.currentTimeMillis()
)
