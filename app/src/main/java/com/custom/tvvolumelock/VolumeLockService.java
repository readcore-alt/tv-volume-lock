package com.custom.tvvolumelock;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class VolumeLockService extends AccessibilityService {

    // === AQUÍ ESTÁ EL LÍMITE (ejemplo: 25%) ===
    private static final int PORCENTAJE_MAXIMO = 25; 

    // ESCUDO 2: Vigilante en segundo plano si alguien intenta forzar el volumen
    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            forzarLimite();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);

        // Activamos el vigilante de cambios de volumen
        try {
            IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            registerReceiver(volumeReceiver, filter);
        } catch (Exception ignored) {}
    }

    private void forzarLimite() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int maximo = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int porcentaje = (actual * 100) / maximo;

            if (porcentaje > PORCENTAJE_MAXIMO) {
                int nivelTope = (PORCENTAJE_MAXIMO * maximo) / 100;
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nivelTope, 0);
            }
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        // ESCUDO 1: Destruye la tecla instantáneamente (sin carteles de texto que causen lag)
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int maximo = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int porcentaje = (actual * 100) / maximo;

                // Si ya llegó al límite o lo supera, destruye cualquier señal (pulsada o mantenida)
                if (porcentaje >= PORCENTAJE_MAXIMO) {
                    forzarLimite();
                    return true; // <-- Bloqueo total inmediato
                }
            }
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(volumeReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
