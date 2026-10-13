package dev.morse.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactHit(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phone: String?,
) {
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "#" }

    val lookupUri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

/** Contact lookups for search. Only runs when READ_CONTACTS was granted. */
class ContactsRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun search(query: String, limit: Int = 3): List<ContactHit> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !hasPermission()) return@withContext emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        val hits = ArrayList<ContactHit>()
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && hits.size < limit) {
                    val id = c.getLong(0)
                    val name = c.getString(2) ?: continue
                    val phone = if (c.getInt(3) > 0) firstPhone(id) else null
                    hits += ContactHit(id, c.getString(1) ?: "", name, phone)
                }
            }
        } catch (e: SecurityException) {
            return@withContext emptyList()
        } catch (e: IllegalArgumentException) {
            return@withContext emptyList()
        }
        hits
    }

    private fun firstPhone(contactId: Long): String? =
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}
