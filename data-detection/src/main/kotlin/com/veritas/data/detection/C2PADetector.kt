@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "CyclomaticComplexMethod",
    "LongMethod",
    "NestedBlockDepth",
    "UnusedPrivateProperty",
)

package com.veritas.data.detection

import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import com.veritas.domain.detection.C2PAOutcome
import com.veritas.domain.detection.C2PAResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.C2PAError
import org.contentauth.c2pa.FileStream
import org.contentauth.c2pa.Reader
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
        private val NOT_PRESENT_ERROR_PATTERNS = listOf("no manifest", "not found", "parsed", "io")
        private const val MAX_FILE_SIZE_BYTES = 150L * 1024 * 1024 // 150 MB
    }

    suspend fun detect(file: java.io.File): C2PAResult = withContext(Dispatchers.IO) {

        if (file.length() > MAX_FILE_SIZE_BYTES) {
            Log.w(TAG, "File too large for in-memory C2PA parse: ${file.length()} bytes")
            return@withContext C2PAResult.NotPresent
        }

        val mimeType = when (file.extension.lowercase()) {
            in setOf("jpg", "jpeg") -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "webp" -> "image/webp"
            else -> {
                Log.w(TAG, "Unsupported C2PA media type for: ${file.name}")
                return@withContext C2PAResult.NotPresent
            }
        }

        val stream = FileStream(file, FileStream.Mode.READ, false)

        var reader: Reader? = null
        try {
            reader = Reader.fromStream(mimeType, stream)
            val detailedJson = reader.detailedJson()

            if (detailedJson.isBlank() || detailedJson == "{}" || detailedJson == "{\"manifests\":{}}") {
                Log.w(TAG, "C2PA returned blank/empty JSON for ${file.name}")
                return@withContext C2PAResult.NotPresent
            }

            val result = parseManifest(detailedJson)
            val outcomeStr = when (result) {
                is C2PAResult.Present -> "issuer=${result.issuerName}, generator=${result.claimGenerator}"
                is C2PAResult.Invalid -> "INVALID: ${result.reason}"
                is C2PAResult.Revoked -> "REVOKED: ${result.reason}"
                C2PAResult.NotPresent -> "NOT_PRESENT"
            }
            Log.i(TAG, "C2PA parse OK: $outcomeStr")
            result
        } catch (e: C2PAError) {
            val msg = e.message ?: ""
            Log.e(TAG, "C2PAError for ${file.absolutePath}: $msg", e)
            val isNotPresentError = NOT_PRESENT_ERROR_PATTERNS.any { pattern ->
                msg.contains(pattern, ignoreCase = true)
            }
            if (isNotPresentError) {
                C2PAResult.NotPresent
            } else {
                C2PAResult.Invalid(reason = msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "C2PA detection failed for ${file.absolutePath}", e)
            C2PAResult.NotPresent
        } finally {
            try { reader?.close() } catch (e: Exception) { /* ignore */ }
            try { stream.close() } catch (e: Exception) { /* ignore */ }
        }
    }

    private fun parseManifest(json: String): C2PAResult {
        if (json.isBlank() || json == "{}" || json == "{\"manifests\":{}}") {
            return C2PAResult.NotPresent
        }

        return try {
            val reader = JsonReader(StringReader(json))
            reader.isLenient = true
            val manifestData = readDetailedManifest(reader)

            if (manifestData.instanceId == null && manifestData.claimGenerator == null) {
                return C2PAResult.NotPresent
            }

            if (manifestData.validationErrors.isNotEmpty()) {
                return C2PAResult.Invalid(reason = manifestData.validationErrors.joinToString("; ") { it })
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
        val validationErrors: MutableList<String> = mutableListOf(),
    )

    private fun readDetailedManifest(reader: JsonReader): ManifestData {
        val data = ManifestData()
        var activeManifestKey: String? = null
        var inValidationStatus = false

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when {
                name == "validation_status" -> {
                    inValidationStatus = true
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var code: String? = null
                        var explanation: String? = null
                        while (reader.hasNext()) {
                            val fieldName = reader.nextName()
                            when (fieldName) {
                                "code" -> code = reader.nextString()
                                "explanation" -> explanation = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (code != null && !code.startsWith("assertion.")) {
                            data.validationErrors.add(explanation ?: code)
                        }
                    }
                    reader.endArray()
                    inValidationStatus = false
                }
                name == "active_manifest" -> {
                    val value = reader.peek()
                    if (value == JsonToken.STRING) {
                        activeManifestKey = reader.nextString()
                    } else {
                        readManifestObject(reader, data)
                    }
                }
                name == activeManifestKey -> {
                    readManifestObject(reader, data)
                }
                name == "manifests" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val manifestKey = reader.nextName()
                        if (manifestKey == activeManifestKey) {
                            readManifestObject(reader, data)
                        } else {
                            skipJsonObject(reader)
                        }
                    }
                    reader.endObject()
                }
                name == "validation_results" -> {
                    reader.skipValue()
                }
                else -> {
                    reader.skipValue()
                }
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
