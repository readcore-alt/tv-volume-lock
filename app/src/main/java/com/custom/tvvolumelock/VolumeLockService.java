package com.custom.tvvolumelock;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.accessibility.AccessibilityEvent;

public class VolumeLockService extends AccessibilityService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean servicioActivo = true;

    // Código: Arriba, Abajo, Arriba, Abajo, Arriba, Arriba
    private final int[] CODIGO_GLOBAL = {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_UP
    };
    private int indiceGlobal = 0;
    private long tiempoUltimaTecla = 0;

    private WindowManager windowManager;
    private LinearLayout panelOverlay;
    private TextView textoValorOverlay;
    private boolean overlayVisible = false;
    private int limiteTemporal = 25;

    private final Runnable autoCierreRunnable = new Runnable() {
        @Override
        public void run() {
            cerrarOverlay();
        }
    };

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

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        construirOverlayView();

        servicioActivo = true;
        handler.post(centinelaLoop);
    }

    private void construirOverlayView() {
        panelOverlay = new LinearLayout(this);
        panelOverlay.setOrientation(LinearLayout.VERTICAL);
        panelOverlay.setGravity(Gravity.CENTER);
        panelOverlay.setPadding(80, 45, 80, 45);

        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(Color.parseColor("#E6080C1E"));
        fondo.setCornerRadius(26f);
        fondo.setStroke(2, Color.parseColor("#33FFFFFF"));
        panelOverlay.setBackground(fondo);

        TextView titulo = new TextView(this);
        titulo.setText("LÍMITE DE VOLUMEN");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(19);
        titulo.setGravity(Gravity.CENTER);
        panelOverlay.addView(titulo);

        textoValorOverlay = new TextView(this);
        textoValorOverlay.setTextColor(Color.parseColor("#00E676"));
        textoValorOverlay.setTextSize(42);
        textoValorOverlay.setPadding(0, 20, 0, 20);
        textoValorOverlay.setGravity(Gravity.CENTER);
        panelOverlay.addView(textoValorOverlay);

        TextView subtitulo = new TextView(this);
        subtitulo.setText("OK Guardar   •   Atrás Cerrar");
        subtitulo.setTextColor(Color.LTGRAY);
        subtitulo.setTextSize(14);
        subtitulo.setGravity(Gravity.CENTER);
        panelOverlay.addView(subtitulo);
    }

    private void mostrarOverlay() {
        if (overlayVisible || windowManager == null || panelOverlay == null) return;

        limiteTemporal = obtenerLimite();
        textoValorOverlay.setText("◄   " + limiteTemporal + "%   ►");

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        try {
            windowManager.addView(panelOverlay, params);
            overlayVisible = true;
            reiniciarTemporizador();
        } catch (Exception ignored) {}
    }

    private void cerrarOverlay() {
        if (!overlayVisible || windowManager == null || panelOverlay == null) return;
        handler.removeCallbacks(autoCierreRunnable);
        try {
            windowManager.removeView(panelOverlay);
        } catch (Exception ignored) {}
        overlayVisible = false;
    }

    private void guardarNuevoLimite() {
        SharedPreferences prefs = getSharedPreferences("volume_prefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("max_limit", limiteTemporal).apply();
        cerrarOverlay();
        forzarLimite();
    }

    private void reiniciarTemporizador() {
        handler.removeCallbacks(autoCierreRunnable);
        handler.postDelayed(autoCierreRunnable, 8000);
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
        int key = event.getKeyCode();

        // 1. SI LA BARRITA ESTÁ EN PANTALLA:
        if (overlayVisible) {
            // Atrás solo cierra la barrita
            if (key == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    cerrarOverlay();
                }
                return true; 
            }

            // Izquierda mueve la barra
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    reiniciarTemporizador();
                    if (limiteTemporal > 5) limiteTemporal -= 5;
                    textoValorOverlay.setText("◄   " + limiteTemporal + "%   ►");
                }
                return true;
            }

            // Derecha mueve la barra
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    reiniciarTemporizador();
                    if (limiteTemporal < 100) limiteTemporal += 5;
                    textoValorOverlay.setText("◄   " + limiteTemporal + "%   ►");
                }
                return true;
            }

            // OK guarda
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    guardarNuevoLimite();
                }
                return true;
            }

            // IMPORTANTE: Si es Arriba, Abajo o cualquier otra tecla,
            // NO se bloquea y sigue su curso normal a la tele o la app de fondo.
        }

        // 2. DETECTOR DEL CÓDIGO
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                long ahora = System.currentTimeMillis();
                if (ahora - tiempoUltimaTecla > 3500) {
                    indiceGlobal = 0;
                }
                tiempoUltimaTecla = ahora;

                if (key == CODIGO_GLOBAL[indiceGlobal]) {
                    indiceGlobal++;
                    if (indiceGlobal == CODIGO_GLOBAL.length) {
                        indiceGlobal = 0;
                        mostrarOverlay();
                        return true;
                    }
                } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    indiceGlobal = 1;
                } else {
                    indiceGlobal = 0;
                }
            }
        }

        // 3. BLOQUEO DE VOLUMEN
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        servicioActivo = false;
        handler.removeCallbacks(centinelaLoop);
        cerrarOverlay();
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
