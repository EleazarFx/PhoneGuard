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
            val ownerUuid = try { UUID.fromString(userId) } catch (e: Exception) { return@transaction false }
            val deviceUuid = try { UUID.fromString(deviceId) } catch (e: Exception) { return@transaction false }

            Devices
                .selectAll()
                .where { (Devices.id eq deviceUuid) and (Devices.userId eq ownerUuid) }
                .any()
        }
    }
}
