package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedApp
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.data.local.entity.DailyScreenTime
import com.example.data.local.entity.DailyUsageLog
import com.example.data.local.entity.GroupApp
import com.example.data.local.entity.ScreenTimeLimit
import kotlinx.coroutines.flow.Flow

data class GroupWithApps(
    val group: AppGroup,
    val packageNames: List<String>
)

@Dao
interface FocusDao {

    // --- Blocked Apps ---
    @Query("SELECT * FROM blocked_apps ORDER BY dateAdded DESC")
    fun getAllBlockedApps(): Flow<List<BlockedApp>>

    @Query("SELECT * FROM blocked_apps")
    suspend fun getAllBlockedAppsSync(): List<BlockedApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApp(blockedApp: BlockedApp)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteBlockedApp(packageName: String)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :packageName)")
    suspend fun isAppBlocked(packageName: String): Boolean


    // --- Screen Time Limits ---
    @Query("SELECT * FROM screen_time_limits")
    fun getAllScreenTimeLimits(): Flow<List<ScreenTimeLimit>>

    @Query("SELECT * FROM screen_time_limits")
    suspend fun getAllScreenTimeLimitsSync(): List<ScreenTimeLimit>

    @Query("SELECT * FROM screen_time_limits WHERE packageName = :packageName")
    suspend fun getScreenTimeLimit(packageName: String): ScreenTimeLimit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateScreenTimeLimit(limit: ScreenTimeLimit)

    @Query("DELETE FROM screen_time_limits WHERE packageName = :packageName")
    suspend fun deleteScreenTimeLimit(packageName: String)

    @Query("UPDATE screen_time_limits SET usedMinutesToday = :usedMinutes, lastResetDate = :date WHERE packageName = :packageName")
    suspend fun updateScreenTimeUsage(packageName: String, usedMinutes: Int, date: String)


    // --- Groups ---
    @Query("SELECT * FROM app_groups ORDER BY id DESC")
    fun getAllGroups(): Flow<List<AppGroup>>

    @Query("SELECT * FROM app_groups")
    suspend fun getAllGroupsSync(): List<AppGroup>

    @Query("SELECT * FROM app_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): AppGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: AppGroup): Long

    @Update
    suspend fun updateGroup(group: AppGroup)

    @Query("DELETE FROM app_groups WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    @Query("UPDATE app_groups SET usedMinutesToday = :usedMinutes, lastResetDate = :date WHERE id = :id")
    suspend fun updateGroupUsage(id: Long, usedMinutes: Int, date: String)


    // --- Group Apps ---
    @Query("SELECT packageName FROM group_apps WHERE groupId = :groupId")
    fun getAppsForGroup(groupId: Long): Flow<List<String>>

    @Query("SELECT packageName FROM group_apps WHERE groupId = :groupId")
    suspend fun getAppsForGroupSync(groupId: Long): List<String>

    @Query("SELECT * FROM group_apps")
    suspend fun getAllGroupAppsSync(): List<GroupApp>

    @Query("SELECT * FROM group_apps")
    fun getAllGroupApps(): Flow<List<GroupApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupApps(groupApps: List<GroupApp>)

    @Query("DELETE FROM group_apps WHERE groupId = :groupId")
    suspend fun deleteAppsForGroup(groupId: Long)

    @Transaction
    suspend fun replaceAppsForGroup(groupId: Long, packageNames: List<String>) {
        deleteAppsForGroup(groupId)
        val entities = packageNames.map { GroupApp(groupId = groupId, packageName = it) }
        insertGroupApps(entities)
    }

    @Transaction
    suspend fun deleteGroupAndApps(groupId: Long) {
        deleteAppsForGroup(groupId)
        deleteGroupById(groupId)
    }


    // --- Blocked Websites ---
    @Query("SELECT * FROM blocked_websites ORDER BY dateAdded DESC")
    fun getAllBlockedWebsites(): Flow<List<BlockedWebsite>>

    @Query("SELECT * FROM blocked_websites")
    suspend fun getAllBlockedWebsitesSync(): List<BlockedWebsite>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedWebsite(website: BlockedWebsite)

    @Query("DELETE FROM blocked_websites WHERE id = :id")
    suspend fun deleteBlockedWebsite(id: Long)


    // --- Blocked Keywords ---
    @Query("SELECT * FROM blocked_keywords ORDER BY dateAdded DESC")
    fun getAllBlockedKeywords(): Flow<List<BlockedKeyword>>

    @Query("SELECT * FROM blocked_keywords")
    suspend fun getAllBlockedKeywordsSync(): List<BlockedKeyword>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedKeyword(keyword: BlockedKeyword)

    @Query("DELETE FROM blocked_keywords WHERE id = :id")
    suspend fun deleteBlockedKeyword(id: Long)


    // --- Daily Usage Logs ---
    @Query("SELECT * FROM daily_usage_logs WHERE date = :date ORDER BY minutesUsed DESC")
    fun getUsageLogsForDate(date: String): Flow<List<DailyUsageLog>>

    @Query("SELECT * FROM daily_usage_logs WHERE date = :date ORDER BY minutesUsed DESC")
    suspend fun getUsageLogsForDateSync(date: String): List<DailyUsageLog>

    @Query("SELECT * FROM daily_usage_logs WHERE packageName = :packageName AND date = :date")
    suspend fun getUsageLog(packageName: String, date: String): DailyUsageLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDailyUsageLog(log: DailyUsageLog)

    @Query("SELECT SUM(minutesUsed) FROM daily_usage_logs WHERE date = :date")
    fun getTotalMinutesUsedForDate(date: String): Flow<Int?>

    // --- Daily Device Screen-On Time ---
    @Query("SELECT screenOnMinutes FROM daily_screen_time WHERE date = :date")
    fun getScreenOnMinutesForDate(date: String): Flow<Int?>

    @Query("SELECT screenOnMinutes FROM daily_screen_time WHERE date = :date")
    suspend fun getScreenOnMinutesForDateSync(date: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateScreenOnTime(item: DailyScreenTime)
}
