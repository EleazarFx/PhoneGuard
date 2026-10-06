package mw.phoneguard.services

import mw.phoneguard.db.Devices
import mw.phoneguard.db.Users
import mw.phoneguard.models.DeviceSummary
import mw.phoneguard.models.RegisterDeviceRequest
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID


import mw.phoneguard.db.Events
import mw.phoneguard.models.EventSummary
import mw.phoneguard.models.HeartbeatRequest
import mw.phoneguard.models.PostEventRequest
import org.jetbrains.exposed.sql.update

import mw.phoneguard.db.Commands
import mw.phoneguard.models.DeviceCommand

import mw.phoneguard.models.CommandSummary
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll

class DeviceService {

    fun registerForUser(userId: String, req: RegisterDeviceRequest): DeviceSummary? {
        if (req.deviceName.isBlank()) return null

        return transaction {
            val ownerUuid = try {
                UUID.fromString(userId)
            } catch (e: IllegalArgumentException) {
                return@transaction null
            }

            val newId = UUID.randomUUID()
            val now = LocalDateTime.now()

            Devices.insert {
                it[id] = newId
                it[Devices.userId] = EntityID(ownerUuid, Users)
                it[deviceName] = req.deviceName
                it[model] = req.model
                it[imei1] = req.imei1
                it[imei2] = req.imei2
                it[status] = "active"
                it[createdAt] = now
            }

            DeviceSummary(
                id = newId.toString(),
                deviceName = req.deviceName,
                model = req.model,
                status = "active",
                lastSeen = null,
                lastBattery = null
            )
        }
    }

    fun listForUser(userId: String): List<DeviceSummary> {
        return transaction {
            val ownerUuid = try {
                UUID.fromString(userId)
            } catch (e: IllegalArgumentException) {
                return@transaction emptyList()
            }

            Devices
                .selectAll()
                .where { Devices.userId eq ownerUuid }
                .map { row ->
                    DeviceSummary(
                        id = row[Devices.id].value.toString(),
                        deviceName = row[Devices.deviceName],
                        model = row[Devices.model],
                        status = row[Devices.status],
                        lastSeen = row[Devices.lastSeen]?.toString(),
                        lastBattery = row[Devices.lastBattery]
                    )
                }
        }
    }

    fun existsForUser(userId: String, deviceId: String): Boolean {
        return transaction {
            val ownerUuid = try {
                UUID.fromString(userId)
            } catch (e: Exception) {
                return@transaction false
            }
            val deviceUuid = try {
                UUID.fromString(deviceId)
            } catch (e: Exception) {
                return@transaction false
            }

            Devices
                .selectAll()
                .where { (Devices.id eq deviceUuid) and (Devices.userId eq ownerUuid) }
                .any()
        }
    }


    fun postEvent(deviceId: String, req: PostEventRequest): EventSummary? {
        return transaction {
            val deviceUuid = try {
                UUID.fromString(deviceId)
            } catch (e: Exception) {
                return@transaction null
            }

            // Compute a simple threat level using the rule-based scoring from spec Section 8.2.
            // We'll move this into a dedicated ThreatScorer later.
            val threatLevel = computeThreatLevel(req.type)

            val newId = UUID.randomUUID()
            val now = LocalDateTime.now()

            Events.insert {
                it[id] = newId
                it[Events.deviceId] = EntityID(deviceUuid, Devices)
                it[clientSeq] = req.clientSeq
                it[type] = req.type
                it[timestamp] = req.timestamp
                it[lat] = req.lat
                it[lng] = req.lng
                it[accuracy] = req.accuracy
                it[battery] = req.battery
                it[captureStatus] = req.captureStatus
                it[photoSha256] = req.photoSha256
                it[Events.threatLevel] = threatLevel
                it[createdAt] = now
            }

            EventSummary(
                id = newId.toString(),
                type = req.type,
                timestamp = req.timestamp,
                receivedAt = now.toString(),
                lat = req.lat,
                lng = req.lng,
                accuracy = req.accuracy,
                battery = req.battery,
                captureStatus = req.captureStatus,
                photoSha256 = req.photoSha256,
                threatLevel = threatLevel
            )
        }
    }

    fun recordHeartbeat(deviceId: String, req: HeartbeatRequest): Boolean {
        return transaction {
            val deviceUuid = try {
                UUID.fromString(deviceId)
            } catch (e: Exception) {
                return@transaction false
            }

            val updated = Devices.update({ Devices.id eq deviceUuid }) {
                it[lastBattery] = req.battery
                it[lastLat] = req.lat
                it[lastLng] = req.lng
                it[lastSeen] = LocalDateTime.now()
                it[alertModeActive] = req.alertModeActive
            }

            updated > 0
        }
    }

    private fun computeThreatLevel(type: String): String {
        return when (type) {
            "sim_change" -> "High"
            "snatch" -> "High"
            "tamper" -> "High"
            "wrong_pin" -> "Medium"
            "restart" -> "Low"
            else -> "Low"
        }
    }


    fun pendingCommands(deviceId: String): List<DeviceCommand> {
        return transaction {
            val deviceUuid = try {
                UUID.fromString(deviceId)
            } catch (e: Exception) {
                return@transaction emptyList()
            }

            Commands
                .selectAll()
                .where { (Commands.deviceId eq deviceUuid) and (Commands.deliveredAt eq null) }
                .orderBy(Commands.createdAt)
                .map { row ->
                    DeviceCommand(
                        id = row[Commands.id].value.toString(),
                        command = row[Commands.command],
                        payload = row[Commands.payload],
                        createdAt = row[Commands.createdAt].toString(),
                        expiresAt = row[Commands.expiresAt]?.toString()
                    )
                }
        }
    }


    fun listEventsForOwner(userId: String, deviceId: String, limit: Int = 50): List<EventSummary>? {
        return transaction {
            val ownerUuid = try {
                UUID.fromString(userId)
            } catch (e: Exception) {
                return@transaction null
            }
            val deviceUuid = try {
                UUID.fromString(deviceId)
            } catch (e: Exception) {
                return@transaction null
            }

            // Verify ownership first
            val owns = Devices
                .selectAll()
                .where { (Devices.id eq deviceUuid) and (Devices.userId eq ownerUuid) }
                .any()
            if (!owns) return@transaction null

            Events
                .selectAll()
                .where { Events.deviceId eq deviceUuid }
                .orderBy(Events.timestamp to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    EventSummary(
                        id = row[Events.id].value.toString(),
                        type = row[Events.type],
                        timestamp = row[Events.timestamp],
                        receivedAt = row[Events.createdAt].toString(),
                        lat = row[Events.lat],
                        lng = row[Events.lng],
                        accuracy = row[Events.accuracy],
                        battery = row[Events.battery],
                        captureStatus = row[Events.captureStatus],
                        photoSha256 = row[Events.photoSha256],
                        threatLevel = row[Events.threatLevel]
                    )
                }
        }
    }

// ---------- Commands ----------

    fun createCommand(
        userId: String,
        deviceId: String,
        command: String,
        payload: String?,
        expiresInSeconds: Long?
    ): CommandSummary? {
        val validCommands = setOf(
            "LOCK", "ALARM", "MESSAGE", "LOCATE",
            "LIVE_TRACK", "ALERT_MODE", "LOST_MODE", "WIPE"
        )
        if (command !in validCommands) return null

        return transaction {
            val ownerUuid = try {
                UUID.fromString(userId)
            } catch (e: Exception) {
                return@transaction null
            }
            val deviceUuid = try {
                UUID.fromString(deviceId)
            } catch (e: Exception) {
                return@transaction null
            }

            // Owner must own the device
            val owns = Devices
                .selectAll()
                .where { (Devices.id eq deviceUuid) and (Devices.userId eq ownerUuid) }
                .any()
            if (!owns) return@transaction null

            val newId = UUID.randomUUID()
            val now = LocalDateTime.now()
            val expiresAt = now.plusSeconds(expiresInSeconds ?: (24L * 3600L))

            Commands.insert {
                it[id] = newId
                it[Commands.deviceId] = EntityID(deviceUuid, Devices)
                it[Commands.userId] = EntityID(ownerUuid, Users)
                it[Commands.command] = command
                it[Commands.payload] = payload
                it[createdAt] = now
                it[this.expiresAt] = expiresAt
            }

            CommandSummary(
                id = newId.toString(),
                command = command,
                payload = payload,
                createdAt = now.toString(),
                expiresAt = expiresAt.toString(),
                deliveredAt = null,
                executedAt = null,
                error = null,
                status = "queued"
            )
        }
    }

    fun listCommandsForOwner(userId: String, deviceId: String, limit: Int = 50): List<CommandSummary>? {
        return transaction {
            val ownerUuid = try {
                UUID.fromString(userId)
            } catch (e: Exception) {
                return@transaction null
            }
            val deviceUuid = try {
                UUID.fromString(deviceId)
            } catch (e: Exception) {
                return@transaction null
            }

            val owns = Devices
                .selectAll()
                .where { (Devices.id eq deviceUuid) and (Devices.userId eq ownerUuid) }
                .any()
            if (!owns) return@transaction null

            Commands
                .selectAll()
                .where { Commands.deviceId eq deviceUuid }
                .orderBy(Commands.createdAt to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    val delivered = row[Commands.deliveredAt]
                    val executed = row[Commands.executedAt]
                    val error = row[Commands.error]
                    val expiresAt = row[Commands.expiresAt]
                    val now = LocalDateTime.now()

                    val status = when {
                        error != null -> "failed"
                        executed != null -> "executed"
                        delivered != null -> "delivered"
                        expiresAt != null && expiresAt.isBefore(now) -> "expired"
                        else -> "queued"
                    }

                    CommandSummary(
                        id = row[Commands.id].value.toString(),
                        command = row[Commands.command],
                        payload = row[Commands.payload],
                        createdAt = row[Commands.createdAt].toString(),
                        expiresAt = expiresAt?.toString(),
                        deliveredAt = delivered?.toString(),
                        executedAt = executed?.toString(),
                        error = error,
                        status = status
                    )
                }
        }
    }

    fun updateCommandStatus(
        deviceId: String,
        commandId: String,
        status: String,
        error: String?
    ): Boolean {
        return transaction {
            val deviceUuid = try {
                UUID.fromString(deviceId)
            } catch (e: Exception) {
                return@transaction false
            }
            val commandUuid = try {
                UUID.fromString(commandId)
            } catch (e: Exception) {
                return@transaction false
            }

            // Verify the command belongs to this device
            val command = Commands
                .selectAll()
                .where { (Commands.id eq commandUuid) and (Commands.deviceId eq deviceUuid) }
                .firstOrNull()
                ?: return@transaction false
            val deliveredAt = command[Commands.deliveredAt]

            val now = LocalDateTime.now()

            val updated = Commands.update({
                (Commands.id eq commandUuid) and (Commands.deviceId eq deviceUuid)
            }) {
                when (status) {
                    "delivered" -> it[Commands.deliveredAt] = now
                    "executed" -> {
                        it[Commands.deliveredAt] = deliveredAt ?: now
                        it[executedAt] = now
                    }

                    "failed" -> {
                        it[Commands.deliveredAt] = deliveredAt ?: now
                        it[Commands.error] = error ?: "unknown"
                    }
                }
            }

            updated > 0
        }
    }


}
