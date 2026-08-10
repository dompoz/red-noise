package com.pinktakhyper.deeprednoise

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pinktakhyper.deeprednoise.service.NoiseService
import com.pinktakhyper.deeprednoise.ui.MainScreen
import com.pinktakhyper.deeprednoise.ui.RedNoiseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var noiseService: NoiseService? = null
    private var serviceBound by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as NoiseService.NoiseBinder
            noiseService = b.getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            noiseService = null
            serviceBound = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op; we don't require it to function */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        startAndBindService()

        setContent {
            RedNoiseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (serviceBound) {
                        val service = noiseService!!
                        val isPlaying by service.isPlaying.collectAsState()
                        val volume by service.volume.collectAsState()
                        val redness by service.redness.collectAsState()
                        val timerMinutes by service.timerMinutes.collectAsState()
                        val timerSecondsRemaining by service.timerSecondsRemaining.collectAsState()

                        MainScreen(
                            isPlaying = isPlaying,
                            volume = volume,
                            redness = redness,
                            timerMinutes = timerMinutes,
                            timerSecondsRemaining = timerSecondsRemaining,
                            onPlayPause = {
                                if (isPlaying) service.pause() else service.play()
                            },
                            onVolumeChange = { service.setVolume(it) },
                            onRednessChange = { service.setRedness(it) },
                            onTimerChange = { service.setTimer(it) },
                        )
                    }
                }
            }
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, NoiseService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
        super.onDestroy()
    }
}
