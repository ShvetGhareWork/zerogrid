package com.example.zerogrid.emergency

import android.content.Context
import android.util.Log
import com.example.zerogrid.mesh.engine.MeshEngine
import com.example.zerogrid.network.ConnectivityChecker
import com.example.zerogrid.network.RetrofitInstance
import com.example.zerogrid.network.SosApiService
import com.example.zerogrid.network.SosDispatchRequest

data class SosDispatchResult(
    val meshDispatched: Boolean,
    val onlineDispatched: Boolean,
    val queuedOffline: Boolean = false,
    val sosId: String? = null,
    val errorMessage: String? = null
)

/**
 * Unified emergency dispatcher that fans out SOS triggers simultaneously to:
 * 1. Offline BLE & Wi-Fi Direct Mesh (always fires immediately).
 * 2. Online central API (POST /api/sos) when internet is available, or queues for retry.
 */
class UnifiedSosDispatcher(
    private val context: Context,
    private val meshEngine: MeshEngine = MeshEngine.getInstance(context),
    private val sosApi: SosApiService = RetrofitInstance.sosApi,
    private val connectivityChecker: ConnectivityChecker = ConnectivityChecker(context)
) {
    companion object {
        private const val TAG = "UnifiedSosDispatcher"
    }

    suspend fun triggerSos(
        lat: Double? = null,
        lng: Double? = null,
        accuracy: Float? = null,
        category: String = "OTHER",
        message: String = ""
    ): SosDispatchResult {
        // 1. Local Mesh Broadcast (always executed)
        val meshDispatched = try {
            meshEngine.triggerSosBeacon(
                category = category,
                message = message,
                lat = lat,
                lon = lng,
                accuracy = accuracy
            )
            Log.d(TAG, "Mesh SOS beacon successfully broadcasted.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast SOS to local mesh", e)
            false
        }

        // 2. Format Category to match backend schema
        val normalizedCategory = when (category.trim().uppercase()) {
            "MEDICAL" -> "MEDICAL"
            "DISASTER" -> "DISASTER"
            "TRAPPED" -> "TRAPPED"
            "SECURITY" -> "SECURITY"
            else -> "OTHER"
        }

        val request = SosDispatchRequest(
            lat = lat ?: 0.0,
            lng = lng ?: 0.0,
            accuracy = accuracy,
            category = normalizedCategory,
            message = message.ifBlank { "Emergency SOS triggered" },
            transport = "BOTH"
        )

        // 3. Online Rescue Network Dispatch
        return if (connectivityChecker.isInternetAvailable()) {
            try {
                Log.d(TAG, "Internet connection active. Posting SOS to central rescue API...")
                val response = sosApi.dispatchSos(request)
                if (response.isSuccessful) {
                    val sosId = response.body()?.sos?.id
                    Log.d(TAG, "Online SOS dispatched successfully. Incident ID: $sosId")
                    SosDispatchResult(
                        meshDispatched = meshDispatched,
                        onlineDispatched = true,
                        queuedOffline = false,
                        sosId = sosId
                    )
                } else {
                    Log.w(TAG, "Online SOS failed with HTTP ${response.code()}: ${response.errorBody()?.string()}")
                    enqueueOfflineSos(request)
                    SosDispatchResult(
                        meshDispatched = meshDispatched,
                        onlineDispatched = false,
                        queuedOffline = true,
                        errorMessage = "Server response: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network exception during online SOS dispatch", e)
                enqueueOfflineSos(request)
                SosDispatchResult(
                    meshDispatched = meshDispatched,
                    onlineDispatched = false,
                    queuedOffline = true,
                    errorMessage = e.localizedMessage
                )
            }
        } else {
            Log.d(TAG, "No internet connection detected. Queuing SOS dispatch for retry.")
            enqueueOfflineSos(request)
            SosDispatchResult(
                meshDispatched = meshDispatched,
                onlineDispatched = false,
                queuedOffline = true
            )
        }
    }

    private fun enqueueOfflineSos(request: SosDispatchRequest) {
        try {
            SosUploadWorker.enqueue(context, request)
            Log.d(TAG, "SOS enqueued via WorkManager for background delivery once internet returns.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue SOS with WorkManager", e)
        }
    }
}
