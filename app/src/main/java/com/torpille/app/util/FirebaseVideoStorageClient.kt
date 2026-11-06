package com.torpille.app.util

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class FirebaseVideoStorageClient(
    private val storage: FirebaseStorage,
    private val contentResolver: ContentResolver,
) {
    data class UploadedMedia(
        val bucket: String,
        val storagePath: String,
        val downloadUrl: String? = null,
    )

    suspend fun uploadCommunityVideo(
        communityId: String,
        localVideoUri: Uri,
        filename: String,
    ): UploadedMedia {
        return uploadCommunityMedia(
            communityId = communityId,
            folder = "videos",
            localUri = localVideoUri,
            filename = filename,
            fallbackMimeType = "video/mp4",
            resolveDownloadUrl = false,
        )
    }


    suspend fun uploadCommunityImage(
        communityId: String,
        localImageUri: Uri,
        filename: String,
    ): UploadedMedia {
        return uploadCommunityMedia(
            communityId = communityId,
            folder = "images",
            localUri = localImageUri,
            filename = filename,
            fallbackMimeType = "image/jpeg",
        )
    }

    suspend fun uploadCommunityAudio(
        communityId: String,
        localAudioUri: Uri,
        filename: String,
    ): UploadedMedia {
        return uploadCommunityMedia(
            communityId = communityId,
            folder = "audios",
            localUri = localAudioUri,
            filename = filename,
            fallbackMimeType = "audio/mp4",
            resolveDownloadUrl = false,
        )
    }

    private suspend fun uploadCommunityMedia(
        communityId: String,
        folder: String,
        localUri: Uri,
        filename: String,
        fallbackMimeType: String,
        resolveDownloadUrl: Boolean = true,
    ): UploadedMedia {
        val storagePath = "communities/$communityId/$folder/$filename"
        val ref = storage.reference.child(storagePath)

        val mimeType = contentResolver.getType(localUri) ?: fallbackMimeType
        val metadata = StorageMetadata.Builder()
            .setContentType(mimeType)
            .build()

        // Important:
        // the file can already be present in Firebase Storage even if the next metadata / downloadUrl
        // call fails. In that case the old code aborted here and the Firestore message was never written,
        // so the vocal never appeared in the discussion thread.
        Log.d(MediaDebug.TAG, "upload start folder=$folder communityId=$communityId path=$storagePath localUri=$localUri mimeType=$mimeType")
        ref.putFile(localUri, metadata).await()
        Log.d(MediaDebug.TAG, "upload success folder=$folder communityId=$communityId path=$storagePath bucket=${ref.bucket}")

        return UploadedMedia(
            bucket = ref.bucket,
            storagePath = storagePath,
            downloadUrl = if (resolveDownloadUrl) resolveDownloadUrlOrNull(storagePath) else null,
        )
    }


    suspend fun resolveStorageDownloadUrl(
        storagePath: String,
        bucket: String? = null,
    ): String {
        val ref = if (bucket.isNullOrBlank()) {
            storage.reference.child(storagePath)
        } else {
            storage.getReferenceFromUrl("gs://$bucket/$storagePath")
        }

        Log.d(MediaDebug.TAG, "resolveStorageDownloadUrl start bucket=${bucket ?: storage.app.options.storageBucket} path=$storagePath")
        return try {
            val url = ref.downloadUrl.await().toString()
            Log.d(MediaDebug.TAG, "resolveStorageDownloadUrl success path=$storagePath")
            url
        } catch (e: StorageException) {
            Log.e(
                MediaDebug.TAG,
                MediaDebug.storageErrorMessage(
                    action = "Lecture Firebase Storage ($storagePath)",
                    errorCode = e.errorCode,
                    rawMessage = e.message,
                ),
                e,
            )
            throw IllegalStateException(
                MediaDebug.storageErrorMessage(
                    action = "Lecture Firebase Storage ($storagePath)",
                    errorCode = e.errorCode,
                    rawMessage = e.message,
                ),
                e,
            )
        }
    }

    private suspend fun resolveDownloadUrlOrNull(
        refPath: String,
        attempts: Int = 4,
        firstDelayMs: Long = 150,
    ): String? {
        val ref = storage.reference.child(refPath)
        var delayMs = firstDelayMs

        repeat(attempts) { attemptIndex ->
            try {
                return ref.downloadUrl.await().toString()
            } catch (e: StorageException) {
                if (e.errorCode == StorageException.ERROR_NOT_AUTHENTICATED ||
                    e.errorCode == StorageException.ERROR_NOT_AUTHORIZED
                ) {
                    return null
                }
                if (attemptIndex < attempts - 1) {
                    delay(delayMs)
                    delayMs *= 2
                }
            } catch (_: Exception) {
                if (attemptIndex < attempts - 1) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        return null
    }
}
