package mw.phoneguard.app.data.api

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import mw.phoneguard.app.data.models.ErrorResponse

@Serializable
data class ContactDto(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val createdAt: String = ""
)

@Serializable
data class CreateContactRequest(
    val name: String,
    val phone: String,
    val email: String? = null
)

class ContactApi {

    suspend fun listContacts(token: String): Result<List<ContactDto>> {
        return try {
            val response = ApiClient.http.get("${ApiClient.BASE_URL}/contacts") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch contacts (${response.status.value})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createContact(token: String, name: String, phone: String, email: String? = null): Result<ContactDto> {
        return try {
            val response = ApiClient.http.post("${ApiClient.BASE_URL}/contacts") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(CreateContactRequest(name = name, phone = phone, email = email))
            }
            if (response.status == HttpStatusCode.Created) {
                Result.success(response.body())
            } else {
                val errorMsg = try {
                    val err = response.body<ErrorResponse>()
                    err.error
                } catch (e: Exception) {
                    "Failed to create contact (${response.status.value})"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteContact(token: String, contactId: String): Result<Boolean> {
        return try {
            val response = ApiClient.http.delete("${ApiClient.BASE_URL}/contacts/$contactId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete contact (${response.status.value})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
