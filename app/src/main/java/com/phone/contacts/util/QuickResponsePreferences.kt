package com.phone.contacts.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf

private const val PREFS_NAME = "quick_response_prefs"
private const val KEY_TEMPLATES = "templates"

/** [isDefault] templates (seeded on first run) can be edited but never deleted — only a
 * user-added one (via the "+" button) can be removed, matching the reference app's own
 * Quick response screen (only the custom entry there has a trash icon). */
data class QuickResponseTemplate(val id: String, val text: String, val isDefault: Boolean)

private val seedTemplates = listOf(
    "Can't talk now. What's up?",
    "I'll call you right back.",
    "I'll call you later.",
    "Can't talk now. Call me later?"
)

private fun encode(templates: List<QuickResponseTemplate>): String =
    templates.joinToString("\n") { "${it.id}|${it.isDefault}|${it.text}" }

private fun decode(raw: String): List<QuickResponseTemplate> =
    raw.split("\n").mapNotNull { line ->
        val parts = line.split("|", limit = 3)
        if (parts.size != 3) return@mapNotNull null
        QuickResponseTemplate(id = parts[0], isDefault = parts[1].toBoolean(), text = parts[2])
    }

/** The editable list of quick-reply templates shown when declining an incoming call with a text —
 * SharedPreferences-backed, same lightweight pattern as the other small preference objects in this
 * app (a handful of short strings, not worth a Room table). */
object QuickResponsePreferences {
    val templates = mutableStateOf<List<QuickResponseTemplate>>(emptyList())

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TEMPLATES, null)
        templates.value = if (raw == null) {
            val seeded = seedTemplates.mapIndexed { index, text -> QuickResponseTemplate("default_$index", text, true) }
            persist(context, seeded)
            seeded
        } else {
            decode(raw)
        }
    }

    fun addTemplate(context: Context, text: String) {
        if (text.isBlank()) return
        persist(context, templates.value + QuickResponseTemplate("custom_${System.currentTimeMillis()}", text, false))
    }

    fun updateTemplate(context: Context, id: String, newText: String) {
        if (newText.isBlank()) return
        persist(context, templates.value.map { if (it.id == id) it.copy(text = newText) else it })
    }

    fun deleteTemplate(context: Context, id: String) {
        persist(context, templates.value.filter { it.id != id })
    }

    private fun persist(context: Context, list: List<QuickResponseTemplate>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEMPLATES, encode(list))
            .apply()
        templates.value = list
    }
}
