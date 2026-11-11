package mw.phoneguard.services

import mw.phoneguard.db.TrustedContacts
import mw.phoneguard.db.Users
import mw.phoneguard.models.ContactDto
import mw.phoneguard.models.CreateContactRequest
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class ContactService {

    fun listForUser(userId: String): List<ContactDto> {
        return transaction {
            val userUuid = try {
                UUID.fromString(userId)
            } catch (e: Exception) {
                return@transaction emptyList()
            }

            TrustedContacts
                .selectAll()
                .where { TrustedContacts.userId eq userUuid }
                .map { row ->
                    ContactDto(
                        id = row[TrustedContacts.id].value.toString(),
                        name = row[TrustedContacts.name],
                        phone = row[TrustedContacts.phone],
                        email = row[TrustedContacts.email],
                        createdAt = row[TrustedContacts.createdAt].toString()
                    )
                }
        }
    }

    fun createContact(userId: String, req: CreateContactRequest): Pair<ContactDto?, String?> {
        if (req.name.isBlank() || req.phone.isBlank()) {
            return null to "Name and phone cannot be blank"
        }

        return transaction {
            val userUuid = try {
                UUID.fromString(userId)
            } catch (e: Exception) {
                return@transaction null to "Invalid user ID"
            }

            val currentCount = TrustedContacts
                .selectAll()
                .where { TrustedContacts.userId eq userUuid }
                .count()

            if (currentCount >= 3) {
                return@transaction null to "Maximum 3 contacts"
            }

            val newId = UUID.randomUUID()
            val now = LocalDateTime.now()

            TrustedContacts.insert {
                it[id] = newId
                it[TrustedContacts.userId] = EntityID(userUuid, Users)
                it[name] = req.name.trim()
                it[phone] = req.phone.trim()
                it[email] = req.email?.trim()?.ifBlank { null }
                it[createdAt] = now
            }

            val dto = ContactDto(
                id = newId.toString(),
                name = req.name.trim(),
                phone = req.phone.trim(),
                email = req.email?.trim()?.ifBlank { null },
                createdAt = now.toString()
            )
            dto to null
        }
    }

    fun deleteContact(userId: String, contactId: String): Boolean {
        return transaction {
            val userUuid = try {
                UUID.fromString(userId)
            } catch (e: Exception) {
                return@transaction false
            }
            val contactUuid = try {
                UUID.fromString(contactId)
            } catch (e: Exception) {
                return@transaction false
            }

            val deleted = TrustedContacts.deleteWhere {
                (id eq contactUuid) and (TrustedContacts.userId eq userUuid)
            }
            deleted > 0
        }
    }
}
