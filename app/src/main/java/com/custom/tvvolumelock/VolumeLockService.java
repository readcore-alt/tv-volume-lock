package com.custom.tvvolumelock;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class VolumeLockService extends AccessibilityService {

    // === AQUÍ DEFINES EL LÍMITE MÁXIMO DE VOLUMEN (0 a 100) ===
    private static final int LIMITE_MAXIMO = 25; 

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int volumenActual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

                // Si ya llegó al límite o lo supera, destruye la señal (retorna true)
                if (volumenActual >= LIMITE_MAXIMO) {
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
