// SimpleMusicService.kt - Versión simplificada sin errores
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
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class SimpleMusicService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val binder = MusicBinder()

    private var currentSongTitle = ""
    private var currentSongArtist = ""
    private var isPlaying = false

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
        fun getService(): SimpleMusicService = this@SimpleMusicService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
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
                currentSongTitle = intent.getStringExtra(EXTRA_SONG_TITLE) ?: ""
                currentSongArtist = intent.getStringExtra(EXTRA_SONG_ARTIST) ?: ""
                startPlaying()
            }
        }

        return START_STICKY
    }

    private fun startPlaying() {
        if (requestAudioFocus()) {
            try {
                if (mediaPlayer == null) {
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setOnPreparedListener(this@SimpleMusicService)
                        setOnCompletionListener(this@SimpleMusicService)
                    }
                }

                mediaPlayer?.reset()

                // URL de demostración - En una app real usarías la URL extraída de YouTube
                val demoUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                mediaPlayer?.setDataSource(demoUrl)
                mediaPlayer?.prepareAsync()

                startForegroundService()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeMusic() {
        if (requestAudioFocus()) {
            mediaPlayer?.start()
            isPlaying = true
            updateNotification()
        }
    }

    private fun pauseMusic() {
        mediaPlayer?.pause()
        isPlaying = false
        updateNotification()
    }

    private fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun skipToNext() {
        // Implementar lógica para siguiente canción
        // Por ahora solo reinicia la canción actual
        startPlaying()
    }

    private fun skipToPrevious() {
        // Implementar lógica para canción anterior
        // Por ahora solo reinicia la canción actual
        mediaPlayer?.seekTo(0)
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mp?.start()
        isPlaying = true
        updateNotification()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        skipToNext()
    }

    fun isPlaying(): Boolean = isPlaying

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    build()
                })
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
                mediaPlayer?.start()
                mediaPlayer?.setVolume(1.0f, 1.0f)
                isPlaying = true
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                stopMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                mediaPlayer?.pause()
                isPlaying = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.1f, 0.1f)
            }
        }
        updateNotification()
    }

    private fun startForegroundService() {
        if (hasNotificationPermission()) {
            startForeground(NOTIFICATION_ID, createNotification())
        }
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

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotification(): Notification {
        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                playPauseIcon,
                "Pausar",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, SimpleMusicService::class.java).apply { action = ACTION_PAUSE },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            NotificationCompat.Action(
                playPauseIcon,
                "Reproducir",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, SimpleMusicService::class.java).apply { action = ACTION_PLAY },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSongTitle.ifEmpty { "Reproduciendo música" })
            .setContentText(currentSongArtist.ifEmpty { "Artista desconocido" })
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDeleteIntent(
                PendingIntent.getService(
                    this, 0,
                    Intent(this, SimpleMusicService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                android.R.drawable.ic_media_previous, "Anterior",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, SimpleMusicService::class.java).apply { action = ACTION_PREVIOUS },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(playPauseAction)
            .addAction(
                android.R.drawable.ic_media_next, "Siguiente",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, SimpleMusicService::class.java).apply { action = ACTION_NEXT },
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
        if (hasNotificationPermission()) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        abandonAudioFocus()
    }
}