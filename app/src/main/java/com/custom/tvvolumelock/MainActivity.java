package com.custom.tvvolumelock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;

public class MainActivity extends Activity {

    // Código: Arriba, Abajo, Arriba, Abajo, Izquierda, Derecha
    private final int[] CODIGO_SECRETO = {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT
    };
    private int indiceCodigo = 0;
    private long tiempoUltimaFlecha = 0;

    private LinearLayout panelAjuste;
    private TextView textoPorcentaje;
    private int limiteTemporal = 25;
    private boolean panelAbierto = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        NightSkyView skyView = new NightSkyView(this);
        setContentView(skyView);

        crearPanelAjuste();
        procesarIntento(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        procesarIntento(intent);
    }

    private void procesarIntento(Intent intent) {
        if (intent != null && intent.getBooleanExtra("ABRIR_DIRECTO", false)) {
            abrirPanel();
        }
    }

    private void crearPanelAjuste() {
        panelAjuste = new LinearLayout(this);
        panelAjuste.setOrientation(LinearLayout.VERTICAL);
        panelAjuste.setBackgroundColor(Color.parseColor("#F0080C1E"));
        panelAjuste.setPadding(70, 50, 70, 50);
        panelAjuste.setGravity(Gravity.CENTER);
        panelAjuste.setVisibility(View.GONE);

        TextView titulo = new TextView(this);
        titulo.setText("LÍMITE DE VOLUMEN MÁXIMO");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(22);
        titulo.setGravity(Gravity.CENTER);
        panelAjuste.addView(titulo);

        textoPorcentaje = new TextView(this);
        textoPorcentaje.setTextColor(Color.parseColor("#00E676"));
        textoPorcentaje.setTextSize(44);
        textoPorcentaje.setPadding(0, 30, 0, 30);
        textoPorcentaje.setGravity(Gravity.CENTER);
        panelAjuste.addView(textoPorcentaje);

        TextView ayuda = new TextView(this);
        ayuda.setText("Usa las flechas ◄ / ► para modificar\nPresiona OK para guardar");
        ayuda.setTextColor(Color.LTGRAY);
        ayuda.setTextSize(16);
        ayuda.setGravity(Gravity.CENTER);
        panelAjuste.addView(ayuda);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.gravity = Gravity.CENTER;
        addContentView(panelAjuste, params);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (panelAbierto) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (limiteTemporal > 5) limiteTemporal -= 5;
                actualizarTextoPanel();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (limiteTemporal < 100) limiteTemporal += 5;
                actualizarTextoPanel();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                guardarYCerrar();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                panelAjuste.setVisibility(View.GONE);
                panelAbierto = false;
                return true;
            }
        }

        // Si estás dentro de la app y metes el código:
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {

            long ahora = System.currentTimeMillis();
            if (ahora - tiempoUltimaFlecha > 3500) {
                indiceCodigo = 0;
            }
            tiempoUltimaFlecha = ahora;

            if (keyCode == CODIGO_SECRETO[indiceCodigo]) {
                indiceCodigo++;
                if (indiceCodigo == CODIGO_SECRETO.length) {
                    indiceCodigo = 0;
                    abrirPanel();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                indiceCodigo = 1;
            } else {
                indiceCodigo = 0;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void abrirPanel() {
        SharedPreferences prefs = getSharedPreferences("volume_prefs", Context.MODE_PRIVATE);
        limiteTemporal = prefs.getInt("max_limit", 25);
        actualizarTextoPanel();
        panelAjuste.setVisibility(View.VISIBLE);
        panelAbierto = true;
    }

    private void actualizarTextoPanel() {
        textoPorcentaje.setText("◄   " + limiteTemporal + "%   ►");
    }

    private void guardarYCerrar() {
        SharedPreferences prefs = getSharedPreferences("volume_prefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("max_limit", limiteTemporal).apply();
        panelAjuste.setVisibility(View.GONE);
        panelAbierto = false;
    }

    private static class NightSkyView extends View {
        private final Paint starPaint = new Paint();
        private final Paint textPaint = new Paint();
        private final float[][] stars = new float[70][4];

        public NightSkyView(Context context) {
            super(context);
            starPaint.setColor(Color.WHITE);
            starPaint.setAntiAlias(true);

            textPaint.setColor(Color.argb(80, 255, 255, 255));
            textPaint.setTextSize(34);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setAntiAlias(true);

            Random random = new Random(101);
            for (int i = 0; i < stars.length; i++) {
                stars[i][0] = random.nextFloat();
                stars[i][1] = random.nextFloat();
                stars[i][2] = 1.0f + random.nextFloat() * 2.2f;
                stars[i][3] = random.nextFloat() * 6.28f;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.parseColor("#050813"));

            int w = getWidth();
            int h = getHeight();
            long time = System.currentTimeMillis();

            for (float[] star : stars) {
                float alpha = (float) ((Math.sin(time * 0.002 + star[3]) + 1.0) / 2.0);
                int alphaInt = 70 + (int) (alpha * 185);
                starPaint.setAlpha(alphaInt);
                canvas.drawCircle(star[0] * w, star[1] * h, star[2], starPaint);
            }

            canvas.drawText("Rest", w / 2f, h * 0.90f, textPaint);
            postInvalidateDelayed(60);
        }
    }
}
