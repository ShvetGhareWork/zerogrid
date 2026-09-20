package com.example.zerogrid.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// ── Contact DTOs ───────────────────────────────────────────────────────────

data class ContactDto(
    @SerializedName("id")               val id: String,
    @SerializedName("name")             val name: String,
    @SerializedName("phoneNumber")      val phoneNumber: String,
    @SerializedName("relationship")     val relationship: String = "Other",
    @SerializedName("contactUserId")    val contactUserId: String? = null,
    @SerializedName("isRegisteredUser") val isRegisteredUser: Boolean = false,
    @SerializedName("createdAt")        val createdAt: String? = null
)

data class AddContactRequest(
    @SerializedName("name")         val name: String,
    @SerializedName("phoneNumber")  val phoneNumber: String,
    @SerializedName("relationship") val relationship: String
)

data class ContactsListResponse(
    @SerializedName("contacts") val contacts: List<ContactDto>
)

data class AddContactResponse(
    @SerializedName("message") val message: String,
    @SerializedName("contact") val contact: ContactDto
)

data class DeleteContactResponse(
    @SerializedName("message") val message: String,
    @SerializedName("id")      val id: String
)

// ── Retrofit Service ───────────────────────────────────────────────────────

interface ContactsApiService {

    @GET(ApiConstants.CONTACTS)
    suspend fun getContacts(): Response<ContactsListResponse>

    @POST(ApiConstants.CONTACTS)
    suspend fun addContact(
        @Body body: AddContactRequest
    ): Response<AddContactResponse>

    @DELETE("${ApiConstants.CONTACTS}/{id}")
    suspend fun deleteContact(
        @Path("id") id: String
    ): Response<DeleteContactResponse>
}
