package com.phone.contacts.util

import com.phone.contacts.R
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast

private const val WHATSAPP_PACKAGE = "com.whatsapp"
private const val MIMETYPE_VOICE_CALL = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
private const val MIMETYPE_VIDEO_CALL = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"

object WhatsAppUtils {
    fun isInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(WHATSAPP_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    /** Opens the number's WhatsApp chat. Used for the Message action, and as the fallback for
     * Voice call/Video call when the contact has no WhatsApp-linked data row (see
     * [launchVoiceCall]/[launchVideoCall]) - e.g. contacts-sync with WhatsApp is off, or this is a
     * raw call-log number with no saved contact at all. */
    fun openChat(context: Context, number: String) {
        if (number.isBlank()) return
        val digits = number.filter { it.isDigit() || it == '+' }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
            setPackage(WHATSAPP_PACKAGE)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_whatsapp_missing), Toast.LENGTH_SHORT).show()
        }
    }

    /** Whether [contactId] specifically has WhatsApp - not just whether the WhatsApp app itself
     * is installed on the device (see [isInstalled]). Matches the reference app's own check
     * (confirmed by decompiling it - it does NOT key off a chat/call-specific data row): a
     * contact's row in the Phone table carries an `account_type_and_data_set` column identifying
     * which sync source wrote that phone number, and WhatsApp's own contact-sync account writes
     * it as something containing "com.whatsapp". A contact that isn't on WhatsApp, or was added
     * before sync ran, has no such row even though the WhatsApp app itself is installed. */
    fun hasWhatsAppContact(context: Context, contactId: String?): Boolean {
        if (contactId.isNullOrBlank()) return false
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE_AND_DATA_SET),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                val columnIndex = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE_AND_DATA_SET)
                var found = false
                while (!found && cursor.moveToNext()) {
                    found = cursor.getString(columnIndex)?.contains(WHATSAPP_PACKAGE, ignoreCase = true) == true
                }
                found
            } ?: false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Places a WhatsApp voice call directly, bypassing the chat screen - returns false (caller
     * should fall back to [openChat]) if this contact has no WhatsApp voice-call data row. */
    fun launchVoiceCall(context: Context, contactId: String?): Boolean =
        launchCall(context, contactId, MIMETYPE_VOICE_CALL)

    /** Same as [launchVoiceCall] but for video calls. */
    fun launchVideoCall(context: Context, contactId: String?): Boolean =
        launchCall(context, contactId, MIMETYPE_VIDEO_CALL)

    /** WhatsApp, when contact sync is enabled, writes its own rows into Android's Contacts
     * provider for every contact it recognizes - one row per action (chat/voice call/video call),
     * each with a distinct MIMETYPE. Android's own Contacts app and most OEM dialers use this same
     * mechanism to show "WhatsApp voice call"/"WhatsApp video call" as native-feeling actions; it's
     * the only way to launch WhatsApp's own call UI directly since WhatsApp exposes no public
     * call intent otherwise. Requires [contactId] (the aggregated Contacts._ID) - a raw phone
     * number with no saved contact has no such row to find. */
    private fun launchCall(context: Context, contactId: String?, mimeType: String): Boolean {
        if (contactId.isNullOrBlank()) return false
        val dataId = try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId, mimeType),
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        } catch (_: SecurityException) {
            null
        } ?: return false

        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId),
                        mimeType
                    )
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
