package com.custom.tvvolumelock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import java.util.Random;

public class MainActivity extends Activity {

    private int clickCount = 0;
    private long lastClickTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NightSkyView skyView = new NightSkyView(this);

        // PUERTA TRASERA SECRETA:
        // Si tú presionas 5 veces seguidas el botón OK del control (o la pantalla),
        // se abre directamente el menú de Accesibilidad para activar el servicio.
        skyView.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastClickTime < 1500) {
                clickCount++;
                if (clickCount >= 5) {
                    clickCount = 0;
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception ignored) {}
                }
            } else {
                clickCount = 1;
            }
            lastClickTime = now;
        });

        setContentView(skyView);
    }

    // Vista ultra liviana: dibuja estrellas fijas directas en memoria (0% uso de CPU)
    private static class NightSkyView extends View {
        private final Paint starPaint = new Paint();
        private final Paint textPaint = new Paint();
        private final float[][] stars = new float[60][3];

        public NightSkyView(Context context) {
            super(context);
            starPaint.setColor(Color.WHITE);
            starPaint.setAntiAlias(true);

            textPaint.setColor(Color.argb(80, 255, 255, 255));
            textPaint.setTextSize(36);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setAntiAlias(true);

            // Genera la posición de 60 estrellas fijas
            Random random = new Random(4242);
            for (int i = 0; i < stars.length; i++) {
                stars[i][0] = random.nextFloat(); // Posición X
                stars[i][1] = random.nextFloat(); // Posición Y
                stars[i][2] = 1.2f + random.nextFloat() * 2.5f; // Tamaño
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // Fondo azul noche profundo
            canvas.drawColor(Color.parseColor("#060A17"));

            int w = getWidth();
            int h = getHeight();

            // Dibujar las estrellas estáticas
            for (float[] star : stars) {
                canvas.drawCircle(star[0] * w, star[1] * h, star[2], starPaint);
            }

            // Nombre tenue de descanso en la parte inferior
            canvas.drawText("Sleep", w / 2f, h * 0.90f, textPaint);
        }
    }
}
