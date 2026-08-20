package com.example.contactapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.contactapp.R
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Low-power background listener for the Shake-to-Fake-Call feature: while "Enable Shake Trigger"
 * is on, this stays alive as a foreground service (required by Android since API 26 — a plain
 * background Service listening to sensors would be killed within minutes) and watches the
 * accelerometer for a vigorous shake pattern (3 sharp pulses within 2 seconds). Normal walking or
 * pocket jostling produces much smaller, slower acceleration swings than a deliberate shake, so it
 * doesn't false-trigger during ordinary movement.
 *
 * On detecting the pattern, it broadcasts directly to [FakeCallReceiver] — the exact same
 * delivery path (Telecom self-managed call + full-screen notification fallback) a scheduled fake
 * call already uses — using the "Default Caller" profile saved from the Fake Call setup screen.
 */
@AndroidEntryPoint
class ShakeDetectionService : Service(), SensorEventListener {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val pulseTimestamps = ArrayDeque<Long>()
    private var lastTriggerTime = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        startForegroundCompat()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart automatically if the OS kills this process under memory pressure — the whole
        // point of "set it and forget it" is that it keeps working without the user reopening
        // the app.
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val acceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat() - SensorManager.GRAVITY_EARTH

        // SHAKE_THRESHOLD is the only knob you need for "how hard do I have to shake" — lower it
        // for a lighter shake to count, raise it to require a firmer one. MIN_PULSE_INTERVAL_MS/
        // SHAKE_WINDOW_MS/REQUIRED_PULSES below control the *pattern* (how many pulses, how fast),
        // independent of how strong each individual pulse needs to be.
        if (acceleration > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            // Debounce: a single physical shake produces many sensor samples above threshold in a
            // row — only count one pulse per MIN_PULSE_INTERVAL_MS so 3 real shakes are required,
            // not 3 samples from the same shake.
            if (now - (pulseTimestamps.lastOrNull() ?: 0L) < MIN_PULSE_INTERVAL_MS) return

            pulseTimestamps.addLast(now)
            while (pulseTimestamps.isNotEmpty() && now - pulseTimestamps.first() > SHAKE_WINDOW_MS) {
                pulseTimestamps.removeFirst()
            }

            if (pulseTimestamps.size >= REQUIRED_PULSES && now - lastTriggerTime > TRIGGER_COOLDOWN_MS) {
                lastTriggerTime = now
                pulseTimestamps.clear()
                triggerFakeCall()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerFakeCall() {
        val name = preferenceManager.getShakeCallerName()
        val number = preferenceManager.getShakeCallerNumber()
        if (name.isBlank() || number.isBlank()) return

        val intent = Intent(this, FakeCallReceiver::class.java).apply {
            putExtra("caller_name", name)
            putExtra("caller_number", number)
        }
        sendBroadcast(intent)
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.shake_service_notification_channel),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.shake_service_notification_channel_desc)
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.shake_service_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "shake_trigger_service_channel"
        private const val NOTIFICATION_ID = 1301

        // Heuristic thresholds — tuned for "deliberate shake" vs. normal handling. Lower
        // SHAKE_THRESHOLD = less force needed per pulse (easier to trigger, more false positives
        // from normal handling); raise it for a firmer/more deliberate shake requirement.
        private const val SHAKE_THRESHOLD = 12f // m/s^2, net of gravity
        private const val MIN_PULSE_INTERVAL_MS = 100L
        private const val SHAKE_WINDOW_MS = 2000L
        private const val REQUIRED_PULSES = 3
        private const val TRIGGER_COOLDOWN_MS = 5000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ShakeDetectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShakeDetectionService::class.java))
        }
    }
}
