package com.example.zerogrid.emergency

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.zerogrid.network.RetrofitInstance
import com.example.zerogrid.network.SosDispatchRequest
import java.util.concurrent.TimeUnit

class SosUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SosUploadWorker"
        const val KEY_LAT = "sos_lat"
        const val KEY_LNG = "sos_lng"
        const val KEY_ACCURACY = "sos_accuracy"
        const val KEY_CATEGORY = "sos_category"
        const val KEY_MESSAGE = "sos_message"
        const val KEY_TRANSPORT = "sos_transport"
        const val KEY_BATTERY = "sos_battery"

        /**
         * Enqueues an offline SOS alert with network constraints and exponential backoff retry.
         */
        fun enqueue(context: Context, request: SosDispatchRequest) {
            val inputData = Data.Builder()
                .putDouble(KEY_LAT, request.lat)
                .putDouble(KEY_LNG, request.lng)
                .putFloat(KEY_ACCURACY, request.accuracy ?: 0f)
                .putString(KEY_CATEGORY, request.category)
                .putString(KEY_MESSAGE, request.message ?: "")
                .putString(KEY_TRANSPORT, request.transport)
                .putInt(KEY_BATTERY, request.batteryPercentage ?: -1)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SosUploadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag("SOS_OFFLINE_UPLOAD")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "SOS_OFFLINE_UPLOAD_UNIQUE",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Offline SOS request enqueued in WorkManager with unique policy.")
        }
    }

    override suspend fun doWork(): Result {
        val lat = inputData.getDouble(KEY_LAT, 0.0)
        val lng = inputData.getDouble(KEY_LNG, 0.0)
        val accuracyRaw = inputData.getFloat(KEY_ACCURACY, 0f)
        val accuracy = if (accuracyRaw > 0f) accuracyRaw else null
        val category = inputData.getString(KEY_CATEGORY) ?: "OTHER"
        val message = inputData.getString(KEY_MESSAGE)
        val transport = inputData.getString(KEY_TRANSPORT) ?: "BOTH"
        val batteryRaw = inputData.getInt(KEY_BATTERY, -1)
        val battery = if (batteryRaw >= 0) batteryRaw else null

        val request = SosDispatchRequest(
            lat = lat,
            lng = lng,
            accuracy = accuracy,
            category = category,
            message = message,
            transport = transport,
            batteryPercentage = battery
        )

        Log.d(TAG, "Attempting to dispatch queued SOS to backend: lat=$lat, lng=$lng, cat=$category")

        return try {
            val response = RetrofitInstance.sosApi.dispatchSos(request)
            if (response.isSuccessful) {
                val sosId = response.body()?.sos?.id
                Log.d(TAG, "Queued SOS successfully dispatched to backend! ID: $sosId")
                Result.success()
            } else if (response.code() in 400..499 && response.code() != 429) {
                // Client error (e.g. invalid auth, bad payload) that won't succeed on retry
                Log.e(TAG, "Permanent failure sending queued SOS: HTTP ${response.code()}")
                Result.failure()
            } else {
                // Rate limited (429) or Server error (5xx) -> Retry
                Log.w(TAG, "Transient failure sending queued SOS: HTTP ${response.code()}. Retrying...")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network exception while dispatching queued SOS. Retrying...", e)
            Result.retry()
        }
    }
}
