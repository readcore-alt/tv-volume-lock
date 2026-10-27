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
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.accessibility.AccessibilityEvent;

public class VolumeLockService extends AccessibilityService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean servicioActivo = true;

    // Código: Arriba, Abajo, Arriba, Abajo, Izquierda, Derecha
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

    // Elementos de la Ventana Flotante (Overlay)
    private WindowManager windowManager;
    private LinearLayout panelOverlay;
    private TextView textoValorOverlay;
    private boolean overlayVisible = false;
    private int limiteTemporal = 25;

    // Auto-cierre de seguridad a los 8 segundos si no tocan nada
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
        panelOverlay.setPadding(80, 50, 80, 50);

        // Fondo oscuro translúcido con esquinas redondeadas
        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(Color.parseColor("#E6080C1E")); // 90% opaco
        fondo.setCornerRadius(28f);
        fondo.setStroke(3, Color.parseColor("#33FFFFFF")); // Borde suave
        panelOverlay.setBackground(fondo);

        TextView titulo = new TextView(this);
        titulo.setText("LÍMITE DE VOLUMEN");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(20);
        titulo.setGravity(Gravity.CENTER);
        panelOverlay.addView(titulo);

        textoValorOverlay = new TextView(this);
        textoValorOverlay.setTextColor(Color.parseColor("#00E676"));
        textoValorOverlay.setTextSize(42);
        textoValorOverlay.setPadding(0, 24, 0, 24);
        textoValorOverlay.setGravity(Gravity.CENTER);
        panelOverlay.addView(textoValorOverlay);

        TextView subtitulo = new TextView(this);
        subtitulo.setText("◄ / ► Modificar   •   OK Guardar");
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
            reiniciarTemporizadorAutoCierre();
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

    private void reiniciarTemporizadorAutoCierre() {
        handler.removeCallbacks(autoCierreRunnable);
        handler.postDelayed(autoCierreRunnable, 8000); // 8 segundos
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
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();

            // MIENTRAS EL RECINTITO FLOTANTE ESTÁ EN PANTALLA:
            if (overlayVisible) {
                reiniciarTemporizadorAutoCierre();

                if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (limiteTemporal > 5) limiteTemporal -= 5;
                    textoValorOverlay.setText("◄   " + limiteTemporal + "%   ►");
                    return true; // No deja que la tecla llegue a Netflix
                } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (limiteTemporal < 100) limiteTemporal += 5;
                    textoValorOverlay.setText("◄   " + limiteTemporal + "%   ►");
                    return true;
                } else if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                    guardarNuevoLimite();
                    return true;
                } else if (key == KeyEvent.KEYCODE_BACK) {
                    cerrarOverlay();
                    return true;
                }
            }

            // DETECTOR DE LA CLAVE SECRETA:
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

        // BLOQUEO INQUEBRANTABLE DE VOLUMEN
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
