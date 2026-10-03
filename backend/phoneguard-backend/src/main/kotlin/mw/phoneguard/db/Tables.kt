package mw.phoneguard.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val emailVerified = bool("email_verified").default(false)
    val createdAt = datetime("created_at")
}

object Devices : UUIDTable("devices") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val deviceName = varchar("device_name", 100)
    val model = varchar("model", 100).nullable()
    val imei1 = varchar("imei_1", 20).nullable()
    val imei2 = varchar("imei_2", 20).nullable()
    val status = varchar("status", 20).default("active")  // active / lost / stolen
    val lastLat = double("last_lat").nullable()
    val lastLng = double("last_lng").nullable()
    val lastSeen = datetime("last_seen").nullable()
    val lastBattery = integer("last_battery").nullable()
    val alertModeActive = bool("alert_mode_active").default(false)
    val wipeAllowed = bool("wipe_allowed").default(false)
    val createdAt = datetime("created_at")
}
