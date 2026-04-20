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
        val newUrl = "${config.spacesCdnBase}/$newS3Key"

        logger.info { "Processing recording $id (quality=${recording.videoQuality}, key=$oldS3Key -> $newS3Key)" }

        val inputFile = File(config.workDir, "${id}_input.ts")
        val outputFile = File(config.workDir, "${id}_output.mp4")

        try {
            logger.info { "[$id] Downloading $oldS3Key" }
            s3.download(oldS3Key, inputFile)
            logger.info { "[$id] Downloaded ${inputFile.length()} bytes" }

            logger.info { "[$id] Transcoding to 240p mp4" }
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

            logger.info { "[$id] Deleting old file $oldS3Key" }
            s3.delete(oldS3Key)

            inputFile.delete()
            outputFile.delete()

            val fileSizeBytes = actualSize
            logger.info { "[$id] Updating API: video_quality=240p, url=$newUrl, file_size_bytes=$fileSizeBytes" }
            api.updateRecording(id, "240p", newUrl, newS3Key, fileSizeBytes)

            logger.info { "[$id] Done" }
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }
}
