package mw.phoneguard

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.*

class BackendIntegrationTest {

    @Test
    fun testRegisterValidation() = testApplication {
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:test_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to "",
                "jwt.secret" to "test_secret_key_12345678901234567890",
                "jwt.issuer" to "phoneguard-test",
                "jwt.audience" to "phoneguard-clients",
                "smtp.host" to "localhost",
                "smtp.port" to "25",
                "smtp.user" to "test",
                "smtp.password" to "test",
                "smtp.fromName" to "Test"
            )
        }
        application { module() }
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"invalid","password":"short"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testRegisterAndLogin() = testApplication {
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:test_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to "",
                "jwt.secret" to "test_secret_key_12345678901234567890",
                "jwt.issuer" to "phoneguard-test",
                "jwt.audience" to "phoneguard-clients",
                "smtp.host" to "localhost",
                "smtp.port" to "25",
                "smtp.user" to "test",
                "smtp.password" to "test",
                "smtp.fromName" to "Test"
            )
        }
        application { module() }
        val email = "test_${System.currentTimeMillis()}@example.com"
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"SecurePassword123!"}""")
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)

        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"SecurePassword123!","deviceInfo":{"model":"Pixel"}}""")
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val body = loginResponse.bodyAsText()
        assertTrue(body.contains("accessToken"))
    }

    @Test
    fun testPasswordResetFlow() = testApplication {
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:test_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to "",
                "jwt.secret" to "test_secret_key_12345678901234567890",
                "jwt.issuer" to "phoneguard-test",
                "jwt.audience" to "phoneguard-clients",
                "smtp.host" to "localhost",
                "smtp.port" to "25",
                "smtp.user" to "test",
                "smtp.password" to "test",
                "smtp.fromName" to "Test"
            )
        }
        application { module() }
        val email = "reset_${System.currentTimeMillis()}@example.com"
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"OldPassword123!"}""")
        }

        val forgotResponse = client.post("/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email"}""")
        }
        assertEquals(HttpStatusCode.OK, forgotResponse.status)
    }
}
