package com.example.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.model.BuildHistoryItem
import com.example.model.BuildMode
import com.example.model.BuildResult
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "build_history")
data class BuildHistoryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectName: String,
    val buildType: String,
    val timestamp: Long,
    val result: String,
    val apkPath: String?,
    val apkSize: Long,
    val durationMs: Long,
    val abiSelection: String,
    val logFilePath: String,
    val errorSummary: String?
) {
    fun toDomain(): BuildHistoryItem {
        return BuildHistoryItem(
            id = id,
            projectId = projectId,
            projectName = projectName,
            buildType = try { BuildMode.valueOf(buildType) } catch (e: Exception) { BuildMode.DEBUG },
            timestamp = timestamp,
            result = try { BuildResult.valueOf(result) } catch (e: Exception) { BuildResult.FAILED },
            apkPath = apkPath,
            apkSize = apkSize,
            durationMs = durationMs,
            abiSelection = abiSelection,
            logFilePath = logFilePath,
            errorSummary = errorSummary
        )
    }

    companion object {
        fun fromDomain(item: BuildHistoryItem): BuildHistoryEntity {
            return BuildHistoryEntity(
                id = item.id,
                projectId = item.projectId,
                projectName = item.projectName,
                buildType = item.buildType.name,
                timestamp = item.timestamp,
                result = item.result.name,
                apkPath = item.apkPath,
                apkSize = item.apkSize,
                durationMs = item.durationMs,
                abiSelection = item.abiSelection,
                logFilePath = item.logFilePath,
                errorSummary = item.errorSummary
            )
        }
    }
}

@Dao
interface BuildHistoryDao {
    @Query("SELECT * FROM build_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<BuildHistoryEntity>>

    @Query("SELECT * FROM build_history WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getHistoryForProject(projectId: String): Flow<List<BuildHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: BuildHistoryEntity)

    @Query("DELETE FROM build_history WHERE id = :id")
    suspend fun deleteHistory(id: String)

    @Query("DELETE FROM build_history")
    suspend fun clearHistory()
}
