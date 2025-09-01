// EnhancedMusicService.kt
package com.example.youtubemusicapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes as ExoAudioAttributes
import kotlinx.coroutines.*

class EnhancedMusicService : Service(), Player.Listener {

    private var exoPlayer: ExoPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val youtubeExtractor = YouTubeExtractor()

    private val binder = MusicBinder()
    private var currentPlaylist = mutableListOf<YouTubeVideo>()
    private var currentIndex = 0

    private var currentSong: YouTubeVideo? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val ACTION_PLAY = "com.example.youtubemusicapp.PLAY"
        const val ACTION_PAUSE = "com.example.youtubemusicapp.PAUSE"
        const val ACTION_STOP = "com.example.youtubemusicapp.STOP"
        const val ACTION_NEXT = "com.example.youtubemusicapp.NEXT"
        const val ACTION_PREVIOUS = "com.example.youtubemusicapp.PREVIOUS"
        const val ACTION_PLAY_SONG = "com.example.youtubemusicapp.PLAY_SONG"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_channel"

        const val EXTRA_SONG_ID = "song_id"
        const val EXTRA_SONG_TITLE = "song_title"
        const val EXTRA_SONG_ARTIST = "song_artist"
        const val EXTRA_SONG_URL = "song_url"
    }

    inner class MusicBinder : Binder() {
        fun getService(): EnhancedMusicService = this@EnhancedMusicService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initializePlayer()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resumeMusic()
            ACTION_PAUSE -> pauseMusic()
            ACTION_STOP -> stopMusic()
            ACTION_NEXT -> skipToNext()
            ACTION_PREVIOUS -> skipToPrevious()
            ACTION_PLAY_SONG -> {
                val songId = intent.getStringExtra(EXTRA_SONG_ID)
                val title = intent.getStringExtra(EXTRA_SONG_TITLE) ?: ""
                val artist = intent.getStringExtra(EXTRA_SONG_ARTIST) ?: ""
                val url = intent.getStringExtra(EXTRA_SONG_URL) ?: ""

                if (songId != null) {
                    val song = YouTubeVideo(songId, title, artist, "", url)
                    playSong(song)
                }
            }
        }
        return START_STICKY
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                ExoAudioAttributes.Builder()
                    .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
                    .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            addListener(this@EnhancedMusicService)
        }
    }

    fun playSong(song: YouTubeVideo) {
        currentSong = song

        if (requestAudioFocus()) {
            serviceScope.launch {
                try {
                    // Extraer información real del video de YouTube
                    val videoInfo = youtubeExtractor.extractVideoInfo(song.id)

                    if (videoInfo?.audioUrl != null) {
                        val mediaItem = MediaItem.fromUri(videoInfo.audioUrl)
                        exoPlayer?.setMediaItem(mediaItem)
                        exoPlayer?.prepare()
                        exoPlayer?.play()

                        startForegroundService()
                    } else {
                        // Fallback: usar URL simulada para demostración
                        playDemoAudio(song)
                    }
                } catch (e: Exception) {
                    // Fallback en caso de error
                    playDemoAudio(song)
                }
            }
        }
    }

    private fun playDemoAudio(song: YouTubeVideo) {
        // Para demostración, usar un archivo de audio de prueba
        val demoUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        val mediaItem = MediaItem.fromUri(demoUrl)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()

        startForegroundService()
    }

    fun pauseMusic() {
        exoPlayer?.pause()
        updateNotification()
    }

    fun resumeMusic() {
        if (requestAudioFocus()) {
            exoPlayer?.play()
            updateNotification()
        }
    }

    fun stopMusic() {
        exoPlayer?.stop()
        abandonAudioFocus()
        stopForeground(true)
        stopSelf()
    }

    fun skipToNext() {
        if (currentPlaylist.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % currentPlaylist.size
            playSong(currentPlaylist[currentIndex])
        }
    }

    fun skipToPrevious() {
        if (currentPlaylist.isNotEmpty()) {
            currentIndex = if (currentIndex - 1 < 0) currentPlaylist.size - 1 else currentIndex - 1
            playSong(currentPlaylist[currentIndex])
        }
    }

    fun setPlaylist(playlist: List<YouTubeVideo>, startIndex: Int = 0) {
        currentPlaylist.clear()
        currentPlaylist.addAll(playlist)
        currentIndex = startIndex.coerceIn(0, playlist.size - 1)
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0

    fun getDuration(): Long = exoPlayer?.duration ?: 0

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    // Player.Listener methods
    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_ENDED -> {
                skipToNext()
            }
            Player.STATE_READY -> {
                updateNotification()
            }
            Player.STATE_BUFFERING -> {
                updateNotification()
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        updateNotification()
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(
                    AudioAttributes.Builder().run {
                        setUsage(AudioAttributes.USAGE_MEDIA)
                        setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        build()
                    }
                )
                setAcceptsDelayedFocusGain(true)
                setOnAudioFocusChangeListener(audioFocusChangeListener)
                build()
            }
            audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                exoPlayer?.play()
                exoPlayer?.volume = 1.0f
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                stopMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                exoPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                exoPlayer?.volume = 0.1f
            }
        }
    }

    private fun startForegroundService() {
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproducción de música",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reproducción de música"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val song = currentSong
        val isPlaying = isPlaying()

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pausar",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, EnhancedMusicService::class.java).apply { action = ACTION_PAUSE },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Reproducir",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, EnhancedMusicService::class.java).apply { action = ACTION_PLAY },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: "Reproduciendo música")
            .setContentText(song?.artist ?: "Artista desconocido")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDeleteIntent(
                PendingIntent.getService(
                    this, 0,
                    Intent(this, EnhancedMusicService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                android.R.drawable.ic_media_previous, "Anterior",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, EnhancedMusicService::class.java).apply { action = ACTION_PREVIOUS },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(playPauseAction)
            .addAction(
                android.R.drawable.ic_media_next, "Siguiente",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, EnhancedMusicService::class.java).apply { action = ACTION_NEXT },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        exoPlayer?.release()
        exoPlayer = null
        abandonAudioFocus()
    }
}