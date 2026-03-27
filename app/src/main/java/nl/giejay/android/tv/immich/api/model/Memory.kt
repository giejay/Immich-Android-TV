package nl.giejay.android.tv.immich.api.model

import java.util.Date

data class MemoryData(
    val year: Int
)

data class Memory(
    val id: String,
    val type: String,
    val data: MemoryData?,
    val assets: List<Asset>,
    val memoryAt: Date?,
    val isSaved: Boolean
)
