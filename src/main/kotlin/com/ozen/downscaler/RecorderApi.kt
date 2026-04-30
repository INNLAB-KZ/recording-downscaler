package com.ozen.downscaler

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class RecorderApi(private val baseUrl: String, private val apiKey: String?) {

    private val logger = KotlinLogging.logger {}
    private val client = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun HttpRequest.Builder.maybeApiKey(): HttpRequest.Builder =
        if (apiKey != null) header("X-API-Key", apiKey) else this

    fun fetchRecording(id: String): Recording {
        val uri = "$baseUrl/api/recordings/$id"
        logger.info { "Fetching recording: $uri" }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .maybeApiKey()
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            error("GET $uri returned ${response.statusCode()}: ${response.body()}")
        }

        return json.decodeFromString<Recording>(response.body())
    }

    fun fetchRecordingsToProcess(): List<Recording> {
        val all = mutableListOf<Recording>()
        var offset = 0
        val limit = 200

        while (true) {
            val uri = "$baseUrl/api/recordings?status=done&video_quality_ne=108p&limit=$limit&offset=$offset"
            logger.info { "Fetching recordings: $uri" }

            val request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .maybeApiKey()
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                error("GET $uri returned ${response.statusCode()}: ${response.body()}")
            }

            val page = json.decodeFromString<RecordingsResponse>(response.body())
            all.addAll(page.data)
            logger.info { "Fetched ${all.size}/${page.total} recordings" }

            if (all.size >= page.total) break
            offset += limit
        }

        return all.filter { it.videoQuality != "audio-only" }
    }

    fun fetchRecordingsWithAudio(): List<Recording> {
        val all = mutableListOf<Recording>()
        var offset = 0
        val limit = 200

        while (true) {
            val uri = "$baseUrl/api/recordings?status=done&audio_s3_key_exists=true&limit=$limit&offset=$offset"
            logger.info { "Fetching recordings with audio_s3_key: $uri" }

            val request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .maybeApiKey()
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                error("GET $uri returned ${response.statusCode()}: ${response.body()}")
            }

            val page = json.decodeFromString<RecordingsResponse>(response.body())
            all.addAll(page.data)
            logger.info { "Fetched ${all.size}/${page.total} recordings with audio_s3_key" }

            if (all.size >= page.total) break
            offset += limit
        }

        return all.filter { it.audioS3Key != null && it.videoQuality != "audio-only" }
    }

    fun updateRecording(id: String, quality: String, url: String, s3Key: String, fileSizeBytes: Long) {
        val uri = "$baseUrl/api/recordings/$id"
        val body = """{"video_quality":"$quality","url":"$url","s3_key":"$s3Key","file_size_bytes":$fileSizeBytes}"""

        val request = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .maybeApiKey()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("PATCH $uri returned ${response.statusCode()}: ${response.body()}")
        }

        logger.info { "Updated recording $id video_quality=$quality url=$url file_size_bytes=$fileSizeBytes" }
    }

    fun updateRecordingAudio(id: String, audioQuality: String, audioUrl: String, audioS3Key: String, audioFileSizeBytes: Long) {
        val uri = "$baseUrl/api/recordings/$id"
        val body = """{"audio_quality":"$audioQuality","audio_url":"$audioUrl","audio_s3_key":"$audioS3Key","audio_file_size_bytes":$audioFileSizeBytes}"""

        val request = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .maybeApiKey()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("PATCH $uri returned ${response.statusCode()}: ${response.body()}")
        }

        logger.info { "Updated recording $id audio_quality=$audioQuality audio_url=$audioUrl audio_file_size_bytes=$audioFileSizeBytes" }
    }
}
