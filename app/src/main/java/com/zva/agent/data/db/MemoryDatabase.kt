package com.zva.agent.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entity ───────────────────────────────────────────────────────────────────

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val toolName: String? = null,
    val toolCallId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
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

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "◈",
    val systemPrompt: String,
    val temperature: Float = 0.7f,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sub_agents")
data class SubAgentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String,
    val systemPrompt: String,
    val parentId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
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

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

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

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY isDefault DESC, createdAt ASC")
    fun getAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): PersonaEntity?

    @Insert
    suspend fun insert(persona: PersonaEntity): Long

    @Update
    suspend fun update(persona: PersonaEntity)

    @Delete
    suspend fun delete(persona: PersonaEntity)

    @Query("UPDATE personas SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("UPDATE personas SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)
}

@Dao
interface SubAgentDao {
    @Query("SELECT * FROM sub_agents WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SubAgentEntity>>

    @Insert
    suspend fun insert(agent: SubAgentEntity): Long

    @Query("UPDATE sub_agents SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Delete
    suspend fun delete(agent: SubAgentEntity)
}

// ── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [MessageEntity::class, MemoryEntity::class, SessionEntity::class, PersonaEntity::class, SubAgentEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun sessionDao(): SessionDao
    abstract fun personaDao(): PersonaDao
    abstract fun subAgentDao(): SubAgentDao
}
