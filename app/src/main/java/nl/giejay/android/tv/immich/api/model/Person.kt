package nl.giejay.android.tv.immich.api.model

import java.util.UUID

data class Person(
    val name: String,
    val id: UUID,
    val thumbnailPath: String
)

data class PeopleResponse(val total: Int,
                          val hasNextPage: Boolean,
                          val people: List<Person>)