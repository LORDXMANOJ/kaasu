package com.manoj.kaasu.data.repo

import com.manoj.kaasu.core.Dates
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.db.ContactDao
import com.manoj.kaasu.data.db.PaymentDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val dao: ContactDao,
    private val paymentDao: PaymentDao,
) {
    val active: Flow<List<Contact>> = dao.observeActive()
    val all: Flow<List<Contact>> = dao.observeAll()

    fun observeById(id: Long): Flow<Contact?> = dao.observeById(id)
    suspend fun getById(id: Long): Contact? = dao.getById(id)
    suspend fun findByVpa(vpa: String): Contact? = dao.findByVpa(vpa)

    suspend fun save(id: Long, name: String, vpa: String?, phone: String?): Long {
        val cleanVpa = vpa?.trim()?.takeIf { it.isNotEmpty() }
        val cleanPhone = phone?.filter { it.isDigit() || it == '+' }?.takeIf { it.isNotEmpty() }
        if (id == 0L) {
            return dao.insert(Contact(name = name.trim(), vpa = cleanVpa, phone = cleanPhone, createdAt = Dates.now()))
        }
        val existing = dao.getById(id) ?: return 0
        dao.update(existing.copy(name = name.trim(), vpa = cleanVpa, phone = cleanPhone, archived = false))
        return id
    }

    /** Keeps their payment history but hides the person. */
    suspend fun archive(contact: Contact) = dao.update(contact.copy(archived = true))

    suspend fun deleteCompletely(contact: Contact) {
        paymentDao.clearContact(contact.id)
        dao.delete(contact)
    }
}
