package com.custom.tvvolumelock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.util.Random;

public class MainActivity extends Activity {

    private int clickCount = 0;
    private long lastClickTime = 0;
    private NightSkyView skyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pantalla completa total (sin barra superior de Android)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        skyView = new NightSkyView(this);
        setContentView(skyView);
    }

    // Captura cualquier toque físico en el cristal (móvil)
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            registrarToqueSecreto();
        }
        return super.dispatchTouchEvent(ev);
    }

    // Captura el botón OK / Enter del control remoto (TV)
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            registrarToqueSecreto();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // Contador silencioso: no muestra avisos ni pistas
    private void registrarToqueSecreto() {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < 2500) {
            clickCount++;
        } else {
            clickCount = 1;
        }
        lastClickTime = now;

        // Solo salta si llegas a 5 toques seguidos
        if (clickCount >= 5) {
            clickCount = 0;
            try {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {}
            }
        }
    }

    // Vista decorativa del cielo nocturno con titileo ultra liviano
    private static class NightSkyView extends View {
        private final Paint starPaint = new Paint();
        private final Paint textPaint = new Paint();
        private final float[][] stars = new float[70][4]; // X, Y, Radio base, Fase

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
            // Fondo azul noche profundo
            canvas.drawColor(Color.parseColor("#050813"));

            int w = getWidth();
            int h = getHeight();
            long time = System.currentTimeMillis();

            // Dibujar las estrellas con brillo suave
            for (float[] star : stars) {
                float alpha = (float) ((Math.sin(time * 0.002 + star[3]) + 1.0) / 2.0);
                int alphaInt = 70 + (int) (alpha * 185);
                starPaint.setAlpha(alphaInt);
                canvas.drawCircle(star[0] * w, star[1] * h, star[2], starPaint);
            }

            // Texto de descanso discreto en la parte inferior
            canvas.drawText("Rest", w / 2f, h * 0.90f, textPaint);

            // Redibujo suave cada 60ms para no calentar ni gastar energía
            postInvalidateDelayed(60);
        }
    }
}
