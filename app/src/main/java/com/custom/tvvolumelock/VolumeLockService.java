package com.custom.tvvolumelock;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class VolumeLockService extends AccessibilityService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean servicioActivo = true;

    // Código Secreto: Arriba, Abajo, Arriba, Abajo, Izquierda, Derecha
    private final int[] CODIGO_GLOBAL = {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT
    };
    private int indiceGlobal = 0;
    private long tiempoUltimaTecla = 0;

    private final ContentObserver volumeObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            forzarLimite();
        }
    };

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            forzarLimite();
        }
    };

    private final Runnable centinelaLoop = new Runnable() {
        @Override
        public void run() {
            if (servicioActivo) {
                forzarLimite();
                handler.postDelayed(this, 80);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }

        try {
            getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI, true, volumeObserver
            );
        } catch (Exception ignored) {}

        try {
            IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            registerReceiver(volumeReceiver, filter);
        } catch (Exception ignored) {}

        servicioActivo = true;
        handler.post(centinelaLoop);
    }

    private int obtenerLimite() {
        SharedPreferences prefs = getSharedPreferences("volume_prefs", Context.MODE_PRIVATE);
        return prefs.getInt("max_limit", 25);
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
        // 1. Escuchar la combinación secreta en cualquier lugar (solo al presionar hacia abajo)
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
                key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {

                long ahora = System.currentTimeMillis();
                if (ahora - tiempoUltimaTecla > 3500) {
                    indiceGlobal = 0;
                }
                tiempoUltimaTecla = ahora;

                if (key == CODIGO_GLOBAL[indiceGlobal]) {
                    indiceGlobal++;
                    if (indiceGlobal == CODIGO_GLOBAL.length) {
                        indiceGlobal = 0;
                        abrirPanelDesdeCualquierLugar();
                    }
                } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    indiceGlobal = 1;
                } else {
                    indiceGlobal = 0;
                }
            }
        }

        // 2. Bloqueo de volumen inquebrantable
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int limite = obtenerLimite();
                int maximo = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int porcentaje = (actual * 100) / maximo;

                if (porcentaje >= limite) {
                    forzarLimite();
                    return true;
                }
            }
        }
        return super.onKeyEvent(event);
    }

    private void abrirPanelDesdeCualquierLugar() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("ABRIR_DIRECTO", true);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        servicioActivo = false;
        handler.removeCallbacks(centinelaLoop);
        try {
            getContentResolver().unregisterContentObserver(volumeObserver);
            unregisterReceiver(volumeReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
