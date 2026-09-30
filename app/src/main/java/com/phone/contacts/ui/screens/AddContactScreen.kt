package com.phone.contacts.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phone.contacts.data.AddressValue
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.data.DateValue
import com.phone.contacts.data.NewContactInput
import com.phone.contacts.data.TypedValue
import com.phone.contacts.util.RecentlyAddedContacts
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val PHONE_TYPES = listOf("Mobile", "Home", "Work", "Other", "Custom")
private val GENERIC_TYPES = listOf("Home", "Work", "Other", "Custom")
private val DATE_TYPES = listOf("Birthday", "Anniversary", "Other", "Custom")

/** Common shape for every entry-list item below, so a single generic [replaceWith] can update any
 * of them — separate non-generic overloads per concrete type all erase to the same JVM signature
 * (a "platform declaration clash"), since generics and the `(T) -> T` lambda type disappear at
 * the bytecode level. */
private interface HasId { val id: Long }

private data class TypedEntry(override val id: Long, val value: String, val type: String, val customLabel: String = "") : HasId
private data class AddressEntryState(
    override val id: Long,
    val type: String = "Home",
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val postcode: String = "",
    val country: String = "",
    val customLabel: String = ""
) : HasId
private data class DateEntryState(
    override val id: Long,
    val type: String = "Birthday",
    val dateMillis: Long? = null,
    val customLabel: String = ""
) : HasId

/** A plain (untyped) multi-value entry — used for Relation, which has no Home/Work/Other picker
 * in the reference app. */
private data class MultiEntry(override val id: Long, val value: String) : HasId

@Composable
fun AddContactScreen(onClose: (saved: Boolean) -> Unit, initialPhone: String = "", editContactId: String? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isEditMode = editContactId != null

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    // The contact's photo as it already exists, before any new pick — shown as the preview until
    // (and unless) the user picks a replacement, which is what [photoUri] then holds instead.
    var existingPhotoUri by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photoUri = uri
    }

    // Defaults collapsed (single "Name" field) for both a fresh Add and an Edit prefill — matches
    // the reference app's own screen, which opens with the combined field, not pre-expanded.
    var nameExpanded by remember { mutableStateOf(false) }
    var singleName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }

    var nextEntryId by remember { mutableStateOf(1L) }
    fun newId(): Long { val id = nextEntryId; nextEntryId += 1; return id }

    val phones = remember {
        mutableStateListOf<TypedEntry>().apply {
            if (initialPhone.isNotBlank()) add(TypedEntry(0L, initialPhone, "Mobile"))
        }
    }
    val emails = remember { mutableStateListOf<TypedEntry>() }
    val websites = remember { mutableStateListOf<TypedEntry>() }
    val addresses = remember { mutableStateListOf<AddressEntryState>() }
    val importantDates = remember { mutableStateListOf<DateEntryState>() }
    val relations = remember { mutableStateListOf<MultiEntry>() }

    var workExpanded by remember { mutableStateOf(false) }
    var jobTitle by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }

    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // Pre-fills every field from the existing contact — same screen as Add, just started full
    // instead of empty, matching the reference app's own reuse of this one screen for both.
    LaunchedEffect(editContactId) {
        if (editContactId == null) return@LaunchedEffect
        val existing = ContactRepository.fetchFullContact(context, editContactId) ?: return@LaunchedEffect
        nameExpanded = existing.nameExpanded
        singleName = existing.singleName
        firstName = existing.firstName
        middleName = existing.middleName
        lastName = existing.lastName
        phones.clear()
        phones.addAll(existing.phones.map { TypedEntry(newId(), it.value, it.type, it.customLabel) })
        emails.clear()
        emails.addAll(existing.emails.map { TypedEntry(newId(), it.value, it.type, it.customLabel) })
        websites.clear()
        websites.addAll(existing.websites.map { TypedEntry(newId(), it.value, it.type, it.customLabel) })
        addresses.clear()
        addresses.addAll(
            existing.addresses.map {
                AddressEntryState(newId(), it.type, it.street, it.city, it.state, it.postcode, it.country, it.customLabel)
            }
        )
        importantDates.clear()
        importantDates.addAll(existing.importantDates.map { DateEntryState(newId(), it.type, it.dateMillis, it.customLabel) })
        relations.clear()
        relations.addAll(existing.relations.map { MultiEntry(newId(), it) })
        workExpanded = existing.workExpanded
        jobTitle = existing.jobTitle
        department = existing.department
        company = existing.company
        notes = existing.notes
        existingPhotoUri = existing.photoUri
    }

    val effectiveName = if (nameExpanded) {
        listOf(firstName, middleName, lastName).map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")
    } else {
        singleName.trim()
    }
    val canSave = effectiveName.isNotBlank() && phones.any { it.value.isNotBlank() } && !isSaving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onClose(false) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = if (isEditMode) "Edit contact" else "Add contact",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            Button(
                enabled = canSave,
                onClick = {
                    isSaving = true
                    coroutineScope.launch {
                        val input = NewContactInput(
                            nameExpanded = nameExpanded,
                            singleName = singleName,
                            firstName = firstName,
                            middleName = middleName,
                            lastName = lastName,
                            phones = phones.map { TypedValue(it.value, it.type, it.customLabel) },
                            emails = emails.map { TypedValue(it.value, it.type, it.customLabel) },
                            addresses = addresses.map {
                                AddressValue(it.type, it.street, it.city, it.state, it.postcode, it.country, it.customLabel)
                            },
                            importantDates = importantDates.mapNotNull { entry ->
                                entry.dateMillis?.let { DateValue(entry.type, it, entry.customLabel) }
                            },
                            websites = websites.map { TypedValue(it.value, it.type, it.customLabel) },
                            relations = relations.map { it.value },
                            workExpanded = workExpanded,
                            jobTitle = jobTitle,
                            department = department,
                            company = company,
                            notes = notes,
                            photoUri = photoUri
                        )
                        val success = if (isEditMode) {
                            ContactRepository.updateFullContact(context, editContactId!!, input)
                        } else {
                            ContactRepository.saveFullContact(context, input)
                        }
                        if (success && !isEditMode) {
                            phones.firstOrNull { it.value.isNotBlank() }?.let {
                                RecentlyAddedContacts.recordAdd(context, it.value.trim())
                            }
                        }
                        isSaving = false
                        onClose(success)
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValuesHorizontal
            ) {
                Text(text = "Save", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable { photoPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val previewPhoto = photoUri ?: existingPhotoUri
                        if (previewPhoto != null) {
                            AsyncImage(
                                model = previewPhoto,
                                contentDescription = "Contact image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AddAPhoto,
                                contentDescription = "Contact image",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Text(
                        text = "Add picture",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clickable { photoPicker.launch("image/*") }
                    )
                }
            }

            item {
                ExpandableNameSection(
                    expanded = nameExpanded,
                    onExpandToggle = {
                        if (!nameExpanded) {
                            // Seed the structured fields from whatever was already typed in the
                            // single field, so expanding doesn't silently drop it.
                            if (firstName.isBlank() && middleName.isBlank() && lastName.isBlank() && singleName.isNotBlank()) {
                                val parts = singleName.trim().split(Regex("\\s+"))
                                firstName = parts.first()
                                lastName = if (parts.size > 1) parts.last() else ""
                                middleName = if (parts.size > 2) parts.subList(1, parts.size - 1).joinToString(" ") else ""
                            }
                        } else {
                            singleName = effectiveName
                        }
                        nameExpanded = !nameExpanded
                    },
                    singleName = singleName,
                    onSingleNameChange = { singleName = it },
                    firstName = firstName,
                    onFirstNameChange = { firstName = it },
                    middleName = middleName,
                    onMiddleNameChange = { middleName = it },
                    lastName = lastName,
                    onLastNameChange = { lastName = it }
                )
            }

            item {
                SectionCard(
                    icon = Icons.Filled.Phone,
                    emptyLabel = "Phone",
                    addMoreLabel = "Add Phone Number",
                    entries = phones,
                    onAdd = { phones.add(TypedEntry(newId(), "", "Mobile")) }
                ) { entry ->
                    TypedEntryRow(
                        icon = Icons.Filled.Phone,
                        valueHint = "Phone",
                        entry = entry,
                        typeOptions = PHONE_TYPES,
                        onValueChange = { phones.replaceWith(entry.id) { e -> e.copy(value = it) } },
                        onTypeChange = { phones.replaceWith(entry.id) { e -> e.copy(type = it) } },
                        onCustomLabelChange = { phones.replaceWith(entry.id) { e -> e.copy(customLabel = it) } },
                        onRemove = { phones.removeAll { it.id == entry.id } }
                    )
                }
            }
            item {
                SectionCard(
                    icon = Icons.Filled.Email,
                    emptyLabel = "Email",
                    addMoreLabel = "Add Email",
                    entries = emails,
                    onAdd = { emails.add(TypedEntry(newId(), "", "Home")) }
                ) { entry ->
                    TypedEntryRow(
                        icon = Icons.Filled.Email,
                        valueHint = "Email",
                        entry = entry,
                        typeOptions = GENERIC_TYPES,
                        onValueChange = { emails.replaceWith(entry.id) { e -> e.copy(value = it) } },
                        onTypeChange = { emails.replaceWith(entry.id) { e -> e.copy(type = it) } },
                        onCustomLabelChange = { emails.replaceWith(entry.id) { e -> e.copy(customLabel = it) } },
                        onRemove = { emails.removeAll { it.id == entry.id } }
                    )
                }
            }

            item {
                SectionCard(
                    icon = Icons.Filled.LocationOn,
                    emptyLabel = "Address",
                    addMoreLabel = "Add Address",
                    entries = addresses,
                    onAdd = { addresses.add(AddressEntryState(newId())) }
                ) { entry ->
                    AddressEntryRow(
                        entry = entry,
                        onFieldChange = { updated -> addresses.replaceWith(entry.id) { updated } },
                        onRemove = { addresses.removeAll { it.id == entry.id } }
                    )
                }
            }

            item {
                SectionCard(
                    icon = Icons.Outlined.CalendarToday,
                    emptyLabel = "Important dates",
                    addMoreLabel = "Add date",
                    entries = importantDates,
                    onAdd = { importantDates.add(DateEntryState(newId())) }
                ) { entry ->
                    DateEntryRow(
                        entry = entry,
                        onDateChange = { millis -> importantDates.replaceWith(entry.id) { it.copy(dateMillis = millis) } },
                        onTypeChange = { importantDates.replaceWith(entry.id) { e -> e.copy(type = it) } },
                        onCustomLabelChange = { importantDates.replaceWith(entry.id) { e -> e.copy(customLabel = it) } },
                        onRemove = { importantDates.removeAll { it.id == entry.id } }
                    )
                }
            }

            item {
                SectionCard(
                    icon = Icons.Outlined.Language,
                    emptyLabel = "Website",
                    addMoreLabel = "Add website",
                    entries = websites,
                    onAdd = { websites.add(TypedEntry(newId(), "", "Home")) }
                ) { entry ->
                    TypedEntryRow(
                        icon = Icons.Outlined.Language,
                        valueHint = "Website",
                        entry = entry,
                        typeOptions = GENERIC_TYPES,
                        onValueChange = { websites.replaceWith(entry.id) { e -> e.copy(value = it) } },
                        onTypeChange = { websites.replaceWith(entry.id) { e -> e.copy(type = it) } },
                        onCustomLabelChange = { websites.replaceWith(entry.id) { e -> e.copy(customLabel = it) } },
                        onRemove = { websites.removeAll { it.id == entry.id } }
                    )
                }
            }

            item {
                SectionCard(
                    icon = Icons.Outlined.FavoriteBorder,
                    emptyLabel = "Relation",
                    addMoreLabel = "Add Relation",
                    entries = relations,
                    onAdd = { relations.add(MultiEntry(newId(), "")) }
                ) { entry ->
                    PlainEntryRow(
                        icon = Icons.Outlined.FavoriteBorder,
                        valueHint = "Relation",
                        value = entry.value,
                        onValueChange = { relations.replaceWith(entry.id) { e -> e.copy(value = it) } },
                        onRemove = { relations.removeAll { it.id == entry.id } }
                    )
                }
            }

            item {
                ExpandableWorkSection(
                    expanded = workExpanded,
                    onExpandToggle = { workExpanded = !workExpanded },
                    jobTitle = jobTitle,
                    onJobTitleChange = { jobTitle = it },
                    department = department,
                    onDepartmentChange = { department = it },
                    company = company,
                    onCompanyChange = { company = it }
                )
            }

            item {
                ContactField(icon = Icons.Filled.Notes, placeholder = "Notes", value = notes, onValueChange = { notes = it })
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

private inline fun <T : HasId> SnapshotStateList<T>.replaceWith(id: Long, transform: (T) -> T) {
    val index = indexOfFirst { it.id == id }
    if (index >= 0) this[index] = transform(this[index])
}

/** An icon's tint follows the reference app exactly: neutral (same as surrounding text) on a
 * static "add" row or an untouched field, and only switches to the primary color once that
 * row is actually active — an expanded Name/Work info section, or an entry that has been added. */
@Composable
private fun FieldIcon(icon: ImageVector, active: Boolean = false, modifier: Modifier = Modifier) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(22.dp)
    )
}

/** The red outlined circle-minus used to remove an entry — matches the reference app's own
 * remove button exactly (it's red, not a plain neutral "x"). */
@Composable
private fun RemoveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Outlined.RemoveCircleOutline,
            contentDescription = "Remove",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** The type picker shown on every typed entry — a "Home ▾" label that opens a dropdown of
 * [options]. Selecting "Custom" (matching the reference app's own picker) swaps the label for an
 * inline editable text field with a pencil icon, since "Custom" has no fixed name of its own. */
@Composable
private fun TypeLabel(
    type: String,
    customLabel: String,
    options: List<String>,
    onTypeChange: (String) -> Unit,
    onCustomLabelChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        if (type == "Custom") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    if (customLabel.isEmpty()) {
                        Text(
                            text = "Custom",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    BasicTextField(
                        value = customLabel,
                        onValueChange = onCustomLabelChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Change type",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.clickable { expanded = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = type,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Change type",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    trailingIcon = if (option == "Custom") {
                        { Icon(imageVector = Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        onTypeChange(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun ExpandableNameSection(
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    singleName: String,
    onSingleNameChange: (String) -> Unit,
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    middleName: String,
    onMiddleNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            FieldIcon(icon = Icons.Filled.Person, active = expanded, modifier = Modifier.padding(top = 3.dp))
            Spacer(modifier = Modifier.size(14.dp))
            if (expanded) {
                Column(modifier = Modifier.weight(1f)) {
                    PlainInlineField(placeholder = "First name", value = firstName, onValueChange = onFirstNameChange)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    PlainInlineField(placeholder = "Middle name", value = middleName, onValueChange = onMiddleNameChange)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    PlainInlineField(placeholder = "Last name", value = lastName, onValueChange = onLastNameChange)
                }
            } else {
                PlainInlineField(
                    placeholder = "Name",
                    value = singleName,
                    onValueChange = onSingleNameChange,
                    modifier = Modifier.weight(1f),
                    verticalPadding = 0.dp
                )
            }
            IconButton(
                onClick = onExpandToggle,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(24.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse name" else "Expand name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExpandableWorkSection(
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    jobTitle: String,
    onJobTitleChange: (String) -> Unit,
    department: String,
    onDepartmentChange: (String) -> Unit,
    company: String,
    onCompanyChange: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !expanded, onClick = onExpandToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            FieldIcon(icon = Icons.Filled.Work, active = expanded, modifier = Modifier.padding(top = 3.dp))
            Spacer(modifier = Modifier.size(14.dp))
            if (expanded) {
                Column(modifier = Modifier.weight(1f)) {
                    PlainInlineField(placeholder = "Job title", value = jobTitle, onValueChange = onJobTitleChange)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    PlainInlineField(placeholder = "Department", value = department, onValueChange = onDepartmentChange)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    PlainInlineField(placeholder = "Company", value = company, onValueChange = onCompanyChange)
                }
            } else {
                Text(
                    text = "Work info",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(
                onClick = onExpandToggle,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(24.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse work info" else "Expand work info",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** One "Phone"/"Email"/"Address"/"Important dates"/"Website"/"Relation" section — matches the
 * reference app's own card exactly: a single rounded card that's either just the empty-state
 * "Add X" row (no entries yet), or every current entry (each row content supplied by
 * [entryContent]) followed by a plus-icon "Add more" row, all inside that one card — never a
 * separate standalone placeholder box sitting above/beside the entries. */
@Composable
private fun <T : HasId> SectionCard(
    icon: ImageVector,
    emptyLabel: String,
    addMoreLabel: String,
    entries: List<T>,
    onAdd: () -> Unit,
    entryContent: @Composable (T) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column {
            if (entries.isEmpty()) {
                CardActionRow(icon = icon, label = emptyLabel, iconActive = false, onClick = onAdd)
            } else {
                entries.forEach { entry ->
                    entryContent(entry)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
                CardActionRow(icon = Icons.Filled.Add, label = addMoreLabel, iconActive = false, onClick = onAdd)
            }
        }
    }
}

@Composable
private fun CardActionRow(icon: ImageVector, label: String, iconActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FieldIcon(icon = icon, active = iconActive)
        Spacer(modifier = Modifier.size(14.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TypedEntryRow(
    icon: ImageVector,
    valueHint: String,
    entry: TypedEntry,
    typeOptions: List<String>,
    onValueChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onCustomLabelChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldIcon(icon = icon, active = true)
            Spacer(modifier = Modifier.size(14.dp))
            Box(modifier = Modifier.weight(1f)) {
                TypeLabel(
                    type = entry.type,
                    customLabel = entry.customLabel,
                    options = typeOptions,
                    onTypeChange = onTypeChange,
                    onCustomLabelChange = onCustomLabelChange
                )
            }
            RemoveButton(onClick = onRemove)
        }
        PlainInlineField(
            placeholder = valueHint,
            value = entry.value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp)
        )
    }
}

/** Relation's row — same shape as [TypedEntryRow] but without a type picker, since the reference
 * app's Add Contact screen has no Home/Work/Other choice for Relation. */
@Composable
private fun PlainEntryRow(
    icon: ImageVector,
    valueHint: String,
    value: String,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FieldIcon(icon = icon, active = true)
        Spacer(modifier = Modifier.size(14.dp))
        PlainInlineField(placeholder = valueHint, value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f))
        RemoveButton(onClick = onRemove)
    }
}

/** Address's row — the reference app splits an address into five structured fields (Street/
 * City/State/Postcode/Country) rather than one free-text line. */
@Composable
private fun AddressEntryRow(
    entry: AddressEntryState,
    onFieldChange: (AddressEntryState) -> Unit,
    onRemove: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldIcon(icon = Icons.Filled.LocationOn, active = true)
            Spacer(modifier = Modifier.size(14.dp))
            Box(modifier = Modifier.weight(1f)) {
                TypeLabel(
                    type = entry.type,
                    customLabel = entry.customLabel,
                    options = GENERIC_TYPES,
                    onTypeChange = { onFieldChange(entry.copy(type = it)) },
                    onCustomLabelChange = { onFieldChange(entry.copy(customLabel = it)) }
                )
            }
            RemoveButton(onClick = onRemove)
        }
        Column(modifier = Modifier.padding(start = 36.dp)) {
            PlainInlineField(placeholder = "Street", value = entry.street, onValueChange = { onFieldChange(entry.copy(street = it)) })
            PlainInlineField(placeholder = "City", value = entry.city, onValueChange = { onFieldChange(entry.copy(city = it)) })
            PlainInlineField(placeholder = "State", value = entry.state, onValueChange = { onFieldChange(entry.copy(state = it)) })
            PlainInlineField(placeholder = "Postcode", value = entry.postcode, onValueChange = { onFieldChange(entry.copy(postcode = it)) })
            PlainInlineField(placeholder = "Country", value = entry.country, onValueChange = { onFieldChange(entry.copy(country = it)) })
        }
    }
}

/** Important dates' row — the reference app opens the native date picker rather than typing a
 * date as free text. */
@Composable
private fun DateEntryRow(
    entry: DateEntryState,
    onDateChange: (Long) -> Unit,
    onTypeChange: (String) -> Unit,
    onCustomLabelChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldIcon(icon = Icons.Outlined.CalendarToday, active = true)
            Spacer(modifier = Modifier.size(14.dp))
            Box(modifier = Modifier.weight(1f)) {
                TypeLabel(
                    type = entry.type,
                    customLabel = entry.customLabel,
                    options = DATE_TYPES,
                    onTypeChange = onTypeChange,
                    onCustomLabelChange = onCustomLabelChange
                )
            }
            RemoveButton(onClick = onRemove)
        }
        Text(
            text = entry.dateMillis?.let { formatPickedDate(it) } ?: "Select date",
            fontSize = 15.sp,
            color = if (entry.dateMillis != null) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, top = 4.dp, bottom = 4.dp)
                .clickable {
                    val cal = Calendar.getInstance().apply { entry.dateMillis?.let { timeInMillis = it } }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val picked = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }.timeInMillis
                            onDateChange(picked)
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
        )
    }
}

private fun formatPickedDate(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(millis)

@Composable
private fun PlainInlineField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 4.dp
) {
    Box(modifier = modifier.padding(vertical = verticalPadding)) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val PaddingValuesHorizontal = PaddingValues(horizontal = 20.dp, vertical = 8.dp)

@Composable
private fun ContactField(
    icon: ImageVector,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FieldIcon(icon = icon, active = false)
            Spacer(modifier = Modifier.size(14.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
