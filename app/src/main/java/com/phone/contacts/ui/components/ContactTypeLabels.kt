package com.phone.contacts.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.phone.contacts.R

/** Shows a contact field type in the user's language. The stored value (what gets saved to the
 * phone, and what the form compares against) stays the English key - only the label is translated. */
@Composable
fun contactTypeLabel(type: String): String = when (type) {
    "Mobile" -> stringResource(R.string.type_mobile)
    "Home" -> stringResource(R.string.type_home)
    "Work" -> stringResource(R.string.type_work)
    "Other" -> stringResource(R.string.type_other)
    "Custom" -> stringResource(R.string.custom_type_fallback)
    "Birthday" -> stringResource(R.string.type_birthday)
    "Anniversary" -> stringResource(R.string.type_anniversary)
    "Assistant" -> stringResource(R.string.rel_assistant)
    "Brother" -> stringResource(R.string.rel_brother)
    "Child" -> stringResource(R.string.rel_child)
    "Domestic Partner" -> stringResource(R.string.rel_domestic_partner)
    "Father" -> stringResource(R.string.rel_father)
    "Friend" -> stringResource(R.string.rel_friend)
    "Manager" -> stringResource(R.string.rel_manager)
    "Mother" -> stringResource(R.string.rel_mother)
    "Parent" -> stringResource(R.string.rel_parent)
    "Partner" -> stringResource(R.string.rel_partner)
    "Referred by" -> stringResource(R.string.rel_referred_by)
    "Relative" -> stringResource(R.string.rel_relative)
    "Sister" -> stringResource(R.string.rel_sister)
    "Spouse" -> stringResource(R.string.rel_spouse)
    else -> type
}
