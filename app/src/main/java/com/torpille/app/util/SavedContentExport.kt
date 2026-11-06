package com.torpille.app.util

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.net.Uri
import androidx.core.content.FileProvider
import com.torpille.app.data.Message
import com.torpille.app.repo.CommunityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object SavedContentExport {
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).build()
    suspend fun prepare(context: Context, message: Message, repo: CommunityRepository): File {
        val direct = when (message.type) { "image" -> message.imageUrl; "audio" -> message.audioUrl; else -> message.videoUrl }
        val path = when (message.type) { "image" -> message.imagePath; "audio" -> message.audioPath; else -> message.videoPath }
        val bucket = when (message.type) { "image" -> message.imageBucket; "audio" -> message.audioBucket; else -> message.videoBucket }
        val remote = if (message.type == "text") null else if (!path.isNullOrBlank()) repo.getAudioPlaybackUrl(path, bucket) else direct
        return withContext(Dispatchers.IO) {
            val folder = File(context.cacheDir, "exports").apply { mkdirs() }
            folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86400000 }?.forEach { it.delete() }
            if (message.type == "text") return@withContext File(folder, "Torpille-${UUID.randomUUID()}.txt").apply { writeText(message.text.orEmpty()) }
            check(remote?.startsWith("https://") == true) { "Média indisponible." }
            client.newCall(Request.Builder().url(remote!!).build()).execute().use { response ->
                check(response.isSuccessful) { "Téléchargement impossible. Réessaie." }
                val body = response.body ?: error("Média vide.")
                val mime = body.contentType()?.toString()?.substringBefore(';')
                val fallback = when (message.type) { "image" -> "jpg"; "audio" -> "m4a"; else -> "mp4" }
                val ext = mime?.takeIf { it.startsWith(message.type + "/") }?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: fallback
                val file = File(folder, "Torpille-${UUID.randomUUID()}.$ext")
                try { body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } } }
                catch (e: Exception) { file.delete(); throw e }
                file
            }
        }
    }
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        context.startActivity(Intent.createChooser(intent, "Partager le contenu"))
    }
    suspend fun save(context: Context, file: File, destination: Uri) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(destination) ?: error("Impossible d’écrire dans ce dossier.")
        output.use { stream -> file.inputStream().use { it.copyTo(stream) } }
    }
}
