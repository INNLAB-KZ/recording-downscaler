package com.ozen.downscaler

data class Config(
    val recorderApiUrl: String,
    val recorderApiKey: String?,
    val spacesEndpoint: String,
    val spacesBucket: String,
    val spacesKey: String,
    val spacesSecret: String,
    val spacesFileAcl: String,
    val workDir: String,
    val concurrency: Int,
    val pollInterval: Long,
    val videoQualities: List<String>,
    val useGpu: Boolean,
    val spacesCdnBase: String,
    val recordingId: String?,
) {
    companion object {
        fun fromEnv(): Config = Config(
            recorderApiUrl = env("RECORDER_API_URL", "http://localhost:8080"),
            recorderApiKey = env("RECORDER_API_KEY"),
            spacesEndpoint = env("SPACES_ENDPOINT", "https://fra1.digitaloceanspaces.com"),
            spacesBucket = env("SPACES_BUCKET", "ozen-recordings"),
            spacesKey = env("SPACES_KEY") ?: error("SPACES_KEY is required"),
            spacesSecret = env("SPACES_SECRET") ?: error("SPACES_SECRET is required"),
            spacesFileAcl = env("SPACES_FILE_ACL", "private"),
            workDir = env("WORK_DIR", "/tmp/downscaler"),
            concurrency = env("CONCURRENCY", "3").toInt(),
            pollInterval = env("POLL_INTERVAL", "300").toLong(),
            videoQualities = env("VIDEO_QUALITIES", "360p,480p,720p,1080p").split(","),
            useGpu = env("USE_GPU", "false").toBoolean(),
            spacesCdnBase = env("SPACES_CDN_BASE", "https://ozen-recordings.fra1.cdn.digitaloceanspaces.com"),
            recordingId = env("RECORDING_ID"),
        )

        private fun env(name: String, default: String): String =
            System.getenv(name) ?: default

        private fun env(name: String): String? =
            System.getenv(name)?.ifBlank { null }
    }
}
