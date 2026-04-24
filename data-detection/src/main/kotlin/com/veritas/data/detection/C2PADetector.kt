@file:Suppress(
    "CyclomaticComplexMethod",
    "ReturnCount",
    "MagicNumber",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "UnusedPrivateProperty",
)

package com.veritas.data.detection

import android.util.JsonReader
import android.util.Log
import com.veritas.domain.detection.C2PAOutcome
import com.veritas.domain.detection.C2PAResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.C2PAError
import java.io.StringReader
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class C2PADetector @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
) {
    companion object {
        private const val TAG = "C2PADetector"
    }

    suspend fun detect(file: java.io.File): C2PAResult = withContext(Dispatchers.IO) {
        try {
            val manifestJson = C2PA.readFile(file.absolutePath)
            parseManifest(manifestJson)
        } catch (e: C2PAError) {
            val msg = e.message ?: ""
            if (msg.contains("no manifest", ignoreCase = true) ||
                msg.contains("not found", ignoreCase = true) ||
                msg.contains("invalid", ignoreCase = true)
            ) {
                C2PAResult.NotPresent
            } else {
                C2PAResult.Invalid(reason = msg)
            }
        } catch (e: Exception) {
            Log.w(TAG, "C2PA detection failed for ${file.absolutePath}", e)
            C2PAResult.NotPresent
        }
    }

    private fun parseManifest(json: String): C2PAResult {
        if (json.isBlank() || json == "{}" || json == "{\"manifests\":{}}") {
            return C2PAResult.NotPresent
        }

        return try {
            val reader = JsonReader(StringReader(json))
            reader.isLenient = true
            val manifestData = readActiveManifest(reader)

            if (manifestData.instanceId == null && manifestData.claimGenerator == null) {
                return C2PAResult.NotPresent
            }

            C2PAResult.Present(
                instanceId = manifestData.instanceId,
                issuerName = manifestData.issuerName,
                deviceName = extractDeviceName(manifestData.claimGenerator),
                signedAt = manifestData.signedAt?.let { parseIso8601(it) },
                claimGenerator = manifestData.claimGenerator,
                actions = manifestData.actions,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse C2PA manifest JSON", e)
            C2PAResult.Invalid(reason = "Malformed manifest: ${e.message}")
        }
    }

    private data class ManifestData(
        var instanceId: String? = null,
        var claimGenerator: String? = null,
        var issuerName: String? = null,
        var signedAt: String? = null,
        val actions: MutableList<String> = mutableListOf(),
    )

    private fun readActiveManifest(reader: JsonReader): ManifestData {
        val data = ManifestData()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "active_manifest" -> readManifestObject(reader, data)
                "manifests" -> skipJsonObject(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return data
    }

    private fun readManifestObject(reader: JsonReader, data: ManifestData) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "instance_id" -> data.instanceId = reader.nextString()
                "claim_generator" -> data.claimGenerator = reader.nextString()
                "signature_info" -> readSignatureInfo(reader, data)
                "actions" -> readActions(reader, data)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readSignatureInfo(reader: JsonReader, data: ManifestData) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "issuer" -> data.issuerName = reader.nextString()
                "time" -> data.signedAt = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readActions(reader: JsonReader, data: ManifestData) {
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "action") {
                    data.actions.add(reader.nextString())
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endArray()
    }

    private fun skipJsonObject(reader: JsonReader) {
        reader.beginObject()
        while (reader.hasNext()) {
            reader.skipValue()
        }
        reader.endObject()
    }

    private fun parseIso8601(dateStr: String): kotlinx.datetime.Instant? {
        return try {
            val parsed = OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            kotlinx.datetime.Instant.fromEpochMilliseconds(parsed.toInstant().toEpochMilli())
        } catch (e: Exception) {
            try {
                val instant = Instant.parse(dateStr)
                kotlinx.datetime.Instant.fromEpochMilliseconds(instant.toEpochMilli())
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun extractDeviceName(claimGenerator: String?): String? {
        if (claimGenerator == null) return null
        val parts = claimGenerator.split("/")
        return parts.getOrNull(0)?.takeIf { it.isNotBlank() }
    }
}
