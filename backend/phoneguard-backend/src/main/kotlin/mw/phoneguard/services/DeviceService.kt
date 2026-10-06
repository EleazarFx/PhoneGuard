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


}
