package com.custom.tvvolumelock;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class VolumeLockService extends AccessibilityService {

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
        // 0 eventos de UI: evita saturar la CPU de la tele en segundo plano
        info.eventTypes = 0; 
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);

        try {
            IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            registerReceiver(volumeReceiver, filter);
        } catch (Exception ignored) {}
    }

    private int obtenerLimite() {
        SharedPreferences prefs = getSharedPreferences("volume_prefs", Context.MODE_PRIVATE);
        return prefs.getInt("max_limit", 25); // Por defecto 25%
    }

    private void forzarLimite() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int limite = obtenerLimite();
            int maximo = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int porcentaje = (actual * 100) / maximo;

            if (porcentaje > limite) {
                int nivelTope = (limite * maximo) / 100;
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nivelTope, 0);
            }
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int limite = obtenerLimite();
                int maximo = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int porcentaje = (actual * 100) / maximo;

                if (porcentaje >= limite) {
                    forzarLimite();
                    return true; // Destruye la tecla instantáneamente
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
