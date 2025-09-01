// YouTubeExtractor.kt
package com.example.youtubemusicapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.util.regex.Pattern

class YouTubeExtractor {

    companion object {
        private const val TAG = "YouTubeExtractor"
        private const val YOUTUBE_BASE_URL = "https://www.youtube.com/watch?v="
        private const val YOUTUBE_API_BASE = "https://www.googleapis.com/youtube/v3/"

        // Patrones regex para extraer información
        private val EXTRACT_PATTERN = Pattern.compile("\"url\":\"([^\"]+)\"")
        private val TITLE_PATTERN = Pattern.compile("\"title\":\"([^\"]+)\"")
        private val DURATION_PATTERN = Pattern.compile("\"lengthSeconds\":\"([^\"]+)\"")
    }

    data class YouTubeInfo(
        val title: String,
        val audioUrl: String?,
        val duration: String?,
        val thumbnail: String?
    )

    /**
     * Extrae información del video de YouTube
     */
    suspend fun extractVideoInfo(videoId: String): YouTubeInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val videoUrl = YOUTUBE_BASE_URL + videoId
                val response = fetchVideoPage(videoUrl)

                if (response != null) {
                    parseVideoInfo(response, videoId)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extrayendo información del video: ${e.message}")
                null
            }
        }
    }

    /**
     * Busca videos en YouTube usando la API de búsqueda simulada
     */
    suspend fun searchVideos(query: String, maxResults: Int = 20): List<YouTubeVideo> {
        return withContext(Dispatchers.IO) {
            try {
                // NOTA: En una implementación real, usarías la YouTube Data API v3
                // Aquí simulo algunos resultados de búsqueda
                generateSimulatedResults(query, maxResults)
            } catch (e: Exception) {
                Log.e(TAG, "Error en búsqueda: ${e.message}")
                emptyList()
            }
        }
    }

    private suspend fun fetchVideoPage(url: String): String? {
        return try {
            val connection = URL(url).openConnection()
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.getInputStream().bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo página: ${e.message}")
            null
        }
    }

    private fun parseVideoInfo(html: String, videoId: String): YouTubeInfo? {
        return try {
            // Extraer título
            val titleMatcher = TITLE_PATTERN.matcher(html)
            val title = if (titleMatcher.find()) {
                URLDecoder.decode(titleMatcher.group(1), "UTF-8")
            } else {
                "Título desconocido"
            }

            // Extraer duración
            val durationMatcher = DURATION_PATTERN.matcher(html)
            val duration = if (durationMatcher.find()) {
                formatDuration(durationMatcher.group(1)?.toIntOrNull() ?: 0)
            } else {
                null
            }

            // Extraer URL de audio (esto es complejo y requiere parsear los streams)
            val audioUrl = extractAudioUrl(html)

            // Thumbnail
            val thumbnail = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"

            YouTubeInfo(
                title = title,
                audioUrl = audioUrl,
                duration = duration,
                thumbnail = thumbnail
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parseando información: ${e.message}")
            null
        }
    }

    private fun extractAudioUrl(html: String): String? {
        return try {
            // NOTA: La extracción real de URLs de YouTube es compleja y cambia frecuentemente
            // YouTube encripta y ofusca estas URLs para prevenir su uso directo
            // Aquí hay una implementación básica que puede no funcionar siempre

            val urlMatcher = EXTRACT_PATTERN.matcher(html)
            var bestAudioUrl: String? = null

            while (urlMatcher.find()) {
                val url = URLDecoder.decode(urlMatcher.group(1), "UTF-8")

                // Buscar streams de solo audio
                if (url.contains("mime=audio") || url.contains("audio/mp4")) {
                    bestAudioUrl = url
                    break
                }
            }

            bestAudioUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo URL de audio: ${e.message}")
            null
        }
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }

    /**
     * Genera resultados simulados para demostración
     * En una app real, usarías la YouTube Data API v3
     */
    private fun generateSimulatedResults(query: String, maxResults: Int): List<YouTubeVideo> {
        return List(maxResults) { index ->
            val videoId = "video_${query}_$index"
            YouTubeVideo(
                id = videoId,
                title = "$query - Resultado ${index + 1}",
                artist = "Artista ${index + 1}",
                thumbnail = "https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
                url = YOUTUBE_BASE_URL + videoId,
                duration = "${(2 + index % 5)}:${String.format("%02d", (30 + index * 7) % 60)}"
            )
        }
    }

    /**
     * Valida si una URL es de YouTube
     */
    fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com/watch") ||
                url.contains("youtu.be/") ||
                url.contains("m.youtube.com")
    }

    /**
     * Extrae el ID del video de una URL de YouTube
     */
    fun extractVideoId(url: String): String? {
        return try {
            when {
                url.contains("youtube.com/watch") -> {
                    val pattern = Pattern.compile("v=([a-zA-Z0-9_-]+)")
                    val matcher = pattern.matcher(url)
                    if (matcher.find()) matcher.group(1) else null
                }
                url.contains("youtu.be/") -> {
                    val pattern = Pattern.compile("youtu.be/([a-zA-Z0-9_-]+)")
                    val matcher = pattern.matcher(url)
                    if (matcher.find()) matcher.group(1) else null
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo ID: ${e.message}")
            null
        }
    }
}

// Extensión del data class para incluir duración
data class YouTubeVideo(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val url: String,
    val duration: String? = null
)