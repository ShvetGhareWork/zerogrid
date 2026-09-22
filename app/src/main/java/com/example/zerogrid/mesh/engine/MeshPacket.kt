package com.example.zerogrid.mesh.engine

import org.json.JSONObject
import java.util.UUID

/**
 * Data packet structure for ZeroGrid off-grid mesh transport and multi-hop routing.
 */
data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val senderId: String,
    val recipientId: String = BROADCAST_ADDRESS,
    var ttl: Int = DEFAULT_TTL,
    var hopCount: Int = 0,
    val type: PacketType,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = ""
) {
    fun toJson(): String {
        val safePayload = payload.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"packetId":"$packetId","senderId":"$senderId","recipientId":"$recipientId","ttl":$ttl,"hopCount":$hopCount,"type":"${type.name}","payload":"$safePayload","timestamp":$timestamp,"signature":"$signature"}"""
    }

    fun toByteArray(): ByteArray = toJson().toByteArray(Charsets.UTF_8)

    /**
     * Parses GPS coordinates from an SOS_BEACON payload.
     *
     * Supports two formats:
     * 1. Structured JSON: {"category":"...","message":"...","lat":12.97,"lng":77.59,"accuracy":15.0}
     * 2. Legacy string:   "Category: MEDICAL | Msg: ... | Lat: 12.97, Lon: 77.59"
     *
     * Returns null if coordinates are absent, zero, or unparseable.
     */
    fun getSosCoordinates(): Pair<Double, Double>? {
        if (type != PacketType.SOS_BEACON) return null
        return try {
            val json = JSONObject(payload)
            val lat = json.optDouble("lat", 0.0)
            val lng = json.optDouble("lng", 0.0)
            if (lat != 0.0 || lng != 0.0) Pair(lat, lng) else null
        } catch (_: Exception) {
            // Fallback: parse legacy pipe-delimited string
            val lat = Regex("Lat:\\s*([\\-0-9.]+)").find(payload)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val lng = Regex("Lon:\\s*([\\-0-9.]+)").find(payload)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            if (lat != 0.0 || lng != 0.0) Pair(lat, lng) else null
        }
    }

    /** Parses GPS accuracy (meters) from a structured JSON SOS_BEACON payload. */
    fun getSosAccuracy(): Float? {
        if (type != PacketType.SOS_BEACON) return null
        return try {
            val json = JSONObject(payload)
            if (json.has("accuracy")) json.getDouble("accuracy").toFloat() else null
        } catch (_: Exception) { null }
    }

    /** Parses the SOS category from structured or legacy payload. */
    fun getSosCategory(): String {
        return try {
            JSONObject(payload).optString("category", "OTHER")
        } catch (_: Exception) {
            Regex("Category:\\s*([^|]+)").find(payload)?.groupValues?.get(1)?.trim() ?: "OTHER"
        }
    }

    /** Parses the SOS message text from structured or legacy payload. */
    fun getSosMessage(): String {
        return try {
            JSONObject(payload).optString("message", "")
        } catch (_: Exception) {
            Regex("Msg:\\s*([^|]+)").find(payload)?.groupValues?.get(1)?.trim() ?: payload
        }
    }

    companion object {
        const val BROADCAST_ADDRESS = "*"
        const val DEFAULT_TTL = 5

        /**
         * Builds a structured JSON SOS beacon payload string.
         * Receivers use [getSosCoordinates] / [getSosCategory] to extract fields.
         */
        fun buildSosPayload(
            category: String,
            message: String,
            lat: Double,
            lng: Double,
            accuracy: Float? = null
        ): String {
            val json = JSONObject()
            json.put("category", category)
            json.put("message", message)
            json.put("lat", lat)
            json.put("lng", lng)
            if (accuracy != null) json.put("accuracy", accuracy.toDouble())
            json.put("ts", System.currentTimeMillis())
            return json.toString()
        }

        fun fromJson(jsonStr: String): MeshPacket? {
            return try {
                val json = JSONObject(jsonStr)
                val sender = json.optString("senderId", "")
                if (sender.isEmpty()) return parseJsonFallback(jsonStr)

                MeshPacket(
                    packetId = json.optString("packetId", UUID.randomUUID().toString()),
                    senderId = sender,
                    recipientId = json.optString("recipientId", BROADCAST_ADDRESS),
                    ttl = json.optInt("ttl", DEFAULT_TTL),
                    hopCount = json.optInt("hopCount", 0),
                    type = PacketType.valueOf(json.optString("type", PacketType.DIRECT_MESSAGE.name)),
                    payload = json.optString("payload", ""),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    signature = json.optString("signature", "")
                )
            } catch (e: Exception) {
                parseJsonFallback(jsonStr)
            }
        }

        private fun parseJsonFallback(jsonStr: String): MeshPacket? {
            return try {
                fun extractString(key: String): String {
                    val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                    return regex.find(jsonStr)?.groupValues?.get(1) ?: ""
                }
                fun extractInt(key: String, default: Int): Int {
                    val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                    return regex.find(jsonStr)?.groupValues?.get(1)?.toIntOrNull() ?: default
                }
                fun extractLong(key: String, default: Long): Long {
                    val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                    return regex.find(jsonStr)?.groupValues?.get(1)?.toLongOrNull() ?: default
                }

                val sender = extractString("senderId")
                if (sender.isEmpty()) return null

                MeshPacket(
                    packetId = extractString("packetId").ifEmpty { UUID.randomUUID().toString() },
                    senderId = sender,
                    recipientId = extractString("recipientId").ifEmpty { BROADCAST_ADDRESS },
                    ttl = extractInt("ttl", DEFAULT_TTL),
                    hopCount = extractInt("hopCount", 0),
                    type = PacketType.valueOf(extractString("type").ifEmpty { PacketType.DIRECT_MESSAGE.name }),
                    payload = extractString("payload"),
                    timestamp = extractLong("timestamp", System.currentTimeMillis()),
                    signature = extractString("signature")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
