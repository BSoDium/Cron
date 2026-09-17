package fr.bsodium.cron.memory

import kotlinx.datetime.Instant

data class MemoryEntry(
    val id: Long,
    val text: String,
    val category: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val pending: Boolean = false,
    val instruction: String? = null,
    val failureReason: String? = null,
)
