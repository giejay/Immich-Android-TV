package nl.giejay.android.tv.immich.settings

import nl.giejay.android.tv.immich.card.ICard

class SettingsCard(
    override val title: String,
    override val description: String?,
    override val id: String,
    override val thumbnailUrl: String?,
    override val pictureUrl: String?,
    override val selected: Boolean = false,
    val onClick: () -> Unit,
) : ICard {
}