package nl.giejay.android.tv.immich.shared.util

import nl.giejay.android.tv.immich.api.model.Asset
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Picks the portrait photo to show next to [first] from [candidates] (other portrait photos), or null when none
 * fits and the caller should just take any. Order of preference:
 * 1. same first person and same city, 2. same first person (both: the one furthest away in time, for variety),
 * 3. same city and same day, 4. same city, 5. same day (these: the closest in time, so the pair belongs together).
 */
internal fun pickPortraitPartner(first: Asset, candidates: List<Asset>): Asset? {
    val firstPerson = first.people?.firstOrNull()?.id
    val firstModified = first.fileModifiedAt?.time
    if (firstPerson != null && firstModified != null) {
        val sameCity = candidates.filter { candidate ->
            candidate.people?.size == first.people.size &&
                candidate.people.firstOrNull()?.id == firstPerson &&
                candidate.exifInfo?.city != null && first.exifInfo?.city != null &&
                candidate.exifInfo.city == first.exifInfo.city &&
                candidate.exifInfo.dateTimeOriginal != null
        }.maxByOrNull { kotlin.math.abs(candidate(it).time - firstModified) }
        val samePerson = sameCity ?: candidates.filter { candidate ->
            candidate.people?.firstOrNull()?.id == firstPerson && candidate.exifInfo?.dateTimeOriginal != null
        }.maxByOrNull { kotlin.math.abs(candidate(it).time - firstModified) }
        if (samePerson != null) return samePerson
    }

    val firstCity = first.exifInfo?.city?.takeIf { it.isNotBlank() }
    val firstTaken = taken(first)
    fun sameCity(other: Asset) = firstCity != null && other.exifInfo?.city == firstCity
    fun sameDay(other: Asset) = firstTaken != null && taken(other)?.let { dayOf(it) == dayOf(firstTaken) } == true
    fun distance(other: Asset) = taken(other)?.let { kotlin.math.abs(it.time - firstTaken!!.time) } ?: Long.MAX_VALUE

    val tiers = listOf<(Asset) -> Boolean>(
        { sameCity(it) && sameDay(it) },
        { sameCity(it) },
        { sameDay(it) },
    )
    for (tier in tiers) {
        val matches = candidates.filter(tier)
        if (matches.isNotEmpty()) {
            return if (firstTaken != null) matches.minByOrNull(::distance) else matches.first()
        }
    }
    return null
}

private fun candidate(asset: Asset): Date = asset.exifInfo!!.dateTimeOriginal!!

private fun taken(asset: Asset): Date? = asset.exifInfo?.dateTimeOriginal ?: asset.fileCreatedAt

private fun dayOf(date: Date): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
    time = date
    get(Calendar.YEAR) * 1000L + get(Calendar.DAY_OF_YEAR)
}
