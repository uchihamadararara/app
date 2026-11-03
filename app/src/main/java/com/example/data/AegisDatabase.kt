package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "locked_apps")
data class LockedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,
    val isSystemCritical: Boolean = false,
    val isLocked: Boolean = false,
    val customAuthMode: String = "GLOBAL_DEFAULT",
    val interceptCount: Int = 0,
    val lastLockedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "security_logs")
data class SecurityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // FAILED_ATTEMPT, COOLDOWN_TRIGGERED, APP_UNLOCKED, BOOT_RESTORED, ADMIN_SHIELD, APP_INTERCEPTED, NOTIFICATION_MASKED
    val targetAppName: String,
    val targetPackage: String,
    val authMethod: String, // FINGERPRINT, FACE_LOCK, PIN, PATTERN, SYSTEM
    val attemptNumber: Int = 0,
    val details: String,
    val photoPath: String? = null
)

@Dao
interface AegisDao {
    @Query("SELECT * FROM locked_apps ORDER BY isLocked DESC, isSystemCritical DESC, appName ASC")
    fun observeAllApps(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps")
    suspend fun getAllAppsOnce(): List<LockedAppEntity>

    @Query("SELECT * FROM locked_apps WHERE packageName = :pkg LIMIT 1")
    suspend fun getAppByPackage(pkg: String): LockedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: LockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppsIgnoreExisting(apps: List<LockedAppEntity>)

    @Query("UPDATE locked_apps SET isLocked = :locked, lastLockedTimestamp = :timestamp WHERE packageName = :pkg")
    suspend fun setAppLockState(pkg: String, locked: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE locked_apps SET customAuthMode = :authMode WHERE packageName = :pkg")
    suspend fun setAppCustomAuthMode(pkg: String, authMode: String)

    @Query("UPDATE locked_apps SET interceptCount = interceptCount + 1 WHERE packageName = :pkg")
    suspend fun incrementInterceptCount(pkg: String)

    @Query("SELECT * FROM security_logs ORDER BY timestamp DESC LIMIT 120")
    fun observeSecurityLogs(): Flow<List<SecurityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecurityLog(log: SecurityLogEntity): Long

    @Query("UPDATE security_logs SET photoPath = :photoPath, details = :details WHERE id = :id")
    suspend fun attachIntruderPhotoToLog(id: Int, photoPath: String, details: String)

    @Query("DELETE FROM security_logs")
    suspend fun clearSecurityLogs()
}

@Database(
    entities = [LockedAppEntity::class, SecurityLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AegisDatabase : RoomDatabase() {
    abstract fun aegisDao(): AegisDao

    companion object {
        @Volatile
        private var INSTANCE: AegisDatabase? = null

        fun getInstance(context: Context): AegisDatabase {
            return INSTANCE ?: synchronized(this) {
                val deviceCtx = SecurityCrypto.getDeviceProtectedContext(context)
                val instance = Room.databaseBuilder(
                    deviceCtx,
                    AegisDatabase::class.java,
                    "aegis_security_vault.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
