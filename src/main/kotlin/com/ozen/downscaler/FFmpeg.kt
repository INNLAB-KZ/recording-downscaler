package com.ozen.downscaler

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

object FFmpeg {

    private val logger = KotlinLogging.logger {}

    fun transcode(input: File, output: File, useGpu: Boolean) {
        val cmd = if (useGpu) {
            listOf(
                "ffmpeg", "-y",
                "-hwaccel", "cuda", "-hwaccel_output_format", "cuda",
                "-i", input.absolutePath,
                "-vf", "scale_cuda=-2:240",
                "-c:v", "h264_nvenc", "-preset", "p1", "-cq", "40",
                "-c:a", "copy",
                "-movflags", "+faststart",
                "-f", "mp4", output.absolutePath,
            )
        } else {
            listOf(
                "ffmpeg", "-y", "-i", input.absolutePath,
                "-c:v", "libx264", "-preset", "ultrafast", "-crf", "40",
                "-vf", "scale=-2:240",
                "-c:a", "copy",
                "-movflags", "+faststart",
                "-f", "mp4", output.absolutePath,
            )
        }
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
}
