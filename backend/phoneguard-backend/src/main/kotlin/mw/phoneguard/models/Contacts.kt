package mw.phoneguard.models

import kotlinx.serialization.Serializable

@Serializable
data class ContactDto(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val createdAt: String
)

@Serializable
data class CreateContactRequest(
    val name: String,
    val phone: String,
    val email: String? = null
)
