package com.example.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.model.CompilerTool
import com.example.model.ToolStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tools")
data class ToolEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val installedVersion: String?,
    val latestVersion: String,
    val downloadSizeBytes: Long,
    val installedSizeBytes: Long,
    val status: String,
    val isRequired: Boolean,
    val downloadUrl: String,
    val sha256Checksum: String,
    val localDirectoryPath: String,
    val primaryBinaryPath: String?,
    val compatibilityNotes: String,
    val errorMessage: String?
) {
    fun toDomain(): CompilerTool {
        return CompilerTool(
            id = id,
            name = name,
            description = description,
            installedVersion = installedVersion,
            latestVersion = latestVersion,
            downloadSizeBytes = downloadSizeBytes,
            installedSizeBytes = installedSizeBytes,
            status = try { ToolStatus.valueOf(status) } catch (e: Exception) { ToolStatus.NOT_INSTALLED },
            isRequired = isRequired,
            downloadUrl = downloadUrl,
            sha256Checksum = sha256Checksum,
            localDirectoryPath = localDirectoryPath,
            primaryBinaryPath = primaryBinaryPath,
            compatibilityNotes = compatibilityNotes,
            errorMessage = errorMessage
        )
    }

    companion object {
        fun fromDomain(tool: CompilerTool): ToolEntity {
            return ToolEntity(
                id = tool.id,
                name = tool.name,
                description = tool.description,
                installedVersion = tool.installedVersion,
                latestVersion = tool.latestVersion,
                downloadSizeBytes = tool.downloadSizeBytes,
                installedSizeBytes = tool.installedSizeBytes,
                status = tool.status.name,
                isRequired = tool.isRequired,
                downloadUrl = tool.downloadUrl,
                sha256Checksum = tool.sha256Checksum,
                localDirectoryPath = tool.localDirectoryPath,
                primaryBinaryPath = tool.primaryBinaryPath,
                compatibilityNotes = tool.compatibilityNotes,
                errorMessage = tool.errorMessage
            )
        }
    }
}

@Dao
interface ToolDao {
    @Query("SELECT * FROM tools ORDER BY isRequired DESC, name ASC")
    fun getAllTools(): Flow<List<ToolEntity>>

    @Query("SELECT * FROM tools WHERE id = :id LIMIT 1")
    suspend fun getToolById(id: String): ToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTool(tool: ToolEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tools: List<ToolEntity>)

    @Update
    suspend fun updateTool(tool: ToolEntity)

    @Query("UPDATE tools SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateToolStatus(id: String, status: String, errorMessage: String? = null)

    @Query("UPDATE tools SET status = :status, installedVersion = :installedVersion, installedSizeBytes = :installedSize WHERE id = :id")
    suspend fun markInstalled(id: String, status: String, installedVersion: String, installedSize: Long)
}
