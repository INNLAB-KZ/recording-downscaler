package com.ozen.downscaler

data class Config(
    val recorderApiUrl: String,
    val recorderApiKey: String?,
    // Source S3 (download)
    val spacesEndpoint: String,
    val spacesBucket: String,
    val spacesKey: String,
    val spacesSecret: String,
    val spacesFileAcl: String,
    // Destination S3 (upload) — falls back to source if not set
    val destEndpoint: String,
    val destBucket: String,
    val destKey: String,
    val destSecret: String,
    val destFileAcl: String,
    val destCdnBase: String,
    val workDir: String,
    val concurrency: Int,
    val pollInterval: Long,
    val useGpu: Boolean,
    val spacesCdnBase: String,
    val recordingId: String?,
) {
    companion object {
        fun fromEnv(): Config {
            val spacesEndpoint = env("SPACES_ENDPOINT", "https://fra1.digitaloceanspaces.com")
            val spacesBucket = env("SPACES_BUCKET", "ozen-recordings")
            val spacesKey = env("SPACES_KEY") ?: error("SPACES_KEY is required")
            val spacesSecret = env("SPACES_SECRET") ?: error("SPACES_SECRET is required")
            val spacesFileAcl = env("SPACES_FILE_ACL", "private")
            val spacesCdnBase = env("SPACES_CDN_BASE", "https://ozen-recordings.fra1.cdn.digitaloceanspaces.com")

            return Config(
                recorderApiUrl = env("RECORDER_API_URL", "http://localhost:8080"),
                recorderApiKey = env("RECORDER_API_KEY"),
                spacesEndpoint = spacesEndpoint,
                spacesBucket = spacesBucket,
                spacesKey = spacesKey,
                spacesSecret = spacesSecret,
                spacesFileAcl = spacesFileAcl,
                destEndpoint = env("DEST_ENDPOINT", spacesEndpoint),
                destBucket = env("DEST_BUCKET", spacesBucket),
                destKey = env("DEST_KEY") ?: spacesKey,
                destSecret = env("DEST_SECRET") ?: spacesSecret,
                destFileAcl = env("DEST_FILE_ACL", spacesFileAcl),
                destCdnBase = env("DEST_CDN_BASE", spacesCdnBase),
                workDir = env("WORK_DIR", "/tmp/downscaler"),
                concurrency = env("CONCURRENCY", "3").toInt(),
                pollInterval = env("POLL_INTERVAL", "300").toLong(),
                useGpu = env("USE_GPU", "false").toBoolean(),
                spacesCdnBase = spacesCdnBase,
                recordingId = env("RECORDING_ID"),
            )
        }

        private fun env(name: String, default: String): String =
            System.getenv(name) ?: default

        private fun env(name: String): String? =
            System.getenv(name)?.ifBlank { null }
    }
}
