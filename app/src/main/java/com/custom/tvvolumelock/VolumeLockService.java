package com.custom.tvvolumelock;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class VolumeLockService extends AccessibilityService {

    // === AQUÍ DEFINES EL PORCENTAJE MÁXIMO (ejemplo: 25% o 30%) ===
    private static final int PORCENTAJE_MAXIMO = 25; 

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int volumenActual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int volumenMaximo = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

                // Calculamos el porcentaje real (de 0 a 100%)
                int porcentajeActual = (volumenActual * 100) / volumenMaximo;

                // Si ya llegó al porcentaje fijado (ej. 25%), corta la tecla:
                if (porcentajeActual >= PORCENTAJE_MAXIMO) {
                    return true;
                }
            }
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
