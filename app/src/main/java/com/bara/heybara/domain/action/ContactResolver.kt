package com.bara.heybara.domain.action

data class Contact(
    val name: String,
    val phoneNumber: String
)

interface ContactResolver {
    suspend fun searchContacts(query: String): List<Contact>
}
