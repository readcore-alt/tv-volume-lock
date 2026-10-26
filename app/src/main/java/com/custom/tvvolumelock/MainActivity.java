package com.custom.tvvolumelock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);

        TextView text = new TextView(this);
        text.setText("Bloqueador de Volumen TV instalado con éxito.\n\nPara activarlo, presiona el botón:");
        text.setTextSize(18);
        layout.addView(text);

        Button button = new Button(this);
        button.setText("Abrir Ajustes de Accesibilidad");
        button.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        layout.addView(button);

        setContentView(layout);
    }
}
