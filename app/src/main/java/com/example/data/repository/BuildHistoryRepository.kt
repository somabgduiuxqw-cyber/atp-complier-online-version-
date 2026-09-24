package com.example.data.repository

import com.example.data.db.BuildHistoryDao
import com.example.data.db.BuildHistoryEntity
import com.example.model.BuildHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BuildHistoryRepository(
    private val buildHistoryDao: BuildHistoryDao
) {
    val allHistory: Flow<List<BuildHistoryItem>> = buildHistoryDao.getAllHistory()
        .map { list -> list.map { it.toDomain() } }

    fun getHistoryForProject(projectId: String): Flow<List<BuildHistoryItem>> {
        return buildHistoryDao.getHistoryForProject(projectId)
            .map { list -> list.map { it.toDomain() } }
    }

    suspend fun addBuildRecord(item: BuildHistoryItem) = withContext(Dispatchers.IO) {
        buildHistoryDao.insertHistory(BuildHistoryEntity.fromDomain(item))
    }

    suspend fun deleteBuildRecord(id: String) = withContext(Dispatchers.IO) {
        buildHistoryDao.deleteHistory(id)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        buildHistoryDao.clearHistory()
    }
}
