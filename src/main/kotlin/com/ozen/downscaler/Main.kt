package com.ozen.downscaler

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

private val logger = KotlinLogging.logger {}

fun main() = runBlocking {
    val config = Config.fromEnv()
    File(config.workDir).mkdirs()

    val api = RecorderApi(config.recorderApiUrl, config.recorderApiKey)
    val s3 = S3Storage(config)
    val pipeline = Pipeline(config, api, s3)

    if (config.recordingId != null) {
        logger.info { "Single recording mode: ${config.recordingId}" }
        try {
            val recording = api.fetchRecording(config.recordingId)
            pipeline.processRecording(recording)
        } finally {
            s3.close()
        }
        return@runBlocking
    }

    logger.info { "Starting recording-downscaler (concurrency=${config.concurrency}, poll=${config.pollInterval}s)" }

    val scope = this
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Received shutdown signal" }
        scope.cancel()
    })

    try {
        while (isActive) {
            runPollCycle(config, pipeline, api)
            logger.info { "Sleeping ${config.pollInterval}s until next cycle" }
            delay(config.pollInterval * 1000)
        }
    } catch (_: CancellationException) {
        logger.info { "Shutting down gracefully" }
    } finally {
        s3.close()
        logger.info { "Shutdown complete" }
    }
}

private suspend fun CoroutineScope.runPollCycle(
    config: Config,
    pipeline: Pipeline,
    api: RecorderApi,
) {
    val recordings = try {
        api.fetchRecordingsToProcess().filter { it.s3Key != null }
    } catch (e: Exception) {
        logger.error(e) { "Failed to fetch recordings" }
        emptyList()
    }

    if (recordings.isEmpty()) {
        logger.info { "No recordings to process" }
        return
    }

    logger.info { "Found ${recordings.size} recordings to process" }

    val semaphore = Semaphore(config.concurrency)
    recordings.map { recording ->
        launch {
            semaphore.withPermit {
                try {
                    pipeline.processRecording(recording)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Failed to process recording ${recording.id}" }
                }
            }
        }
    }.joinAll()

    logger.info { "Poll cycle complete, processed ${recordings.size} recordings" }
}
