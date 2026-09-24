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

/** Append-only raw sensor log, independent of [SessionEventEntity]: every screen/motion transition
 *  is written here unconditionally, not just the subset the FSM acts on, so a night can be
 *  relabeled with hindsight after the fact. See docs/sleep-detection-architecture.md §5. */
@Entity(
    tableName = "observation_log",
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
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val type: String, // free-form tag, e.g. "screen_off", "ar_walking" -- see RawObservationSink
    val timestamp: Long, // epoch ms
    val payloadJson: String, // small feature bag; "{}" until a phase needs richer payloads
)

@Entity(tableName = "memory_entries")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val category: String?, // free-text, optional -- no closed set defined by the feature spec
    val createdAt: Long,
    val updatedAt: Long,
    val pending: Boolean = false,
    val instruction: String? = null,
    val failureReason: String? = null,
)
