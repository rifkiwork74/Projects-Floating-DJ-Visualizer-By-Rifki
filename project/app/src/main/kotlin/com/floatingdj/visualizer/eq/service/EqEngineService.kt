package com.floatingdj.visualizer.eq.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.floatingdj.visualizer.EqualizerActivity
import com.floatingdj.visualizer.FloatingVisualizerService
import com.floatingdj.visualizer.eq.core.EqEngineController
import com.floatingdj.visualizer.eq.data.EqPresetRepository

/** User-visible lifecycle owner that keeps the selected output-mix experiment alive. */
class EqEngineService : Service() {
    companion object {
        const val ACTION_STOP = "com.floatingdj.visualizer.eq.STOP"
        private const val CHANNEL = "eq_engine_status"
        private const val NOTIFICATION_ID = 4300
    }
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Equalizer Engine", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Status audio effect engine"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("eq_presets_v1", MODE_PRIVATE)
        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean("engine_enabled", false).apply()
            EqEngineController.disableAll()
            EqEngineController.release()
            stopForeground(true)
            stopSelf()
            refreshVisualizerNotification()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (prefs.getBoolean("engine_enabled", false)) {
            val activated = try { EqEngineController.activate() } catch (_: Throwable) { false }
            if (!activated || !EqEngineController.hasControl()) {
                prefs.edit().putBoolean("engine_enabled", false).apply()
                EqEngineController.disableAll()
                EqEngineController.release()
                stopForeground(true)
                stopSelf(startId)
                refreshVisualizerNotification()
                return START_NOT_STICKY
            }
            EqEngineController.setEnabled(true)
            // Rebuild native effect handles from persisted working state after service/process restart.
            EqPresetRepository(this).loadCurrentState()?.let { profile ->
                val bands = EqEngineController.bands()
                val points = profile.centersHz.zip(profile.gainDb).filter { it.first > 0 }.sortedBy { it.first }
                bands.forEach { band ->
                    val exact = points.firstOrNull { it.first == band.centerHz }
                    val gain = if (exact != null) exact.second else if (points.isNotEmpty() && points.size <= bands.size) {
                        val lower = points.lastOrNull { it.first < band.centerHz }
                        val upper = points.firstOrNull { it.first > band.centerHz }
                        when {
                            lower == null -> points.first().second
                            upper == null -> points.last().second
                            else -> {
                                val x = kotlin.math.ln(band.centerHz.toDouble())
                                val x0 = kotlin.math.ln(lower.first.toDouble())
                                val x1 = kotlin.math.ln(upper.first.toDouble())
                                val t = if (x1 == x0) 0.0 else (x - x0) / (x1 - x0)
                                lower.second + (upper.second - lower.second) * t.toFloat()
                            }
                        }
                    } else null
                    if (gain != null) EqEngineController.setBand(band.index, (gain * 100f).toInt())
                }
                EqEngineController.setPreampDb(if (profile.preampEnabled) profile.preampDb else 0f)
                EqEngineController.setBassStrength(profile.bassStrength)
                EqEngineController.setBassEnabled(profile.bassEnabled)
                EqEngineController.setVirtualizerStrength(profile.virtualizerStrength)
                EqEngineController.setVirtualizerEnabled(profile.virtualizerEnabled)
                EqEngineController.setLimiterEnabled(profile.limiterEnabled)
            }
        } else {
            EqEngineController.disableAll()
            EqEngineController.release()
            stopForeground(true)
            stopSelf(startId)
            refreshVisualizerNotification()
            return START_NOT_STICKY
        }
        refreshVisualizerNotification()
        return START_STICKY
    }
    private fun refreshVisualizerNotification() {
        val hostPrefs = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        val running = hostPrefs.getBoolean("service_active", false) || hostPrefs.getBoolean("service_paused", false)
        if (!running) return
        try {
            startService(Intent(this, FloatingVisualizerService::class.java).setAction(FloatingVisualizerService.ACTION_REFRESH_NOTIFICATION))
        } catch (_: Throwable) {
            // The EQ engine must never fail because the visualizer notification is unavailable.
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 4301, Intent(this, EqualizerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Floating DJ · EQ Engine")
            .setContentText("Output-mix effect is experimental and device-dependent")
            .setContentIntent(open).setOngoing(true).build()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        // Release native effect handles exactly once when the lifecycle owner is stopped.
        EqEngineController.disableAll()
        EqEngineController.release()
        refreshVisualizerNotification()
        super.onDestroy()
    }
}
