package nl.giejay.android.tv.immich.shared.util

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetExifInfo
import nl.giejay.android.tv.immich.api.model.Person
import org.junit.Test
import java.util.Date
import java.util.UUID

class PortraitPairingTest {
    private val day = 24L * 60 * 60 * 1000
    private val base = 1_700_000_000_000L
    private val anna = UUID.randomUUID()
    private val ben = UUID.randomUUID()

    private fun person(id: UUID) = Person("p", id, "")

    private fun portrait(
        id: String,
        takenOffsetMillis: Long? = 0,
        city: String? = null,
        people: List<UUID> = emptyList(),
    ) = Asset(
        id = id, type = "IMAGE", deviceAssetId = null,
        exifInfo = AssetExifInfo(null, 1, 800, 1200, city, null, takenOffsetMillis?.let { Date(base + it) }, null, null),
        fileCreatedAt = takenOffsetMillis?.let { Date(base + it) }, fileModifiedAt = Date(base),
        albumName = null, people = people.map(::person), tags = null, originalPath = null, originalFileName = null,
    )

    @Test
    fun `same person and city wins and is the furthest in time`() {
        val first = portrait("first", 0, "Rome", listOf(anna))
        val near = portrait("near", 1 * day, "Rome", listOf(anna))
        val far = portrait("far", 30 * day, "Rome", listOf(anna))
        val otherCity = portrait("other", 2 * day, "Paris", listOf(anna))

        assertThat(pickPortraitPartner(first, listOf(near, far, otherCity))).isEqualTo(far)
    }

    @Test
    fun `same person beats same city when no person and city pair exists`() {
        val first = portrait("first", 0, "Rome", listOf(anna))
        val samePerson = portrait("person", 5 * day, "Paris", listOf(anna, ben))
        val sameCity = portrait("city", 0, "Rome", listOf(ben))

        assertThat(pickPortraitPartner(first, listOf(sameCity, samePerson))).isEqualTo(samePerson)
    }

    @Test
    fun `without people the same city and day is preferred, closest in time`() {
        val first = portrait("first", 0, "Rome")
        val cityOnly = portrait("cityOnly", 10 * day, "Rome")
        val dayOnly = portrait("dayOnly", 1000, "Paris")
        val cityAndDay = portrait("cityAndDay", 3_600_000, "Rome")
        val cityAndDayLater = portrait("later", 7_200_000, "Rome")

        assertThat(pickPortraitPartner(first, listOf(cityOnly, dayOnly, cityAndDayLater, cityAndDay))).isEqualTo(cityAndDay)
    }

    @Test
    fun `then same city, then same day, and the closest in time within a tier`() {
        val first = portrait("first", 0, "Rome")
        val cityFar = portrait("cityFar", 40 * day, "Rome")
        val cityNear = portrait("cityNear", 10 * day, "Rome")
        val dayOnly = portrait("dayOnly", 1000, "Paris")

        assertThat(pickPortraitPartner(first, listOf(cityFar, cityNear, dayOnly))).isEqualTo(cityNear)
        assertThat(pickPortraitPartner(first, listOf(dayOnly, portrait("elsewhere", 50 * day, "Oslo")))).isEqualTo(dayOnly)
    }

    @Test
    fun `nothing fits gives null so the caller takes any`() {
        val first = portrait("first", 0, "Rome")
        assertThat(pickPortraitPartner(first, listOf(portrait("x", 50 * day, "Oslo"), portrait("y", 90 * day, null)))).isNull()
        assertThat(pickPortraitPartner(first, emptyList())).isNull()
    }

    @Test
    fun `photos without any date or city behave as before`() {
        val first = portrait("first", null)
        assertThat(pickPortraitPartner(first, listOf(portrait("a", null), portrait("b", null)))).isNull()
    }

    @Test
    fun `toSliderItems pairs the portraits by place`() {
        val rome1 = portrait("r1", 0, "Rome")
        val paris1 = portrait("p1", 100 * day, "Paris")
        val rome2 = portrait("r2", 1000, "Rome")
        val paris2 = portrait("p2", 100 * day + 1000, "Paris")

        val pairs = listOf(rome1, paris1, rome2, paris2).toSliderItems(keepOrder = false, mergePortrait = true)
            .map { setOfNotNull(it.mainItem.id, it.secondaryItem?.id) }

        assertThat(pairs).containsExactly(setOf("r1", "r2"), setOf("p1", "p2"))
    }
}
