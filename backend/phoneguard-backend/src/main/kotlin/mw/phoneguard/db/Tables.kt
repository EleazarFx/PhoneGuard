package mw.phoneguard.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val emailVerified = bool("email_verified").default(false)
    val createdAt = datetime("created_at")
}