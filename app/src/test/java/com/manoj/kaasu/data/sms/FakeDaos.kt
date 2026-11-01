package com.manoj.kaasu.data.sms

import com.manoj.kaasu.data.db.Category
import com.manoj.kaasu.data.db.CategoryDao
import com.manoj.kaasu.data.db.CategoryKind
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.db.ContactDao
import com.manoj.kaasu.data.db.Direction
import com.manoj.kaasu.data.db.Payment
import com.manoj.kaasu.data.db.PaymentDao
import com.manoj.kaasu.data.db.PaymentStatus
import com.manoj.kaasu.data.db.SmsEvent
import com.manoj.kaasu.data.db.SmsEventDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-ins for the Room DAOs, mirroring their SQL. */
class FakePaymentDao : PaymentDao {
    val rows = MutableStateFlow<List<Payment>>(emptyList())
    private var next = 1L

    override fun observeAll(): Flow<List<Payment>> = rows.map { list -> list.sortedByDescending { it.createdAt } }
    override fun observeBetween(from: Long, to: Long) = rows.map { list -> list.filter { it.createdAt in from..to } }
    override suspend fun getBetween(from: Long, to: Long) = rows.value.filter { it.createdAt in from..to }
    override fun observeById(id: Long) = rows.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }
    override fun observeByContact(contactId: Long) = rows.map { list -> list.filter { it.contactId == contactId } }
    override fun observeWithContact() = rows.map { list -> list.filter { it.contactId != null } }
    override fun observeUntagged(since: Long) = rows.map { list ->
        list.filter { it.direction == Direction.OUT && it.categoryId == null && it.status != PaymentStatus.FAILED && it.createdAt >= since }
    }
    override suspend fun findUnlinked(direction: Direction, amount: Long, from: Long, to: Long) =
        rows.value.filter { it.direction == direction && it.amountPaise == amount && it.createdAt in from..to && it.smsEventId == null }
    override suspend fun findByRef(ref: String) = rows.value.firstOrNull { it.bankRef == ref || it.upiTxnId == ref }
    override suspend fun getPendingOlderThan(before: Long) = rows.value.filter { it.status == PaymentStatus.PENDING && it.createdAt < before }
    override suspend fun getAll() = rows.value
    override suspend fun insert(payment: Payment): Long {
        val id = if (payment.id == 0L) next++ else payment.id
        rows.value = rows.value + payment.copy(id = id)
        return id
    }
    override suspend fun insertAll(payments: List<Payment>) = payments.forEach { insert(it) }
    override suspend fun update(payment: Payment) {
        rows.value = rows.value.map { if (it.id == payment.id) payment else it }
    }
    override suspend fun delete(payment: Payment) {
        rows.value = rows.value.filterNot { it.id == payment.id }
    }
    override suspend fun clearCategory(categoryId: Long) {
        rows.value = rows.value.map { if (it.categoryId == categoryId) it.copy(categoryId = null) else it }
    }
    override suspend fun clearContact(contactId: Long) {
        rows.value = rows.value.map { if (it.contactId == contactId) it.copy(contactId = null) else it }
    }
    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

class FakeSmsEventDao : SmsEventDao {
    val rows = MutableStateFlow<List<SmsEvent>>(emptyList())
    private var next = 1L

    override suspend fun insert(event: SmsEvent): Long {
        if (rows.value.any { it.providerId == event.providerId }) return -1
        val id = next++
        rows.value = rows.value + event.copy(id = id)
        return id
    }
    override suspend fun insertAll(events: List<SmsEvent>) = events.forEach { insert(it) }
    override suspend fun update(event: SmsEvent) {
        rows.value = rows.value.map { if (it.id == event.id) event else it }
    }
    override fun observeLatestWithBalance() = rows.map { list -> list.filter { it.balancePaise != null }.maxByOrNull { it.receivedAt } }
    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun getAll() = rows.value
    override fun observeCount() = rows.map { it.size }
    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

class FakeContactDao(initial: List<Contact> = emptyList()) : ContactDao {
    val rows = MutableStateFlow(initial)
    override fun observeActive() = rows.map { list -> list.filter { !it.archived } }
    override fun observeAll() = rows
    override fun observeById(id: Long) = rows.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun getActive() = rows.value.filter { !it.archived }
    override suspend fun findByVpa(vpa: String) = rows.value.firstOrNull { it.vpa.equals(vpa, ignoreCase = true) }
    override suspend fun getAll() = rows.value
    override suspend fun insert(contact: Contact): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
        rows.value = rows.value + contact.copy(id = id)
        return id
    }
    override suspend fun insertAll(contacts: List<Contact>) = contacts.forEach { insert(it) }
    override suspend fun update(contact: Contact) {
        rows.value = rows.value.map { if (it.id == contact.id) contact else it }
    }
    override suspend fun delete(contact: Contact) {
        rows.value = rows.value.filterNot { it.id == contact.id }
    }
    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

class FakeCategoryDao(initial: List<Category>) : CategoryDao {
    val rows = MutableStateFlow(initial)
    override fun observeAll() = rows
    override suspend fun getAll() = rows.value
    override suspend fun firstOfKind(kind: CategoryKind) = rows.value.filter { it.kind == kind && !it.archived }.minByOrNull { it.sortOrder }
    override suspend fun count() = rows.value.size
    override suspend fun insert(category: Category): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
        rows.value = rows.value + category.copy(id = id)
        return id
    }
    override suspend fun insertAll(categories: List<Category>) = categories.forEach { insert(it) }
    override suspend fun update(category: Category) {
        rows.value = rows.value.map { if (it.id == category.id) category else it }
    }
    override suspend fun updateAll(categories: List<Category>) = categories.forEach { update(it) }
    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
