package com.ozen.downscaler

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

class Pipeline(
    private val config: Config,
    private val api: RecorderApi,
    private val s3: S3Storage,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun processRecording(recording: Recording) {
        val id = recording.id
        val oldS3Key = recording.s3Key ?: error("Recording $id has no s3_key")
        val newS3Key = oldS3Key.replaceAfterLast('.', "mp4")
        val newUrl = "${config.destCdnBase}/$newS3Key"

        logger.info { "Processing recording $id (quality=${recording.videoQuality}, key=$oldS3Key -> $newS3Key)" }

        val inputFile = File(config.workDir, "${id}_input.ts")
        val audioFile = File(config.workDir, "${id}_audio_output.aac")
        val outputFile = File(config.workDir, "${id}_output.mp4")

        try {
            logger.info { "[$id] Downloading $oldS3Key" }
            s3.download(oldS3Key, inputFile)
            logger.info { "[$id] Downloaded ${inputFile.length()} bytes" }

            // Extract audio 128kbps from original before transcoding video
            val audioS3Key = recording.audioS3Key
            if (audioS3Key != null && !s3.exists(audioS3Key)) {
                val audioUrl = "${config.destCdnBase}/$audioS3Key"
                logger.info { "[$id] Audio missing in S3, extracting from original video" }

                FFmpeg.extractAudio(inputFile, audioFile)
                logger.info { "[$id] Extracted audio ${audioFile.length()} bytes" }

                s3.upload(audioS3Key, audioFile, "audio/aac")
                val audioSize = s3.headObjectSize(audioS3Key)
                if (audioFile.length() != audioSize) {
                    error("Audio size mismatch: expected=${audioFile.length()} actual=$audioSize")
                }
                logger.info { "[$id] Audio uploaded and verified ($audioSize bytes)" }

                api.updateRecordingAudio(id, "128kbps", audioUrl, audioS3Key, audioSize)
                audioFile.delete()
            }

            // Transcode video to 108p
            logger.info { "[$id] Transcoding to 108p mp4" }
            FFmpeg.transcode(inputFile, outputFile, config.useGpu)
            logger.info { "[$id] Transcoded ${inputFile.length()} -> ${outputFile.length()} bytes" }

            logger.info { "[$id] Uploading $newS3Key" }
            s3.upload(newS3Key, outputFile)

            val expectedSize = outputFile.length()
            val actualSize = s3.headObjectSize(newS3Key)
            if (expectedSize != actualSize) {
                error("Size mismatch after upload: expected=$expectedSize actual=$actualSize")
            }
            logger.info { "[$id] Upload verified ($actualSize bytes)" }

            if (oldS3Key != newS3Key) {
                logger.info { "[$id] Deleting old file $oldS3Key" }
                s3.delete(oldS3Key)
            }

            inputFile.delete()
            outputFile.delete()

            val fileSizeBytes = actualSize
            logger.info { "[$id] Updating API: video_quality=108p, url=$newUrl, file_size_bytes=$fileSizeBytes" }
            api.updateRecording(id, "108p", newUrl, newS3Key, fileSizeBytes)

            logger.info { "[$id] Done" }
        } finally {
            inputFile.delete()
            audioFile.delete()
            outputFile.delete()
        }
    }

    suspend fun processRecordingAudio(recording: Recording) {
        val id = recording.id
        val audioS3Key = recording.audioS3Key ?: error("Recording $id has no audio_s3_key")
        val videoS3Key = recording.s3Key ?: error("Recording $id has no s3_key")
        val audioUrl = "${config.destCdnBase}/$audioS3Key"

        if (s3.exists(audioS3Key)) {
            logger.info { "[$id] Audio file already exists in S3: $audioS3Key, skipping" }
            return
        }

        logger.info { "[$id] Audio file missing in S3: $audioS3Key, extracting from video" }

        val inputFile = File(config.workDir, "${id}_audio_input.mp4")
        val outputFile = File(config.workDir, "${id}_audio_output.aac")

        try {
            logger.info { "[$id] Downloading $videoS3Key for audio extraction" }
            s3.download(videoS3Key, inputFile)
            logger.info { "[$id] Downloaded ${inputFile.length()} bytes" }

            logger.info { "[$id] Extracting audio to AAC 128kbps" }
            FFmpeg.extractAudio(inputFile, outputFile)
            logger.info { "[$id] Extracted audio ${inputFile.length()} -> ${outputFile.length()} bytes" }

            logger.info { "[$id] Uploading audio $audioS3Key" }
            s3.upload(audioS3Key, outputFile, "audio/aac")

            val expectedSize = outputFile.length()
            val actualSize = s3.headObjectSize(audioS3Key)
            if (expectedSize != actualSize) {
                error("Audio size mismatch after upload: expected=$expectedSize actual=$actualSize")
            }
            logger.info { "[$id] Audio upload verified ($actualSize bytes)" }

            inputFile.delete()
            outputFile.delete()

            logger.info { "[$id] Updating API: audio_quality=128kbps, audio_url=$audioUrl" }
            api.updateRecordingAudio(id, "128kbps", audioUrl, audioS3Key, actualSize)

            logger.info { "[$id] Audio done" }
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }
}
