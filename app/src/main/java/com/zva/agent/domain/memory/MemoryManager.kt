package com.zva.agent.domain.memory

import com.zva.agent.data.db.MemoryDao
import com.zva.agent.data.db.MemoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    private val memoryDao: MemoryDao,
) {
    suspend fun remember(content: String, category: String = "episodic", importance: Float = 0.5f) {
        memoryDao.insert(
            MemoryEntity(
                category = category,
                content = content,
                importance = importance,
            )
        )
    }

    suspend fun recall(query: String): List<String> {
        // Simple keyword matching — production would use embeddings
        val all = memoryDao.getTopMemories(50)
        val queryLower = query.lowercase()
        return all
            .filter { it.content.lowercase().contains(queryLower) }
            .onEach { memoryDao.touch(it.id) }
            .map { "[${it.category}] ${it.content}" }
    }

    suspend fun getTopMemories(limit: Int = 20): List<MemoryEntity> =
        memoryDao.getTopMemories(limit)

    fun getAllMemories(): Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    suspend fun getMemoryContext(): String {
        val memories = memoryDao.getTopMemories(10)
        if (memories.isEmpty()) return ""
        return buildString {
            appendLine("## Your accumulated memories (shaped by experience):")
            memories.forEach { m ->
                appendLine("- [${m.category}] ${m.content}")
            }
        }
    }

    suspend fun delete(memory: MemoryEntity) = memoryDao.delete(memory)
}
