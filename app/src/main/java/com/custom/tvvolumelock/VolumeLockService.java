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

    // MÁQUINA DE ESTADOS: Clave CH- -> CH- -> Hold CH+ (3s)
    private int pasoClave = 0;
    private long tiempoUltimoPaso = 0;
    private boolean esperandoHoldAbrir = false;

    // VINCULACIÓN EN VIVO
    private boolean modoVinculado = false;
    private boolean esperandoHoldVincular = false;
    private boolean chPlusSoltadoTrasAbrir = false;
    private int volumenPrevioVinculacion = -1;

    // ESCUDO DEL BOTÓN ATRÁS (Evita que la soltada del botón pase a Netflix)
    private boolean ignorarProximoBackUp = false;

    private WindowManager windowManager;
    private LinearLayout panelOverlay;
    private TextView textoValorOverlay;
    private TextView textoSubtitulo;
    private boolean overlayVisible = false;
    private int limiteTemporal = 25;

    private final Runnable abrirOverlayRunnable = new Runnable() {
        @Override
        public void run() {
            pasoClave = 0;
            esperandoHoldAbrir = false;
            mostrarOverlay();
        }
    };

    private final Runnable vincularRunnable = new Runnable() {
        @Override
        public void run() {
            if (overlayVisible && !modoVinculado) {
                activarVinculacion();
            }
        }
    };

    private final Runnable autoCierreRunnable = new Runnable() {
        @Override
        public void run() {
            cancelarYCerrar();
        }
    };

    private final ContentObserver volumeObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (!modoVinculado) {
                forzarLimite();
            }
        }
    };

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!modoVinculado) {
                forzarLimite();
            }
        }
    };

    // CENTINELA A 200 MS
    private final Runnable centinelaLoop = new Runnable() {
        @Override
        public void run() {
            if (servicioActivo) {
                if (!modoVinculado) {
                    forzarLimite();
                }
                handler.postDelayed(this, 200);
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
        panelOverlay.setFocusable(true);
        panelOverlay.setFocusableInTouchMode(true);

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

        textoSubtitulo = new TextView(this);
        textoSubtitulo.setText("OK Guardar   •   Atrás Cancelar");
        textoSubtitulo.setTextColor(Color.LTGRAY);
        textoSubtitulo.setTextSize(14);
        textoSubtitulo.setGravity(Gravity.CENTER);
        panelOverlay.addView(textoSubtitulo);
    }

    private void mostrarOverlay() {
        if (overlayVisible || windowManager == null || panelOverlay == null) return;

        limiteTemporal = obtenerLimite();
        modoVinculado = false;
        chPlusSoltadoTrasAbrir = false;
        esperandoHoldVincular = false;
        ignorarProximoBackUp = false;

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            volumenPrevioVinculacion = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        }

        actualizarVisualOverlay();

        // QUITADO FLAG_NOT_FOCUSABLE: Ahora la barrita sí tiene foco para capturar Atrás
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        try {
            windowManager.addView(panelOverlay, params);
            overlayVisible = true;
            panelOverlay.requestFocus();
            reiniciarTemporizador();
        } catch (Exception ignored) {}
    }

    private void cerrarOverlay() {
        if (!overlayVisible || windowManager == null || panelOverlay == null) return;
        handler.removeCallbacks(autoCierreRunnable);
        handler.removeCallbacks(vincularRunnable);
        try {
            windowManager.removeView(panelOverlay);
        } catch (Exception ignored) {}
        overlayVisible = false;
        modoVinculado = false;
    }

    private void activarVinculacion() {
        modoVinculado = true;
        esperandoHoldVincular = false;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            volumenPrevioVinculacion = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        aplicarAudioEnVivo(limiteTemporal);
        actualizarVisualOverlay();
        reiniciarTemporizador();
    }

    private void desvincularAudio() {
        if (modoVinculado) {
            modoVinculado = false;
            restaurarVolumenPrevio();
            actualizarVisualOverlay();
            reiniciarTemporizador();
        }
    }

    private void restaurarVolumenPrevio() {
        if (volumenPrevioVinculacion >= 0) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, volumenPrevioVinculacion, 0);
            }
        }
    }

    private void aplicarAudioEnVivo(int porcentaje) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            int maximo = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int nivel = (porcentaje * maximo) / 100;
            am.setStreamVolume(AudioManager.STREAM_MUSIC, nivel, 0);
        }
    }

    private void actualizarVisualOverlay() {
        if (modoVinculado) {
            textoValorOverlay.setText("◄   " + limiteTemporal + "%   ►  ●");
            textoValorOverlay.setTextColor(Color.parseColor("#00E5FF")); // Celeste
        } else {
            textoValorOverlay.setText("◄   " + limiteTemporal + "%   ►");
            textoValorOverlay.setTextColor(Color.parseColor("#00E676")); // Verde normal
        }
        textoSubtitulo.setText("OK Guardar   •   Atrás Cancelar");
        textoSubtitulo.setTextColor(Color.LTGRAY);
    }

    private void modificarLimite(int delta) {
        limiteTemporal += delta;
        if (limiteTemporal < 1) limiteTemporal = 1;
        if (limiteTemporal > 100) limiteTemporal = 100;

        actualizarVisualOverlay();

        if (modoVinculado) {
            aplicarAudioEnVivo(limiteTemporal);
        }
        reiniciarTemporizador();
    }

    private void guardarNuevoLimite() {
        SharedPreferences prefs = getSharedPreferences("volume_prefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("max_limit", limiteTemporal).apply();

        if (modoVinculado) {
            modoVinculado = false;
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int maximo = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int nuevoTecho = (limiteTemporal * maximo) / 100;
                int volumenSeguro = Math.min(volumenPrevioVinculacion, nuevoTecho);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, volumenSeguro, 0);
            }
        }

        cerrarOverlay();
        forzarLimite();
    }

    private void cancelarYCerrar() {
        if (modoVinculado) {
            desvincularAudio();
        }
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

    private boolean isChannelDown(int key) {
        return key == KeyEvent.KEYCODE_CHANNEL_DOWN || key == KeyEvent.KEYCODE_PAGE_DOWN;
    }

    private boolean isChannelUp(int key) {
        return key == KeyEvent.KEYCODE_CHANNEL_UP || key == KeyEvent.KEYCODE_PAGE_UP;
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();

        // 1. SI LA BARRITA ESTÁ EN PANTALLA
        if (overlayVisible) {
            // Manejo de la tecla suelta (ACTION_UP)
            if (event.getAction() == KeyEvent.ACTION_UP) {
                // Escudo para atrapar la soltada de ATRÁS y que no llegue a Netflix
                if (key == KeyEvent.KEYCODE_BACK && ignorarProximoBackUp) {
                    ignorarProximoBackUp = false;
                    return true;
                }

                if (isChannelUp(key)) {
                    chPlusSoltadoTrasAbrir = true;
                    if (esperandoHoldVincular) {
                        handler.removeCallbacks(vincularRunnable);
                        esperandoHoldVincular = false;
                    }
                }
                return true;
            }

            // Manejo de la tecla pulsada (ACTION_DOWN)
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                // AHORA SÍ: Captura real de Retroceder / Atrás
                if (key == KeyEvent.KEYCODE_BACK) {
                    ignorarProximoBackUp = true; // Prepara el escudo para el ACTION_UP
                    cancelarYCerrar();
                    return true;
                }

                // Flechas horizontales (±5%)
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (event.getRepeatCount() == 0) modificarLimite(-5);
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.getRepeatCount() == 0) modificarLimite(5);
                    return true;
                }

                // Flechas verticales (±1% punto a punto)
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.getRepeatCount() == 0) modificarLimite(-1);
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    if (event.getRepeatCount() == 0) modificarLimite(1);
                    return true;
                }

                // Guardar
                if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                    guardarNuevoLimite();
                    return true;
                }

                // Mantener presionado CH+ por 2s para vincular
                if (isChannelUp(key)) {
                    if (chPlusSoltadoTrasAbrir && !modoVinculado && event.getRepeatCount() == 0) {
                        esperandoHoldVincular = true;
                        handler.postDelayed(vincularRunnable, 2000);
                    }
                    return true;
                }

                // Un solo toque a CH- desvincula
                if (isChannelDown(key)) {
                    if (event.getRepeatCount() == 0 && modoVinculado) {
                        desvincularAudio();
                    }
                    return true;
                }
            }
            return true;
        }

        // 2. MÁQUINA DE ESTADOS: CH- -> CH- -> Hold CH+ (3s)
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            long ahora = System.currentTimeMillis();

            if (isChannelDown(key)) {
                if (event.getRepeatCount() == 0) {
                    if (ahora - tiempoUltimoPaso > 2000) {
                        pasoClave = 0;
                    }
                    tiempoUltimoPaso = ahora;

                    if (pasoClave == 0) {
                        pasoClave = 1;
                    } else if (pasoClave == 1) {
                        pasoClave = 2;
                    } else {
                        pasoClave = 1;
                    }
                }
            } else if (isChannelUp(key)) {
                if (pasoClave == 2 && (ahora - tiempoUltimoPaso <= 2000)) {
                    if (event.getRepeatCount() == 0) {
                        esperandoHoldAbrir = true;
                        handler.postDelayed(abrirOverlayRunnable, 3000);
                    }
                    return true;
                } else {
                    pasoClave = 0;
                    handler.removeCallbacks(abrirOverlayRunnable);
                    esperandoHoldAbrir = false;
                }
            } else {
                pasoClave = 0;
                handler.removeCallbacks(abrirOverlayRunnable);
                esperandoHoldAbrir = false;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (isChannelUp(key) && esperandoHoldAbrir) {
                handler.removeCallbacks(abrirOverlayRunnable);
                esperandoHoldAbrir = false;
                pasoClave = 0;
            }
        }

        // 3. FRENO REACTIVO INSTANTÁNEO
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!modoVinculado) {
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
        }

        return super.onKeyEvent(event);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        servicioActivo = false;
        handler.removeCallbacks(centinelaLoop);
        cancelarYCerrar();
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
