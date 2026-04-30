package com.ozen.downscaler

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

object FFmpeg {

    private val logger = KotlinLogging.logger {}

    fun transcode(input: File, output: File, useGpu: Boolean) {
        val cmd = listOf(
            "ffmpeg", "-y", "-i", input.absolutePath,
            "-vf", "scale=192:108,fps=8",
            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "32",
            "-maxrate", "100k", "-bufsize", "200k",
            "-c:a", "aac", "-b:a", "40k", "-ac", "1", "-ar", "22050",
            "-movflags", "+faststart",
            "-f", "mp4", output.absolutePath,
        )
        logger.info { "Running: ${cmd.joinToString(" ")}" }

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()

        val processOutput = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error { "ffmpeg output:\n$processOutput" }
            error("ffmpeg exited with code $exitCode")
        }

        logger.info { "Transcoded ${input.name} (${input.length()} bytes) -> ${output.name} (${output.length()} bytes)" }
    }

    fun extractAudio(input: File, output: File) {
        val cmd = listOf(
            "ffmpeg", "-y", "-i", input.absolutePath,
            "-vn",
            "-c:a", "aac", "-b:a", "128k", "-ac", "1",
            "-f", "adts", output.absolutePath,
        )
        logger.info { "Running: ${cmd.joinToString(" ")}" }

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()

        val processOutput = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error { "ffmpeg output:\n$processOutput" }
            error("ffmpeg exited with code $exitCode")
        }

        logger.info { "Extracted audio ${input.name} (${input.length()} bytes) -> ${output.name} (${output.length()} bytes)" }
    }
}
