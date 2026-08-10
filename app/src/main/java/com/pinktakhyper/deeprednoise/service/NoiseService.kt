package com.pinktakhyper.deeprednoise.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Binder
import android.os.CountDownTimer
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.app.NotificationCompat.MediaStyle
import com.pinktakhyper.deeprednoise.MainActivity
import com.pinktakhyper.deeprednoise.R
import com.pinktakhyper.deeprednoise.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

class NoiseService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "red_noise_playback"
        private const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.pinktakhyper.deeprednoise.PLAY"
        const val ACTION_PAUSE = "com.pinktakhyper.deeprednoise.PAUSE"
        const val ACTION_STOP = "com.pinktakhyper.deeprednoise.STOP"

        private const val SAMPLE_RATE = 44100
        private const val BUFFER_FRAMES = 4096
    }

    inner class NoiseBinder : Binder() {
        fun getService(): NoiseService = this@NoiseService
    }

    private val binder = NoiseBinder()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _volume = MutableStateFlow(0.8f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _redness = MutableStateFlow(0.5f)
    val redness: StateFlow<Float> = _redness.asStateFlow()

    private val _timerMinutes = MutableStateFlow(0)
    val timerMinutes: StateFlow<Int> = _timerMinutes.asStateFlow()

    private val _timerSecondsRemaining = MutableStateFlow(0L)
    val timerSecondsRemaining: StateFlow<Long> = _timerSecondsRemaining.asStateFlow()

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    private lateinit var repository: SettingsRepository

    private var audioTrack: AudioTrack? = null
    private var audioJob: Job? = null

    @Volatile private var gainVolume = 0.8f
    @Volatile private var filterCoeff = 0.5f

    private var sleepTimer: CountDownTimer? = null

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        setupMediaSession()

        lifecycleScope.launch {
            repository.settings.collect { settings ->
                _volume.value = settings.volume
                _redness.value = settings.redness
                _timerMinutes.value = settings.timerMinutes
                gainVolume = settings.volume
                filterCoeff = rednessToCoeff(settings.redness)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        startForeground(NOTIFICATION_ID, buildNotification())
        updateMediaSession(playing = true)

        audioJob = lifecycleScope.launch(Dispatchers.IO) {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            val bufSize = maxOf(minBuf, BUFFER_FRAMES * 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val buffer = FloatArray(BUFFER_FRAMES)

            // Three cascaded single-pole IIR stages for a steep deep-red spectrum.
            var pole1 = 0.0
            var pole2 = 0.0
            var pole3 = 0.0

            // DC blocker state: removes slow drift without touching audible bass.
            // Beta lowered to 0.995 so the blocker passes deep low-freq content at high alpha.
            var dcX1 = 0.0
            var dcY1 = 0.0
            val dcBeta = 0.995

            // RMS gain rider: normalises signal to a loud but headroom-safe level.
            // Target RMS 0.70 (~-3 dBFS).
            // RMS envelope: fast attack ~0.1s, moderate release ~0.5s.
            // Rider itself smoothed with ~1s time constant so it converges in ~3s.
            val rmsTargetLevel = 0.70
            var rmsAccum = rmsTargetLevel * rmsTargetLevel  // pre-seed to avoid startup lurch
            var gainRider = 1.0
            val rmsAttack      = 1.0 - kotlin.math.exp(-1.0 / (0.1 * SAMPLE_RATE))
            val rmsRelease     = 1.0 - kotlin.math.exp(-1.0 / (0.5 * SAMPLE_RATE))
            val riderSmoothing = 1.0 - kotlin.math.exp(-1.0 / (1.0 * SAMPLE_RATE))

            while (_isPlaying.value) {
                val coeff = filterCoeff.toDouble()
                val gain = gainVolume.toDouble()

                for (i in buffer.indices) {
                    val white = Random.nextDouble(-1.0, 1.0)

                    // Cascade three poles — each applies the same IIR coefficient.
                    pole1 = (1.0 - coeff) * white + coeff * pole1
                    pole2 = (1.0 - coeff) * pole1 + coeff * pole2
                    pole3 = (1.0 - coeff) * pole2 + coeff * pole3

                    // DC blocker: y[n] = x[n] - x[n-1] + beta * y[n-1]
                    val blocked = pole3 - dcX1 + dcBeta * dcY1
                    dcX1 = pole3
                    dcY1 = blocked

                    // RMS gain rider.
                    val sq = blocked * blocked
                    val alpha = if (sq > rmsAccum) rmsAttack else rmsRelease
                    rmsAccum += alpha * (sq - rmsAccum)
                    val rms = kotlin.math.sqrt(rmsAccum.coerceAtLeast(1e-12))
                    val targetGain = (rmsTargetLevel / rms).coerceIn(0.01, 8.0)
                    gainRider += riderSmoothing * (targetGain - gainRider)

                    // Soft knee limiter: unity gain below 0.8, smoothly compressed to
                    // ceiling 1.0 above that. Eliminates hard clips without audible pumping.
                    val raw = blocked * gainRider * gain
                    val abs = kotlin.math.abs(raw)
                    val knee = 0.8
                    val limited = if (abs <= knee) {
                        raw
                    } else {
                        val over = abs - knee          // how far above the knee
                        val range = 1.0 - knee         // available headroom above knee
                        val compressed = knee + range * (1.0 - kotlin.math.exp(-over / range))
                        if (raw >= 0.0) compressed else -compressed
                    }
                    buffer[i] = limited.toFloat()
                }

                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }

            track.stop()
            track.release()
            audioTrack = null
        }

        val timerMins = _timerMinutes.value
        if (timerMins > 0) startSleepTimer(timerMins)
    }

    fun pause() {
        if (!_isPlaying.value) return
        cancelSleepTimer()
        _isPlaying.value = false
        audioJob?.cancel()
        updateMediaSession(playing = false)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    fun stop() {
        cancelSleepTimer()
        _isPlaying.value = false
        audioJob?.cancel()
        updateMediaSession(playing = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    fun setVolume(vol: Float) {
        _volume.value = vol
        gainVolume = vol
        lifecycleScope.launch { repository.saveVolume(vol) }
    }

    fun setRedness(red: Float) {
        _redness.value = red
        filterCoeff = rednessToCoeff(red)
        lifecycleScope.launch { repository.saveRedness(red) }
    }

    fun setTimer(minutes: Int) {
        _timerMinutes.value = minutes
        lifecycleScope.launch { repository.saveTimer(minutes) }
        if (_isPlaying.value) {
            cancelSleepTimer()
            if (minutes > 0) startSleepTimer(minutes)
        }
    }

    private fun startSleepTimer(minutes: Int) {
        val totalMillis = minutes * 60_000L
        sleepTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _timerSecondsRemaining.value = millisUntilFinished / 1000
            }
            override fun onFinish() {
                _timerSecondsRemaining.value = 0
                stop()
            }
        }.start()
    }

    private fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
        _timerSecondsRemaining.value = 0
    }

    private fun rednessToCoeff(redness: Float): Float {
        // Lower bound alpha=0.95 (lighter deep red), upper bound alpha=0.9893 (mid-point of
        // the previous 0.980-0.9985 range, which was about the practical loudness ceiling).
        return (0.95f + redness * 0.0393f).coerceIn(0.95f, 0.9893f)
    }


    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "RedNoiseSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onStop() = stop()
            })
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Red Noise")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Red Noise")
                    .build()
            )
            isActive = true
        }
    }

    private fun updateMediaSession(playing: Boolean) {
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .build()
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseAction = if (_isPlaying.value) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                getString(R.string.pause),
                buildActionIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                getString(R.string.play),
                buildActionIntent(ACTION_PLAY)
            )
        }

        val stopAction = NotificationCompat.Action(
            R.drawable.ic_stop,
            getString(R.string.stop),
            buildActionIntent(ACTION_STOP)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (_isPlaying.value) getString(R.string.playing) else getString(R.string.paused))
            .setSmallIcon(R.drawable.ic_noise)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(_isPlaying.value)
            .build()
    }

    private fun buildActionIntent(action: String): PendingIntent {
        val intent = Intent(this, NoiseService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onDestroy() {
        cancelSleepTimer()
        audioJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        mediaSession.release()
        super.onDestroy()
    }
}
