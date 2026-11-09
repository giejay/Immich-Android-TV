package nl.giejay.android.tv.immich.card

data class Card(
    override val title: String,
    override val description: String?,
    override val id: String,
    override val thumbnailUrl: String?,
    override val backgroundUrl: String?,
    override var selected: Boolean = false,
    override val isFolder: Boolean = false,
    override val coverUrl: String? = null,
    override val badge: String? = null) : ICard
