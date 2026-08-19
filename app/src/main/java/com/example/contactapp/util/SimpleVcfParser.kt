package com.example.contactapp.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class VCardContact(
    var fullName: String? = null,
    val phoneNumbers: MutableList<String> = mutableListOf(),
    val emails: MutableList<String> = mutableListOf(),
    var organization: String? = null
)

class SimpleVcfParser {

    fun parse(inputStream: InputStream): List<VCardContact> {
        val contacts = mutableListOf<VCardContact>()
        var currentContact: VCardContact? = null

        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = mutableListOf<String>()
        
        try {
            reader.forEachLine { line ->
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    if (lines.isNotEmpty()) {
                        val lastIndex = lines.size - 1
                        lines[lastIndex] = lines[lastIndex] + line.substring(1)
                    }
                } else {
                    lines.add(line)
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }

        for (line in lines) {
            when {
                line.startsWith("BEGIN:VCARD", ignoreCase = true) -> {
                    currentContact = VCardContact()
                }
                line.startsWith("END:VCARD", ignoreCase = true) -> {
                    currentContact?.let { contacts.add(it) }
                    currentContact = null
                }
                else -> {
                    parseLine(line, currentContact)
                }
            }
        }
        return contacts
    }

    private fun parseLine(line: String, contact: VCardContact?) {
        if (contact == null) return

        val parts = line.split(":", limit = 2)
        if (parts.size < 2) return

        val fullKey = parts[0].uppercase()
        val value = parts[1].trim()

        when {
            fullKey.startsWith("FN") -> contact.fullName = value
            fullKey.startsWith("TEL") -> {
                // Basic cleanup of common VCF number noise
                val cleanPhone = value.replace(Regex("[^0-9+]"), "")
                if (cleanPhone.isNotEmpty()) {
                    contact.phoneNumbers.add(cleanPhone)
                }
            }
            fullKey.startsWith("EMAIL") -> contact.emails.add(value)
            fullKey.startsWith("ORG") -> contact.organization = value
        }
    }
}
