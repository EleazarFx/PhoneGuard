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


object Events : UUIDTable("events") {
    val deviceId = reference("device_id", Devices, onDelete = ReferenceOption.CASCADE)
    val clientSeq = long("client_seq").nullable()
    val type = varchar("type", 50)
    val timestamp = long("timestamp")                       // epoch millis from device
    val lat = double("lat").nullable()
    val lng = double("lng").nullable()
    val accuracy = double("accuracy").nullable()
    val battery = integer("battery").nullable()
    val captureStatus = varchar("capture_status", 30).nullable()
    val photoSha256 = varchar("photo_sha256", 64).nullable()
    val threatLevel = varchar("threat_level", 20).nullable()
    val createdAt = datetime("created_at")
}

object Commands : UUIDTable("commands") {
    val deviceId = reference("device_id", Devices, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val command = varchar("command", 50)
    val payload = text("payload").nullable()
    val createdAt = datetime("created_at")
    val expiresAt = datetime("expires_at").nullable()
    val deliveredAt = datetime("delivered_at").nullable()
    val executedAt = datetime("executed_at").nullable()
    val error = varchar("error", 500).nullable()
}
