// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.contacts

import app.projecthaven.data.db.ContactDao
import app.projecthaven.data.db.ContactEntity
import app.projecthaven.domain.sos.PhoneNumbers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class EmergencyContact(val id: Long, val name: String, val phone: String, val isPrimary: Boolean)

class ContactsRepository(
    private val dao: ContactDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val contacts: Flow<List<EmergencyContact>> =
        dao.observeAll().map { list -> list.map { EmergencyContact(it.id, it.name, it.phone, it.isPrimary) } }

    sealed interface AddResult {
        data class Added(val id: Long) : AddResult
        data class Invalid(val reason: String) : AddResult
    }

    suspend fun add(name: String, phone: String): AddResult {
        val normalized = PhoneNumbers.normalize(phone)
        if (!PhoneNumbers.isPlausible(normalized)) return AddResult.Invalid("Enter a valid phone number")
        val existing = dao.all()
        if (existing.any { PhoneNumbers.normalize(it.phone) == normalized }) return AddResult.Invalid("This number is already saved")
        if (existing.size >= MAX_CONTACTS) return AddResult.Invalid("You can save up to $MAX_CONTACTS contacts")
        val displayName = name.trim().ifEmpty { normalized }.take(80)
        val id = dao.insert(ContactEntity(name = displayName, phone = normalized, isPrimary = existing.isEmpty(), createdAt = clock()))
        return AddResult.Added(id)
    }

    suspend fun rename(contact: EmergencyContact, name: String) {
        dao.update(ContactEntity(contact.id, name.trim().ifEmpty { contact.phone }.take(80), contact.phone, contact.isPrimary,
            dao.all().firstOrNull { it.id == contact.id }?.createdAt ?: clock()))
    }

    suspend fun delete(id: Long) {
        val wasPrimary = dao.all().firstOrNull { it.id == id }?.isPrimary == true
        dao.delete(id)
        if (wasPrimary) dao.all().firstOrNull()?.let { dao.makePrimary(it.id) }
    }

    suspend fun makePrimary(id: Long) = dao.makePrimary(id)

    companion object {
        const val MAX_CONTACTS = 10
    }
}
