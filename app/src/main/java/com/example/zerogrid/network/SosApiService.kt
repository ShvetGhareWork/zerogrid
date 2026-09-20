package com.example.zerogrid.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// ── Request Body ────────────────────────────────────────────────────────────

data class SosDispatchRequest(
    @SerializedName("lat")       val lat: Double,
    @SerializedName("lng")       val lng: Double,
    @SerializedName("accuracy")  val accuracy: Float? = null,
    @SerializedName("category")  val category: String = "OTHER",
    @SerializedName("message")   val message: String? = null,
    @SerializedName("transport") val transport: String = "BOTH" // "ONLINE", "MESH", "BOTH"
)

// ── Response Bodies ─────────────────────────────────────────────────────────

data class SosUserSummaryDto(
    @SerializedName("id")          val id: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("phoneNumber") val phoneNumber: String? = null
)

data class SosLocationDto(
    @SerializedName("type")        val type: String = "Point",
    @SerializedName("coordinates") val coordinates: List<Double> // [lng, lat]
)

data class SosEventDto(
    @SerializedName("id")             val id: String,
    @SerializedName("triggeredBy")    val triggeredBy: SosUserSummaryDto? = null,
    @SerializedName("location")       val location: SosLocationDto? = null,
    @SerializedName("accuracyMeters") val accuracyMeters: Float? = null,
    @SerializedName("category")       val category: String = "OTHER",
    @SerializedName("message")        val message: String? = null,
    @SerializedName("transport")      val transport: String = "BOTH",
    @SerializedName("status")         val status: String = "ACTIVE",
    @SerializedName("acknowledgedBy") val acknowledgedBy: String? = null,
    @SerializedName("resolvedBy")     val resolvedBy: String? = null,
    @SerializedName("createdAt")      val createdAt: String? = null,
    @SerializedName("updatedAt")      val updatedAt: String? = null
)

data class SosDispatchResponse(
    @SerializedName("message") val message: String,
    @SerializedName("sos")     val sos: SosEventDto
)

data class SosDetailResponse(
    @SerializedName("sos") val sos: SosEventDto
)

// ── Retrofit Service ────────────────────────────────────────────────────────

interface SosApiService {

    @POST(ApiConstants.SOS)
    suspend fun dispatchSos(
        @Body body: SosDispatchRequest
    ): Response<SosDispatchResponse>

    @GET("${ApiConstants.SOS}/{id}")
    suspend fun getSosById(
        @Path("id") id: String
    ): Response<SosDetailResponse>
}
