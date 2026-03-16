package com.bara.heybara.data.action

import android.content.Context
import android.provider.ContactsContract
import com.bara.heybara.domain.action.Contact
import com.bara.heybara.domain.action.ContactResolver

class DeviceContactResolver(private val context: Context) : ContactResolver {

    override suspend fun searchContacts(query: String): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)
                val number = cursor.getString(numberIdx)
                if (name != null && number != null) {
                    contacts.add(Contact(name, number))
                }
            }
        }
        return contacts.distinctBy { it.phoneNumber }
    }
}
