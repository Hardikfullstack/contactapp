package com.phone.contacts.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.FileProvider
import com.phone.contacts.data.local.AppDatabase
import com.phone.contacts.data.local.DeletedContactEntity
import com.phone.contacts.util.AnalyticsEvents
import com.phone.contacts.util.AnalyticsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/** A phone/email/website value paired with its type label ("Mobile", "Home", "Work", "Other", or
 * "Custom" with a free-text [customLabel]), matching the reference app's own per-entry type
 * picker in its Add Contact screen. */
data class TypedValue(val value: String, val type: String, val customLabel: String = "")

/** A structured postal address (Street/City/State/Postcode/Country, matching the reference app's
 * own address fields) plus its type label. */
data class AddressValue(
    val type: String,
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val postcode: String = "",
    val country: String = "",
    val customLabel: String = ""
) {
    fun isBlank() = street.isBlank() && city.isBlank() && state.isBlank() && postcode.isBlank() && country.isBlank()
}

/** An important date (birthday/anniversary/other/custom) as picked from the native date picker. */
data class DateValue(val type: String, val dateMillis: Long, val customLabel: String = "")

/** Everything the Add Contact screen can collect, matching the reference app's field set:
 * structured-or-plain name, any number of typed phones/emails/websites, structured addresses,
 * important dates, typed relations (person name + a relation-kind picker), an optional structured
 * work info, notes, and an optional picked photo. */
data class NewContactInput(
    val nameExpanded: Boolean,
    val singleName: String,
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val phones: List<TypedValue>,
    val emails: List<TypedValue>,
    val addresses: List<AddressValue>,
    val importantDates: List<DateValue>,
    val websites: List<TypedValue>,
    val relations: List<TypedValue>,
    val workExpanded: Boolean,
    val jobTitle: String,
    val department: String,
    val company: String,
    val notes: String,
    val photoUri: Uri?
) {
    fun displayName(): String = if (nameExpanded) {
        listOfNotNull(
            firstName.trim().ifBlank { null },
            middleName.trim().ifBlank { null },
            lastName.trim().ifBlank { null }
        ).joinToString(" ")
    } else {
        singleName.trim()
    }
}

/** The small set of fields Contact Detail needs to refresh live after an edit — see
 * [ContactRepository.getContactSummary]. */
data class ContactSummary(val name: String?, val photoUri: String?)

/** Everything [ContactRepository.fetchFullContact] reads back out of an existing contact — the
 * exact same shape [NewContactInput] needs, so the Add Contact screen can pre-fill itself for
 * editing without a separate state model. [photoUri] here is the contact's *existing* photo (a
 * `content://` URI, for preview only) — distinct from [NewContactInput.photoUri], which is only
 * set when the user picks a brand-new photo to replace it with. */
data class FullContactData(
    val nameExpanded: Boolean,
    val singleName: String,
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val phones: List<TypedValue>,
    val emails: List<TypedValue>,
    val addresses: List<AddressValue>,
    val importantDates: List<DateValue>,
    val websites: List<TypedValue>,
    val relations: List<TypedValue>,
    val workExpanded: Boolean,
    val jobTitle: String,
    val department: String,
    val company: String,
    val notes: String,
    val photoUri: String?
)

private fun android.database.Cursor.stringOf(column: String): String {
    val index = getColumnIndex(column)
    return if (index >= 0) getString(index) ?: "" else ""
}

private fun android.database.Cursor.intOf(column: String): Int {
    val index = getColumnIndex(column)
    return if (index >= 0) getInt(index) else 0
}

/** Inverse of [formatEventDate] — parses `Event.START_DATE`'s "yyyy-MM-dd" back into millis. */
private fun parseEventDate(value: String): Long? = try {
    if (value.isBlank()) null else java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(value)?.time
} catch (_: Exception) {
    null
}

private fun phoneTypeLabel(type: Int, label: String): Pair<String, String> = when (type) {
    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile" to ""
    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home" to ""
    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work" to ""
    ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> "Custom" to label
    else -> "Other" to ""
}

private fun emailTypeLabel(type: Int, label: String): Pair<String, String> = when (type) {
    ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "Home" to ""
    ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "Work" to ""
    ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> "Custom" to label
    else -> "Other" to ""
}

private fun websiteTypeLabel(type: Int, label: String): Pair<String, String> = when (type) {
    ContactsContract.CommonDataKinds.Website.TYPE_HOME -> "Home" to ""
    ContactsContract.CommonDataKinds.Website.TYPE_WORK -> "Work" to ""
    ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM -> "Custom" to label
    else -> "Other" to ""
}

private fun postalTypeLabel(type: Int, label: String): Pair<String, String> = when (type) {
    ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> "Home" to ""
    ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> "Work" to ""
    ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM -> "Custom" to label
    else -> "Other" to ""
}

private fun eventTypeLabel(type: Int, label: String): Pair<String, String> = when (type) {
    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> "Birthday" to ""
    ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> "Anniversary" to ""
    ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM -> "Custom" to label
    else -> "Other" to ""
}

/** Relation has no TYPE_OTHER fallback (unlike Phone/Email/Website/Event) — every non-custom
 * value is one of these named constants, matching the reference app's own full relation-type list. */
private fun relationTypeLabel(type: Int, label: String): Pair<String, String> = when (type) {
    ContactsContract.CommonDataKinds.Relation.TYPE_ASSISTANT -> "Assistant" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_BROTHER -> "Brother" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_CHILD -> "Child" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_DOMESTIC_PARTNER -> "Domestic Partner" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_FATHER -> "Father" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_FRIEND -> "Friend" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_MANAGER -> "Manager" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_MOTHER -> "Mother" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_PARENT -> "Parent" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_PARTNER -> "Partner" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_REFERRED_BY -> "Referred by" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_RELATIVE -> "Relative" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_SISTER -> "Sister" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_SPOUSE -> "Spouse" to ""
    ContactsContract.CommonDataKinds.Relation.TYPE_CUSTOM -> "Custom" to label
    else -> "Assistant" to ""
}

private fun phoneTypeConstant(type: String): Int = when (type) {
    "Mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
    "Home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
    "Work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
    else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
}

private fun emailTypeConstant(type: String): Int = when (type) {
    "Home" -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
    "Work" -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
    else -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
}

private fun websiteTypeConstant(type: String): Int = when (type) {
    "Home" -> ContactsContract.CommonDataKinds.Website.TYPE_HOME
    "Work" -> ContactsContract.CommonDataKinds.Website.TYPE_WORK
    else -> ContactsContract.CommonDataKinds.Website.TYPE_OTHER
}

private fun postalTypeConstant(type: String): Int = when (type) {
    "Home" -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
    "Work" -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK
    else -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER
}

private fun eventTypeConstant(type: String): Int = when (type) {
    "Birthday" -> ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
    "Anniversary" -> ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY
    else -> ContactsContract.CommonDataKinds.Event.TYPE_OTHER
}

private fun relationTypeConstant(type: String): Int = when (type) {
    "Assistant" -> ContactsContract.CommonDataKinds.Relation.TYPE_ASSISTANT
    "Brother" -> ContactsContract.CommonDataKinds.Relation.TYPE_BROTHER
    "Child" -> ContactsContract.CommonDataKinds.Relation.TYPE_CHILD
    "Domestic Partner" -> ContactsContract.CommonDataKinds.Relation.TYPE_DOMESTIC_PARTNER
    "Father" -> ContactsContract.CommonDataKinds.Relation.TYPE_FATHER
    "Friend" -> ContactsContract.CommonDataKinds.Relation.TYPE_FRIEND
    "Manager" -> ContactsContract.CommonDataKinds.Relation.TYPE_MANAGER
    "Mother" -> ContactsContract.CommonDataKinds.Relation.TYPE_MOTHER
    "Parent" -> ContactsContract.CommonDataKinds.Relation.TYPE_PARENT
    "Partner" -> ContactsContract.CommonDataKinds.Relation.TYPE_PARTNER
    "Referred by" -> ContactsContract.CommonDataKinds.Relation.TYPE_REFERRED_BY
    "Relative" -> ContactsContract.CommonDataKinds.Relation.TYPE_RELATIVE
    "Sister" -> ContactsContract.CommonDataKinds.Relation.TYPE_SISTER
    "Spouse" -> ContactsContract.CommonDataKinds.Relation.TYPE_SPOUSE
    else -> ContactsContract.CommonDataKinds.Relation.TYPE_ASSISTANT
}

/** [ContactsContract.CommonDataKinds.Event.START_DATE] must be "yyyy-MM-dd" for the system
 * Contacts app/provider to parse it back out correctly. */
private fun formatEventDate(millis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(millis))

private const val RECYCLE_BIN_PREFS = "recycle_bin_prefs"
private const val KEY_AUTO_DELETE = "auto_delete_after_30_days"
private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000

private fun escapeVcardValue(value: String): String =
    value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

private fun unescapeVcardValue(value: String): String =
    value.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

/** Applies a type column, handling "Custom" the same way across Phone/Email/Website/
 * StructuredPostal/Event: [customConstant] (each kind's own TYPE_CUSTOM) plus a free-text
 * [labelColumn] value, instead of one of the kind's fixed type constants. */
private fun ContentProviderOperation.Builder.applyType(
    typeColumn: String,
    labelColumn: String,
    type: String,
    customLabel: String,
    customConstant: Int,
    constantFor: (String) -> Int
): ContentProviderOperation.Builder {
    if (type == "Custom") {
        withValue(typeColumn, customConstant)
        if (customLabel.isNotBlank()) withValue(labelColumn, customLabel)
    } else {
        withValue(typeColumn, constantFor(type))
    }
    return this
}

object ContactRepository {

    /** Saves a new contact with the given name/phone (both required — the only fields the current
     * Add Contact screen actually wires up; email/address/etc. are UI-only placeholders for now). */
    suspend fun saveContact(context: Context, name: String, phone: String): Boolean = withContext(Dispatchers.IO) {
        val ops = ArrayList<ContentProviderOperation>()

        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        )

        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            AnalyticsManager.logEventWithAction(AnalyticsEvents.CONTACT_CREATED, AnalyticsEvents.SCREEN_ADD_CONTACT, AnalyticsEvents.ACTION_SUCCESS, mapOf(AnalyticsEvents.PARAM_TYPE to "single"))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Saves a new contact with the reference app's full Add Contact field set — structured or
     * plain name, any number of phones/emails/addresses/important dates/websites/relations, an
     * optional structured work info (job title/department/company), notes, and an optional
     * picked photo. Kept separate from [saveContact] (used by the simpler recycle-bin restore
     * path, which only ever has a plain name/number to work with). */
    suspend fun saveFullContact(context: Context, input: NewContactInput): Boolean = withContext(Dispatchers.IO) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        buildContactDataOps(context, input, rawContactId = null, ops)
        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            AnalyticsManager.logEventWithAction(AnalyticsEvents.CONTACT_CREATED, AnalyticsEvents.SCREEN_ADD_CONTACT, AnalyticsEvents.ACTION_SUCCESS, mapOf(AnalyticsEvents.PARAM_TYPE to "full"))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Contact Detail's "Edit" action — reuses [saveFullContact]'s exact field-building logic via
     * [buildContactDataOps], but replaces the existing raw contact's editable data rows instead of
     * inserting a new raw contact. The Photo row is only touched when [NewContactInput.photoUri]
     * is non-null (the user actually picked a new photo) — otherwise the existing photo, which
     * this input has no byte data for, is left alone rather than being wiped. */
    suspend fun updateFullContact(context: Context, contactId: String, input: NewContactInput): Boolean = withContext(Dispatchers.IO) {
        val rawContactIds = getRawContactIds(context, contactId)
        val primaryRawContactId = rawContactIds.firstOrNull() ?: return@withContext false
        val ops = ArrayList<ContentProviderOperation>()

        val editableMimeTypes = listOf(
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
        )
        // Deletes across every raw contact under this aggregate, not just the first — otherwise a
        // second (or third) raw contact's own copy of each row survives untouched and every future
        // edit just piles another fresh copy on top of it.
        val rawContactPlaceholders = rawContactIds.joinToString(",") { "?" }
        val mimeTypePlaceholders = editableMimeTypes.joinToString(",") { "?" }
        ops.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} IN ($rawContactPlaceholders) AND ${ContactsContract.Data.MIMETYPE} IN ($mimeTypePlaceholders)",
                    (rawContactIds.map { it.toString() } + editableMimeTypes).toTypedArray()
                )
                .build()
        )
        if (input.photoUri != null) {
            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} IN ($rawContactPlaceholders) AND ${ContactsContract.Data.MIMETYPE} = ?",
                        (rawContactIds.map { it.toString() } + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE).toTypedArray()
                    )
                    .build()
            )
        }
        // The fresh rows all land on just the primary raw contact — consolidating what may have
        // been split across several back down to one going forward.
        buildContactDataOps(context, input, rawContactId = primaryRawContactId, ops)

        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** A single aggregated [contactId] can have more than one raw contact underneath it (e.g. one
     * from a sync source plus one this app itself created) — each with its own copy of every
     * field. [fetchFullContact] reads across all of them by querying on `CONTACT_ID`, so editing
     * and writing back to only the first raw contact left the others' rows untouched, and every
     * edit-save cycle added one more copy on top without ever clearing the rest — the exact
     * "same number two or three times" bug this fixes. */
    private fun getRawContactIds(context: Context, contactId: String): List<Long> {
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            while (cursor.moveToNext()) ids.add(cursor.getLong(idIndex))
        }
        return ids
    }

    /** Builds every Data-row insert [saveFullContact]/[updateFullContact] need — identical field
     * set either way, only differing in how each row attaches to its raw contact: a fresh
     * back-reference (insert, [rawContactId] null) or a literal existing id (update). */
    private fun buildContactDataOps(context: Context, input: NewContactInput, rawContactId: Long?, ops: MutableList<ContentProviderOperation>) {
        fun ContentProviderOperation.Builder.attachRawContact(): ContentProviderOperation.Builder =
            if (rawContactId != null) withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            else withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)

        val nameOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .attachRawContact()
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        if (input.nameExpanded) {
            if (input.firstName.isNotBlank()) nameOp.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, input.firstName)
            if (input.middleName.isNotBlank()) nameOp.withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, input.middleName)
            if (input.lastName.isNotBlank()) nameOp.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, input.lastName)
        }
        nameOp.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, input.displayName())
        ops.add(nameOp.build())

        input.phones.filter { it.value.isNotBlank() }.forEach { phone ->
            val phoneOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .attachRawContact()
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.value)
                .applyType(
                    ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.LABEL,
                    phone.type, phone.customLabel, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM, ::phoneTypeConstant
                )
            ops.add(phoneOp.build())
        }
        input.emails.filter { it.value.isNotBlank() }.forEach { email ->
            val emailOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .attachRawContact()
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.value)
                .applyType(
                    ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.LABEL,
                    email.type, email.customLabel, ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM, ::emailTypeConstant
                )
            ops.add(emailOp.build())
        }
        input.addresses.filterNot { it.isBlank() }.forEach { address ->
            val postalOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .attachRawContact()
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                .applyType(
                    ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.LABEL,
                    address.type, address.customLabel, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM, ::postalTypeConstant
                )
            if (address.street.isNotBlank()) postalOp.withValue(ContactsContract.CommonDataKinds.StructuredPostal.STREET, address.street)
            if (address.city.isNotBlank()) postalOp.withValue(ContactsContract.CommonDataKinds.StructuredPostal.CITY, address.city)
            if (address.state.isNotBlank()) postalOp.withValue(ContactsContract.CommonDataKinds.StructuredPostal.REGION, address.state)
            if (address.postcode.isNotBlank()) postalOp.withValue(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, address.postcode)
            if (address.country.isNotBlank()) postalOp.withValue(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, address.country)
            postalOp.withValue(
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                listOf(address.street, address.city, address.state, address.postcode, address.country).filter { it.isNotBlank() }.joinToString(", ")
            )
            ops.add(postalOp.build())
        }
        input.importantDates.forEach { date ->
            val eventOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .attachRawContact()
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, formatEventDate(date.dateMillis))
                .applyType(
                    ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.LABEL,
                    date.type, date.customLabel, ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM, ::eventTypeConstant
                )
            ops.add(eventOp.build())
        }
        input.websites.filter { it.value.isNotBlank() }.forEach { website ->
            val websiteOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .attachRawContact()
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Website.URL, website.value)
                .applyType(
                    ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.LABEL,
                    website.type, website.customLabel, ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM, ::websiteTypeConstant
                )
            ops.add(websiteOp.build())
        }
        input.relations.filter { it.value.isNotBlank() }.forEach { relation ->
            val relationOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .attachRawContact()
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Relation.NAME, relation.value)
                .applyType(
                    ContactsContract.CommonDataKinds.Relation.TYPE, ContactsContract.CommonDataKinds.Relation.LABEL,
                    relation.type, relation.customLabel, ContactsContract.CommonDataKinds.Relation.TYPE_CUSTOM, ::relationTypeConstant
                )
            ops.add(relationOp.build())
        }
        if (input.workExpanded && (input.jobTitle.isNotBlank() || input.department.isNotBlank() || input.company.isNotBlank())) {
            val orgOp = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .attachRawContact()
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
            if (input.company.isNotBlank()) orgOp.withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, input.company)
            if (input.jobTitle.isNotBlank()) orgOp.withValue(ContactsContract.CommonDataKinds.Organization.TITLE, input.jobTitle)
            if (input.department.isNotBlank()) orgOp.withValue(ContactsContract.CommonDataKinds.Organization.DEPARTMENT, input.department)
            ops.add(orgOp.build())
        }
        if (input.notes.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .attachRawContact()
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, input.notes)
                    .build()
            )
        }
        val photoBytes = input.photoUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
        }
        if (photoBytes != null) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .attachRawContact()
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                    .build()
            )
        }
    }

    /** Contact Detail's "Edit" action reads the contact's full editable data back out with this —
     * structured/plain name, every phone/email/address/date/website/relation, work info, notes,
     * and the existing photo URI (for preview only; picking a new photo is what actually replaces
     * it, via [NewContactInput.photoUri]) — to pre-fill [NewContactInput]'s exact shape. */
    suspend fun fetchFullContact(context: Context, contactId: String): FullContactData? = withContext(Dispatchers.IO) {
        var displayName = ""
        var givenName = ""
        var middleName = ""
        var familyName = ""
        val phones = mutableListOf<TypedValue>()
        val emails = mutableListOf<TypedValue>()
        val addresses = mutableListOf<AddressValue>()
        val dates = mutableListOf<DateValue>()
        val websites = mutableListOf<TypedValue>()
        val relations = mutableListOf<TypedValue>()
        var jobTitle = ""
        var department = ""
        var company = ""
        var notes = ""

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            "${ContactsContract.Data.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val mimeIndex = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
            while (cursor.moveToNext()) {
                when (cursor.getString(mimeIndex)) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        displayName = cursor.stringOf(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)
                        givenName = cursor.stringOf(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
                        middleName = cursor.stringOf(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME)
                        familyName = cursor.stringOf(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
                    }
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val number = cursor.stringOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (number.isNotBlank()) {
                            val (typeLabel, customLabel) = phoneTypeLabel(
                                cursor.intOf(ContactsContract.CommonDataKinds.Phone.TYPE),
                                cursor.stringOf(ContactsContract.CommonDataKinds.Phone.LABEL)
                            )
                            phones.add(TypedValue(number, typeLabel, customLabel))
                        }
                    }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        val address = cursor.stringOf(ContactsContract.CommonDataKinds.Email.ADDRESS)
                        if (address.isNotBlank()) {
                            val (typeLabel, customLabel) = emailTypeLabel(
                                cursor.intOf(ContactsContract.CommonDataKinds.Email.TYPE),
                                cursor.stringOf(ContactsContract.CommonDataKinds.Email.LABEL)
                            )
                            emails.add(TypedValue(address, typeLabel, customLabel))
                        }
                    }
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        val (typeLabel, customLabel) = postalTypeLabel(
                            cursor.intOf(ContactsContract.CommonDataKinds.StructuredPostal.TYPE),
                            cursor.stringOf(ContactsContract.CommonDataKinds.StructuredPostal.LABEL)
                        )
                        addresses.add(
                            AddressValue(
                                type = typeLabel,
                                street = cursor.stringOf(ContactsContract.CommonDataKinds.StructuredPostal.STREET),
                                city = cursor.stringOf(ContactsContract.CommonDataKinds.StructuredPostal.CITY),
                                state = cursor.stringOf(ContactsContract.CommonDataKinds.StructuredPostal.REGION),
                                postcode = cursor.stringOf(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE),
                                country = cursor.stringOf(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY),
                                customLabel = customLabel
                            )
                        )
                    }
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                        val millis = parseEventDate(cursor.stringOf(ContactsContract.CommonDataKinds.Event.START_DATE))
                        if (millis != null) {
                            val (typeLabel, customLabel) = eventTypeLabel(
                                cursor.intOf(ContactsContract.CommonDataKinds.Event.TYPE),
                                cursor.stringOf(ContactsContract.CommonDataKinds.Event.LABEL)
                            )
                            dates.add(DateValue(typeLabel, millis, customLabel))
                        }
                    }
                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> {
                        val url = cursor.stringOf(ContactsContract.CommonDataKinds.Website.URL)
                        if (url.isNotBlank()) {
                            val (typeLabel, customLabel) = websiteTypeLabel(
                                cursor.intOf(ContactsContract.CommonDataKinds.Website.TYPE),
                                cursor.stringOf(ContactsContract.CommonDataKinds.Website.LABEL)
                            )
                            websites.add(TypedValue(url, typeLabel, customLabel))
                        }
                    }
                    ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE -> {
                        val relationName = cursor.stringOf(ContactsContract.CommonDataKinds.Relation.NAME)
                        if (relationName.isNotBlank()) {
                            val (typeLabel, customLabel) = relationTypeLabel(
                                cursor.intOf(ContactsContract.CommonDataKinds.Relation.TYPE),
                                cursor.stringOf(ContactsContract.CommonDataKinds.Relation.LABEL)
                            )
                            relations.add(TypedValue(relationName, typeLabel, customLabel))
                        }
                    }
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        company = cursor.stringOf(ContactsContract.CommonDataKinds.Organization.COMPANY)
                        jobTitle = cursor.stringOf(ContactsContract.CommonDataKinds.Organization.TITLE)
                        department = cursor.stringOf(ContactsContract.CommonDataKinds.Organization.DEPARTMENT)
                    }
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                        notes = cursor.stringOf(ContactsContract.CommonDataKinds.Note.NOTE)
                    }
                }
            }
        }

        if (displayName.isBlank() && givenName.isBlank() && familyName.isBlank() && phones.isEmpty()) return@withContext null

        val photoUri = context.contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong()),
            arrayOf(ContactsContract.Contacts.PHOTO_URI),
            null, null, null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

        // Android's own ContactsProvider auto-splits DISPLAY_NAME into GIVEN_NAME/MIDDLE_NAME/
        // FAMILY_NAME even when only DISPLAY_NAME was ever written (this app's own collapsed-name
        // save included) — for a 3+ word name it fills in all three, so there's no reliable way to
        // tell "structured entry" apart from "auto-derived split" from the stored parts alone.
        // Always starts collapsed on the combined name; the split parts are still pre-filled in
        // the background so manually expanding shows the right values instead of starting blank.
        val nameExpanded = false
        // Reading across every raw contact under this aggregate (see getRawContactIds's doc) means
        // a contact with more than one raw contact carrying the exact same field — the same "same
        // number 2-3 times" duplication this whole read/write path already had to guard against —
        // surfaces here too, as byte-identical entries. All four value types are plain data classes,
        // so distinct() is exact-duplicate removal, nothing fuzzier.
        FullContactData(
            nameExpanded = nameExpanded,
            singleName = if (nameExpanded) "" else displayName,
            firstName = givenName,
            middleName = middleName,
            lastName = familyName,
            phones = phones.distinct(),
            emails = emails.distinct(),
            addresses = addresses.distinct(),
            importantDates = dates.distinct(),
            websites = websites.distinct(),
            relations = relations.distinct(),
            workExpanded = jobTitle.isNotBlank() || department.isNotBlank() || company.isNotBlank(),
            jobTitle = jobTitle,
            department = department,
            company = company,
            notes = notes,
            photoUri = photoUri
        )
    }

    /** Deletes the given contacts (by CONTACT_ID) from the device's contacts provider — used by
     * the Contacts screen's multi-select "delete" action. */
    suspend fun deleteContacts(context: Context, ids: List<String>): Boolean = withContext(Dispatchers.IO) {
        AnalyticsManager.logEventWithAction(AnalyticsEvents.CONTACT_DELETED, AnalyticsEvents.SCREEN_CONTACTS, AnalyticsEvents.ACTION_SUCCESS, mapOf(AnalyticsEvents.PARAM_COUNT to ids.size))
        try {
            ids.forEach { id ->
                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id.toLong())
                context.contentResolver.delete(uri, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Moves contacts to the Recycle Bin instead of deleting them outright: captures a minimal
     * record (name + number, no photo — see [DeletedContactEntity]'s own doc) for each, then
     * removes them from the system provider via the existing [deleteContacts]. */
    suspend fun moveToRecycleBin(context: Context, contacts: List<Contact>): Boolean = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(context).recycleBinDao()
        contacts.forEach { contact ->
            dao.insert(DeletedContactEntity(id = contact.id, name = contact.name, number = contact.number))
        }
        deleteContacts(context, contacts.map { it.id })
    }

    /** Re-inserts a Recycle Bin entry as a contact — reuses [saveContact], which already does
     * exactly the RawContact+StructuredName+Phone insert this needs. Android assigns a brand-new
     * contact id; the original one isn't preserved (not possible on this platform). */
    suspend fun restoreFromRecycleBin(context: Context, entity: DeletedContactEntity): Boolean {
        val restored = saveContact(context, entity.name, entity.number)
        if (restored) {
            AppDatabase.getInstance(context).recycleBinDao().deletePermanently(listOf(entity.id))
        }
        return restored
    }

    /** Permanently removes Recycle Bin entries without restoring them. */
    suspend fun deleteFromRecycleBinPermanently(context: Context, ids: List<String>) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(context).recycleBinDao().deletePermanently(ids)
    }

    /** Empties the Recycle Bin entirely. */
    suspend fun clearRecycleBin(context: Context) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(context).recycleBinDao().clearAll()
    }

    fun recycleBinFlow(context: Context): Flow<List<DeletedContactEntity>> =
        AppDatabase.getInstance(context).recycleBinDao().getAll()

    /** The Recycle Bin's own "Delete contacts" toggle ("Contact that have been in the bin for
     * more than 30 days will be deleted forever") — on by default. */
    fun isRecycleBinAutoDeleteEnabled(context: Context): Boolean =
        context.getSharedPreferences(RECYCLE_BIN_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_DELETE, true)

    fun setRecycleBinAutoDeleteEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(RECYCLE_BIN_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_DELETE, enabled).apply()
    }

    /** No background job — this runs opportunistically whenever the Recycle Bin screen is opened,
     * which is when the reference app's own 30-day purge would actually be noticed anyway. */
    suspend fun purgeExpiredRecycleBinEntriesIfEnabled(context: Context) = withContext(Dispatchers.IO) {
        if (!isRecycleBinAutoDeleteEnabled(context)) return@withContext
        val threshold = System.currentTimeMillis() - THIRTY_DAYS_MILLIS
        AppDatabase.getInstance(context).recycleBinDao().deleteOlderThan(threshold)
    }

    /** Settings > Import/Export > "Export to file" — writes every contact (name, phones, emails,
     * organization, note) as one combined .vcf to [destinationUri] (picked via
     * `ActivityResultContracts.CreateDocument`). Returns how many contacts were written. */
    suspend fun exportContactsToVcf(context: Context, destinationUri: Uri): Int = withContext(Dispatchers.IO) {
        val names = mutableMapOf<Long, String>()
        val phones = mutableMapOf<Long, MutableList<String>>()
        val emails = mutableMapOf<Long, MutableList<String>>()
        val orgs = mutableMapOf<Long, String>()
        val notes = mutableMapOf<Long, String>()

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
            null, null, null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val mimeIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val dataIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIndex)
                val value = cursor.getString(dataIndex)?.takeIf { it.isNotBlank() } ?: continue
                when (cursor.getString(mimeIndex)) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> names.putIfAbsent(contactId, value)
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> phones.getOrPut(contactId) { mutableListOf() }.add(value)
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> emails.getOrPut(contactId) { mutableListOf() }.add(value)
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> orgs.putIfAbsent(contactId, value)
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> notes.putIfAbsent(contactId, value)
                }
            }
        }

        var count = 0
        val builder = StringBuilder()
        names.forEach { (contactId, name) ->
            builder.append("BEGIN:VCARD\r\n")
            builder.append("VERSION:3.0\r\n")
            builder.append("FN:${escapeVcardValue(name)}\r\n")
            builder.append("N:${escapeVcardValue(name)};;;;\r\n")
            phones[contactId]?.forEach { builder.append("TEL;TYPE=CELL:${escapeVcardValue(it)}\r\n") }
            emails[contactId]?.forEach { builder.append("EMAIL;TYPE=HOME:${escapeVcardValue(it)}\r\n") }
            orgs[contactId]?.let { builder.append("ORG:${escapeVcardValue(it)}\r\n") }
            notes[contactId]?.let { builder.append("NOTE:${escapeVcardValue(it)}\r\n") }
            builder.append("END:VCARD\r\n")
            count++
        }

        try {
            context.contentResolver.openOutputStream(destinationUri)?.use { it.write(builder.toString().toByteArray(Charsets.UTF_8)) }
            count
        } catch (_: Exception) {
            0
        }
    }

    /** Settings > Import/Export > "Import from file" — reads [sourceUri] (picked via
     * `ActivityResultContracts.OpenDocument`), parses each `BEGIN:VCARD`…`END:VCARD` block (name,
     * phones, emails, organization, note), and saves each as a new contact via [saveFullContact].
     * Returns how many contacts were imported. */
    suspend fun importContactsFromVcf(context: Context, sourceUri: Uri): Int = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(sourceUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (_: Exception) {
            null
        } ?: return@withContext 0

        val blocks = Regex("BEGIN:VCARD", RegexOption.IGNORE_CASE).split(text).drop(1)
        var imported = 0
        blocks.forEach { block ->
            var name = ""
            val phones = mutableListOf<String>()
            val emails = mutableListOf<String>()
            var org = ""
            var note = ""
            block.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                val colonIndex = line.indexOf(':')
                if (colonIndex < 0) return@forEach
                val property = line.substring(0, colonIndex).substringBefore(';').uppercase()
                val value = unescapeVcardValue(line.substring(colonIndex + 1))
                when (property) {
                    "FN" -> if (name.isBlank()) name = value
                    "N" -> if (name.isBlank()) name = value.split(";").filter { it.isNotBlank() }.joinToString(" ")
                    "TEL" -> if (value.isNotBlank()) phones.add(value)
                    "EMAIL" -> if (value.isNotBlank()) emails.add(value)
                    "ORG" -> if (org.isBlank()) org = value.replace(";", " ").trim()
                    "NOTE" -> note = value
                }
            }
            if (name.isBlank() && phones.isEmpty()) return@forEach
            val input = NewContactInput(
                nameExpanded = false,
                singleName = name.ifBlank { phones.first() },
                firstName = "",
                middleName = "",
                lastName = "",
                phones = phones.map { TypedValue(it, "Mobile") },
                emails = emails.map { TypedValue(it, "Home") },
                addresses = emptyList(),
                importantDates = emptyList(),
                websites = emptyList(),
                relations = emptyList(),
                workExpanded = org.isNotBlank(),
                jobTitle = "",
                department = "",
                company = org,
                notes = note,
                photoUri = null
            )
            if (saveFullContact(context, input)) imported++
        }
        imported
    }

    /** Reads each contact's real vCard bytes off the system Contacts provider's own `as_vcard`
     * URI and writes them to a genuinely named "<Contact Name>.vcf" file under the cache dir,
     * exposed via [FileProvider] — sharing the provider's `as_vcard` URI directly (the previous
     * approach) shows no file/size/name to most share targets since that URI doesn't implement
     * `OpenableColumns`, so this is what actually gets a real, visible .vcf into the share sheet. */
    suspend fun getVcardUris(context: Context, ids: List<String>): List<Uri> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared_vcards").apply { mkdirs() }
        ids.mapNotNull { id ->
            val contactId = id.toLongOrNull() ?: return@mapNotNull null
            try {
                val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
                val (lookupKey, displayName) = context.contentResolver.query(
                    contactUri,
                    arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null
                } ?: return@mapNotNull null

                val vcardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)
                val bytes = context.contentResolver.openAssetFileDescriptor(vcardUri, "r")?.use { afd ->
                    afd.createInputStream().use { it.readBytes() }
                } ?: return@mapNotNull null

                val safeName = (displayName?.takeIf { it.isNotBlank() } ?: "contact")
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val file = File(dir, "$safeName.vcf")
                file.writeBytes(bytes)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Toggles a contact's starred (favorite) state — used by the contact detail screen's
     * Favorites action. */
    suspend fun setStarred(context: Context, contactId: String, starred: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
            val values = android.content.ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
            }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Sets (or clears, when [ringtoneUri] is null) a contact's custom ringtone — used by the
     * contact detail screen's "Set Ringtone" action. */
    suspend fun setCustomRingtone(context: Context, contactId: String, ringtoneUri: android.net.Uri?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
                val values = android.content.ContentValues().apply {
                    put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri?.toString())
                }
                context.contentResolver.update(uri, values, null, null) > 0
            } catch (_: Exception) {
                false
            }
        }

    /** Reads a contact's custom ringtone. Three distinct results, matching what [setCustomRingtone]
     * writes: null = no override (column is NULL, falls back to the system default), [android.net.Uri.EMPTY]
     * = the contact was explicitly set to ring silently (column is an empty string, not NULL — the
     * two must stay distinguishable or a never-touched contact looks identical to one deliberately
     * silenced), otherwise the stored ringtone Uri. Used by the Set Ringtone screen to preselect
     * the currently active choice. */
    suspend fun getCustomRingtone(context: Context, contactId: String): android.net.Uri? =
        withContext(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.Contacts.CUSTOM_RINGTONE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val raw = cursor.getString(0)
                        when {
                            raw == null -> null
                            raw.isEmpty() -> android.net.Uri.EMPTY
                            else -> android.net.Uri.parse(raw)
                        }
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

    /** Resolves a raw phone number (as Telecom hands it over — no guaranteed formatting) to a
     * matching contact's name/photo, for the call screen. Uses PhoneLookup, which does its own
     * number-normalization matching internally, so exact string formatting doesn't need to match. */
    /** A contact's `PHOTO_URI` can change (a different internal photo-file id) after *any* Data
     * row batch-update on its raw contact — even one that never touches the Photo row itself, like
     * [updateFullContact] editing just the name/phones/etc. — and its `DISPLAY_NAME` obviously
     * changes on an edit too. Screens holding nav-argument name/photoUri captured before such an
     * edit should re-fetch with this (one query, both columns) instead of trusting them forever. */
    suspend fun getContactSummary(context: Context, contactId: String): ContactSummary? = withContext(Dispatchers.IO) {
        val id = contactId.toLongOrNull() ?: return@withContext null
        context.contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id),
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_URI),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContactSummary(name = cursor.getString(0), photoUri = cursor.getString(1)) else null
        }
    }

    suspend fun findContactByNumber(context: Context, number: String): Contact? =
        withContext(Dispatchers.IO) { findContactByNumberNow(context, number) }

    /** Same lookup as [findContactByNumber], but runs on the calling thread — for the call-added path,
     * where the contact must be known before the call screen opens (no first-frame flash of the number). */
    fun findContactByNumberNow(context: Context, number: String): Contact? {
        if (number.isBlank()) return null
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.STARRED
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val starredIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.STARRED)
                    return Contact(
                        id = cursor.getString(idIndex) ?: "",
                        name = cursor.getString(nameIndex) ?: number,
                        number = number,
                        photoUri = cursor.getString(photoIndex),
                        isStarred = starredIndex >= 0 && cursor.getInt(starredIndex) == 1
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted — caller falls back to showing the raw number.
        }
        return null
    }

    /** Reactive — re-queries and re-emits whenever a contact is added/edited/deleted, instead of
     * needing an explicit manual refresh (e.g. on screen resume). */
    fun fetchContacts(context: Context): Flow<List<Contact>> =
        observeContacts(context, favoritesOnly = false)

    /** Same as [fetchContacts] but limited to contacts starred (favorited) in the device's
     * contacts provider. */
    fun fetchFavoriteContacts(context: Context): Flow<List<Contact>> =
        observeContacts(context, favoritesOnly = true)

    private fun observeContacts(context: Context, favoritesOnly: Boolean): Flow<List<Contact>> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(queryContacts(context, favoritesOnly))
            }
        }
        resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        trySend(queryContacts(context, favoritesOnly))
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    private fun queryContacts(context: Context, favoritesOnly: Boolean): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED
        )
        val selection = buildString {
            append("${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} IS NOT NULL")
            if (favoritesOnly) append(" AND ${ContactsContract.CommonDataKinds.Phone.STARRED} = 1")
        }
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val starredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                val lastContactedIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED)
                while (cursor.moveToNext()) {
                    contacts.add(
                        Contact(
                            id = cursor.getString(idIndex) ?: continue,
                            name = cursor.getString(nameIndex) ?: continue,
                            number = cursor.getString(numberIndex) ?: continue,
                            photoUri = cursor.getString(photoIndex),
                            isStarred = cursor.getInt(starredIndex) == 1,
                            lastTimeContacted = cursor.getLong(lastContactedIndex)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted — caller is responsible for checking beforehand.
        }
        // A contact with multiple phone numbers produces one row per number — keep just the first.
        return contacts.distinctBy { it.id }
    }
}
