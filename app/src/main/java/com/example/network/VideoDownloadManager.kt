package com.example.network

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File

object VideoDownloadManager {

    sealed class DownloadResult {
        data class Success(val downloadId: Long, val targetPath: String) : DownloadResult()
        data class Failure(val errorMessage: String) : DownloadResult()
    }

    enum class TargetFolder(val displayName: String, val systemDirectory: String, val subPath: String) {
        DOWNLOADS("📂 Downloads", Environment.DIRECTORY_DOWNLOADS, ""),
        MOVIES("🎬 Filmes", Environment.DIRECTORY_DOWNLOADS, "Filmes"),
        SERIES("📺 Séries & Animes", Environment.DIRECTORY_DOWNLOADS, "Series"),
        CUSTOM("📁 A2 Miguel Videos", Environment.DIRECTORY_DOWNLOADS, "A2_Miguel")
    }

    enum class VideoQuality(val label: String, val tag: String, val description: String) {
        FHD_1080P("1080p Full HD", "1080p", "Melhor qualidade e nitidez de imagem"),
        HD_720P("720p HD", "720p", "Equilíbrio ideal de nitidez e velocidade"),
        SD_480P("480p Rápido", "480p", "Download rápido e arquivo mais leve"),
        ORIGINAL("Original Servidor", "Original", "Fluxo nativo direto da fonte")
    }

    /**
     * Sanitiza o nome do arquivo para garantir compatibilidade com Android e Windows
     */
    fun sanitizeFileName(rawName: String, quality: VideoQuality = VideoQuality.HD_720P): String {
        var clean = rawName
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")

        if (clean.isBlank()) {
            clean = "A2_Miguel_Video"
        }

        // Se não tiver extensão .mp4 ou .mkv, adiciona
        if (!clean.endsWith(".mp4", ignoreCase = true) && !clean.endsWith(".mkv", ignoreCase = true)) {
            clean = "$clean.mp4"
        }

        return clean
    }

    /**
     * Sugere um nome limpo baseado no título e no episódio
     */
    fun suggestFileName(title: String, episodeNumber: String?, quality: VideoQuality): String {
        val baseTitle = title.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val cleanBase = if (baseTitle.isNotBlank()) baseTitle else "A2_Miguel"

        return if (!episodeNumber.isNullOrBlank()) {
            "A2_${cleanBase}_Ep${episodeNumber}_${quality.tag}.mp4"
        } else {
            "A2_${cleanBase}_${quality.tag}.mp4"
        }
    }

    /**
     * Inicia o download nativo usando o DownloadManager do Android
     */
    fun startDownload(
        context: Context,
        mediaUrl: String,
        customFileName: String,
        targetFolder: TargetFolder,
        quality: VideoQuality
    ): DownloadResult {
        if (mediaUrl.isBlank()) {
            return DownloadResult.Failure("URL do vídeo não encontrada para download.")
        }

        return try {
            val sanitizedName = sanitizeFileName(customFileName, quality)
            val uri = Uri.parse(mediaUrl)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: return DownloadResult.Failure("Serviço de DownloadManager indisponível neste aparelho.")

            val request = DownloadManager.Request(uri).apply {
                setTitle(sanitizedName)
                setDescription("Baixando no A2 Miguel ($quality.label)")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

                // Define pasta de destino
                val destinationPath = if (targetFolder.subPath.isNotBlank()) {
                    "${targetFolder.subPath}/$sanitizedName"
                } else {
                    sanitizedName
                }

                setDestinationInExternalPublicDir(targetFolder.systemDirectory, destinationPath)
            }

            val downloadId = downloadManager.enqueue(request)
            val fullPath = "${targetFolder.displayName} > $sanitizedName"

            Toast.makeText(
                context,
                "⬇️ Download iniciado!\nSalvo em: $fullPath",
                Toast.LENGTH_LONG
            ).show()

            DownloadResult.Success(downloadId, fullPath)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Erro desconhecido ao iniciar download"
            Toast.makeText(context, "Erro no download: $errorMsg", Toast.LENGTH_LONG).show()
            DownloadResult.Failure(errorMsg)
        }
    }

    /**
     * Gera comando para baixar no computador via PowerShell ou cURL
     */
    fun generatePcDownloadCommand(mediaUrl: String, fileName: String): String {
        val cleanName = sanitizeFileName(fileName)
        return """powershell -Command "Invoke-WebRequest -Uri '$mediaUrl' -OutFile '${'$'}env:USERPROFILE\Downloads\$cleanName'""""
    }
}
