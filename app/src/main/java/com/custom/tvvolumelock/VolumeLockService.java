package com.custom.tvvolumelock;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class VolumeLockService extends AccessibilityService {

    private static final int PORCENTAJE_MAXIMO = 25; 

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Forzamos al sistema operativo por código a que nos entregue los botones físicos:
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);
        
        Toast.makeText(this, "Servicio de Bloqueo Conectado", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int maximo = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int porcentaje = (actual * 100) / maximo;

                // Mensaje en pantalla para ver qué está leyendo exactamente:
                Toast.makeText(this, "Vol: " + actual + "/" + maximo + " (" + porcentaje + "%)", Toast.LENGTH_SHORT).show();

                if (porcentaje >= PORCENTAJE_MAXIMO) {
                    // Forzamos el volumen al límite por si el hardware se adelantó:
                    int nivelTope = (PORCENTAJE_MAXIMO * maximo) / 100;
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nivelTope, 0);
                    return true; // Destruye la tecla
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
