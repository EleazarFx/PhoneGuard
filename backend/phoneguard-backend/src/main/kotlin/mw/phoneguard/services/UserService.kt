package mw.phoneguard.services
import at.favre.lib.crypto.bcrypt.BCrypt
import mw.phoneguard.db.Users
import mw.phoneguard.models.LoginRequest
import mw.phoneguard.models.RegisterRequest
import mw.phoneguard.models.RegisterResponse
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

data class AuthenticatedUser(val id: String, val email: String)

class UserService {

    fun register(req: RegisterRequest): RegisterResponse? {
        if (req.email.isBlank() || !req.email.contains("@")) return null
        if (req.password.length < 8) return null

        return transaction {
            val emailTaken = Users
                .selectAll()
                .where { Users.email eq req.email.lowercase() }
                .any()

            if (emailTaken) return@transaction null

            val hash = BCrypt.withDefaults()
                .hashToString(12, req.password.toCharArray())

            val newId = UUID.randomUUID()

            Users.insert {
                it[id] = newId
                it[email] = req.email.lowercase()
                it[passwordHash] = hash
                it[emailVerified] = false
                it[createdAt] = LocalDateTime.now()
            }

            RegisterResponse(userId = newId.toString(), email = req.email.lowercase())
        }
    }

    fun authenticate(req: LoginRequest): AuthenticatedUser? {
        return transaction {
            val row = Users
                .selectAll()
                .where { Users.email eq req.email.lowercase() }
                .singleOrNull()
                ?: return@transaction null

            val verified = BCrypt.verifyer()
                .verify(req.password.toCharArray(), row[Users.passwordHash])
                .verified

            if (!verified) return@transaction null

            AuthenticatedUser(
                id = row[Users.id].value.toString(),
                email = row[Users.email]
            )
        }
    }
}
