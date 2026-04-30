package com.ozen.downscaler

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.*
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.content.toInputStream
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

class S3Storage(private val config: Config) {

    private val logger = KotlinLogging.logger {}
    private val partSize = 64L * 1024 * 1024 // 64 MB

    private val sourceClient = S3Client {
        region = "fra1"
        endpointUrl = Url.parse(config.spacesEndpoint)
        credentialsProvider = StaticCredentialsProvider(
            Credentials(config.spacesKey, config.spacesSecret)
        )
        forcePathStyle = true
    }

    private val destClient = S3Client {
        region = "fra1"
        endpointUrl = Url.parse(config.destEndpoint)
        credentialsProvider = StaticCredentialsProvider(
            Credentials(config.destKey, config.destSecret)
        )
        forcePathStyle = true
    }

    suspend fun download(s3Key: String, destination: File) {
        logger.info { "Downloading s3://${config.spacesBucket}/$s3Key" }
        sourceClient.getObject(GetObjectRequest {
            bucket = config.spacesBucket
            key = s3Key
        }) { resp ->
            val input = resp.body?.toInputStream() ?: error("Empty body for key: $s3Key")
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        logger.info { "Downloaded ${destination.length()} bytes to ${destination.name}" }
    }

    suspend fun upload(s3Key: String, file: File, contentType: String = "video/mp4") {
        val fileSize = file.length()
        logger.info { "Uploading ${file.name} ($fileSize bytes) to s3://${config.destBucket}/$s3Key" }

        if (fileSize <= partSize) {
            destClient.putObject(PutObjectRequest {
                bucket = config.destBucket
                key = s3Key
                acl = ObjectCannedAcl.fromValue(config.destFileAcl)
                this.contentType = contentType
                body = ByteStream.fromFile(file)
            })
            return
        }

        multipartUpload(s3Key, file, fileSize, contentType)
    }

    suspend fun delete(s3Key: String) {
        logger.info { "Deleting s3://${config.spacesBucket}/$s3Key" }
        sourceClient.deleteObject(DeleteObjectRequest {
            bucket = config.spacesBucket
            key = s3Key
        })
    }

    private suspend fun multipartUpload(s3Key: String, file: File, fileSize: Long, contentType: String) {
        val createResp = destClient.createMultipartUpload(CreateMultipartUploadRequest {
            bucket = config.destBucket
            key = s3Key
            acl = ObjectCannedAcl.fromValue(config.destFileAcl)
            this.contentType = contentType
        })
        val uploadId = createResp.uploadId ?: error("No uploadId returned")

        try {
            val completedParts = mutableListOf<CompletedPart>()
            var partNumber = 1
            var bytesUploaded = 0L

            file.inputStream().use { input ->
                while (bytesUploaded < fileSize) {
                    val remaining = fileSize - bytesUploaded
                    val size = minOf(partSize, remaining).toInt()
                    val bytes = input.readNBytes(size)

                    val partResp = destClient.uploadPart(UploadPartRequest {
                        this.bucket = config.destBucket
                        this.key = s3Key
                        this.uploadId = uploadId
                        this.partNumber = partNumber
                        this.body = ByteStream.fromBytes(bytes)
                    })

                    completedParts.add(CompletedPart {
                        this.partNumber = partNumber
                        this.eTag = partResp.eTag
                    })

                    bytesUploaded += size
                    logger.info { "Uploaded part $partNumber ($bytesUploaded/$fileSize bytes)" }
                    partNumber++
                }
            }

            destClient.completeMultipartUpload(CompleteMultipartUploadRequest {
                this.bucket = config.destBucket
                this.key = s3Key
                this.uploadId = uploadId
                this.multipartUpload = CompletedMultipartUpload {
                    this.parts = completedParts
                }
            })
        } catch (e: Exception) {
            logger.error(e) { "Multipart upload failed, aborting" }
            destClient.abortMultipartUpload(AbortMultipartUploadRequest {
                this.bucket = config.destBucket
                this.key = s3Key
                this.uploadId = uploadId
            })
            throw e
        }
    }

    suspend fun sourceExists(s3Key: String): Boolean {
        return try {
            sourceClient.headObject(HeadObjectRequest {
                bucket = config.spacesBucket
                key = s3Key
            })
            true
        } catch (e: aws.sdk.kotlin.services.s3.model.NotFound) {
            false
        } catch (e: aws.sdk.kotlin.services.s3.model.NoSuchKey) {
            false
        }
    }

    suspend fun exists(s3Key: String): Boolean {
        return try {
            destClient.headObject(HeadObjectRequest {
                bucket = config.destBucket
                key = s3Key
            })
            true
        } catch (e: aws.sdk.kotlin.services.s3.model.NotFound) {
            false
        } catch (e: aws.sdk.kotlin.services.s3.model.NoSuchKey) {
            false
        }
    }

    suspend fun headObjectSize(s3Key: String): Long {
        val resp = destClient.headObject(HeadObjectRequest {
            bucket = config.destBucket
            key = s3Key
        })
        return resp.contentLength ?: 0L
    }

    fun close() {
        sourceClient.close()
        destClient.close()
    }
}
