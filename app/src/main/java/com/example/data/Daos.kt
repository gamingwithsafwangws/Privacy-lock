package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLockDao {
    @Query("SELECT * FROM app_lock_info ORDER BY appName ASC")
    fun getAllLocks(): Flow<List<AppLockInfo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLock(lockInfo: AppLockInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocks(locks: List<AppLockInfo>)

    @Query("UPDATE app_lock_info SET isLocked = :locked, lockedAt = :timestamp WHERE packageName = :pkgName")
    suspend fun updateLockStatus(pkgName: String, locked: Boolean, timestamp: Long)

    @Query("SELECT COUNT(*) FROM app_lock_info")
    suspend fun getCount(): Int
}

@Dao
interface SecurityLogDao {
    @Query("SELECT * FROM security_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<SecurityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SecurityLog)

    @Query("DELETE FROM security_logs")
    suspend fun clearAllLogs()
}
