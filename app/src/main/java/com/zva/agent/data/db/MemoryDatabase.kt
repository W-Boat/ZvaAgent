package com.zva.agent.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entity ───────────────────────────────────────────────────────────────────

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,          // "user", "assistant_zva", "assistant_dia", "tool"
    val content: String,
    val toolName: String? = null,
    val toolCallId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,      // "episodic", "semantic", "preference"
    val content: String,
    val importance: Float = 0.5f,
    val accessCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessageAt: Long = System.currentTimeMillis(),
)

// ── DAO ──────────────────────────────────────────────────────────────────────

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessages(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int = 50): Flow<List<MessageEntity>>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importance DESC, lastAccessedAt DESC LIMIT :limit")
    suspend fun getTopMemories(limit: Int = 20): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY importance DESC")
    suspend fun getMemoriesByCategory(category: String): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY importance DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("UPDATE memories SET accessCount = accessCount + 1, lastAccessedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long = System.currentTimeMillis())
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY lastMessageAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)
}

// ── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [MessageEntity::class, MemoryEntity::class, SessionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun sessionDao(): SessionDao
}
