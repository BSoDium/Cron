package fr.bsodium.cron.session.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["date"], unique = true)],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val date: String, // YYYY-MM-DD, morning date
    val status: String, // SessionStatus name
    val planJson: String,
    val currentInstructionJson: String,
    val lastAiCallAt: Long?, // epoch ms
    val snoozeCount: Int,
    val timezone: String,
    val cachedFirstEventSig: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "session_events",
    indices = [Index("sessionId")],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SessionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val trigger: String, // TriggerType name
    val timestamp: Long, // epoch ms
    val dataJson: String,
)

@Entity(
    tableName = "ai_messages",
    indices = [Index(value = ["sessionId", "turnIndex"])],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val turnIndex: Int,
    val role: String, // "user" | "assistant"
    val contentJson: String, // Anthropic content blocks as JSON
    val createdAt: Long,
)
