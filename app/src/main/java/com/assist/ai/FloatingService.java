package com.assist.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private LinearLayout menuLayout;
    private TextView bubbleBtn;
    private TextView resultText;
    private EditText inputPrompt;
    private EditText inputApiKey;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startMyForeground();
        prefs = getSharedPreferences("AssistAIPrefs", MODE_PRIVATE);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);

        bubbleBtn = new TextView(this);
        bubbleBtn.setText("✦ AI");
        bubbleBtn.setTextColor(Color.WHITE);
        bubbleBtn.setTextSize(14f);
        bubbleBtn.setGravity(Gravity.CENTER);
        bubbleBtn.setPadding(34, 24, 34, 24);

        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setColor(Color.parseColor("#4F46E5"));
        bubbleBg.setCornerRadius(100f);
        bubbleBg.setStroke(3, Color.WHITE);
        bubbleBtn.setBackground(bubbleBg);

        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setPadding(32, 32, 32, 32);
        menuLayout.setVisibility(View.GONE);

        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(Color.parseColor("#181825"));
        menuBg.setCornerRadius(36f);
        menuBg.setStroke(3, Color.parseColor("#6366F1"));
        menuLayout.setBackground(menuBg);

        inputApiKey = new EditText(this);
        inputApiKey.setHint("Tempel API Key di sini...");
        inputApiKey.setHintTextColor(Color.GRAY);
        inputApiKey.setTextColor(Color.parseColor("#A6E3A1"));
        inputApiKey.setTextSize(11f);
        inputApiKey.setSingleLine(true);
        inputApiKey.setText(prefs.getString("api_key", ""));

        inputPrompt = new EditText(this);
        inputPrompt.setHint("Ketik soal atau klik 'Tempel Soal'...");
        inputPrompt.setHintTextColor(Color.LTGRAY);
        inputPrompt.setTextColor(Color.WHITE);
        inputPrompt.setTextSize(13f);
        inputPrompt.setMinLines(2);
        inputPrompt.setMaxLines(4);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPaste = new Button(this);
        btnPaste.setText("📋 Tempel Soal");
        btnPaste.setTextSize(11f);
        btnPaste.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cb.getPrimaryClip().getItemAt(0).getText();
                if (text != null) inputPrompt.setText(text.toString());
            }
        });

        Button btnGForm = new Button(this);
        btnGForm.setText("⚡ Jawab Cepat");
        btnGForm.setTextSize(11f);
        btnGForm.setOnClickListener(v -> kirimKeAI("Jawab soal berikut secara akurat, singkat, dan langsung pada jawaban intinya (untuk pengisian Google Form): \n"));

        Button btnFormat = new Button(this);
        btnFormat.setText("📝 Format Tugas");
        btnFormat.setTextSize(11f);
        btnFormat.setOnClickListener(v -> kirimKeAI("Jawab tugas berikut dengan susunan rapi, terstruktur, dan siap salin ke dokumen: \n"));

        row1.addView(btnPaste);
        row1.addView(btnGForm);
        row1.addView(btnFormat);

        ScrollView scrollResult = new ScrollView(this);
        resultText = new TextView(this);
        resultText.setText("Jawaban AI akan tampil di sini...");
        resultText.setTextColor(Color.parseColor("#CDD6F4"));
        resultText.setTextSize(13f);
        resultText.setPadding(8, 16, 8, 16);
        scrollResult.addView(resultText);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCopy = new Button(this);
        btnCopy.setText("Salin Jawaban");
        btnCopy.setTextSize(11f);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("AI", resultText.getText().toString()));
            Toast.makeText(this, "Disalin ke Clipboard!", Toast.LENGTH_SHORT).show();
        });

        Button btnHide = new Button(this);
        btnHide.setText("Tutup Menu");
        btnHide.setTextSize(11f);
        btnHide.setOnClickListener(v -> toggleMenu(false));

        Button btnStop = new Button(this);
        btnStop.setText("Matikan");
        btnStop.setTextSize(11f);
        btnStop.setOnClickListener(v -> stopSelf());

        row2.addView(btnCopy);
        row2.addView(btnHide);
        row2.addView(btnStop);

        menuLayout.addView(inputApiKey);
        menuLayout.addView(inputPrompt);
        menuLayout.addView(row1);
        menuLayout.addView(scrollResult, new LinearLayout.LayoutParams(780, 400));
        menuLayout.addView(row2);

        rootContainer.addView(bubbleBtn);
        rootContainer.addView(menuLayout);
        floatingView = rootContainer;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 300;

        windowManager.addView(floatingView, params);

        bubbleBtn.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (System.currentTimeMillis() - touchStartTime < 200) {
                            toggleMenu(menuLayout.getVisibility() == View.GONE);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void toggleMenu(boolean show) {
        if (show) {
            menuLayout.setVisibility(View.VISIBLE);
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        } else {
            menuLayout.setVisibility(View.GONE);
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        windowManager.updateViewLayout(floatingView, params);
    }

    private void kirimKeAI(String instruksi) {
        String apiKey = inputApiKey.getText().toString().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Isi dulu API Key di baris atas!", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString("api_key", apiKey).apply();

        String soal = inputPrompt.getText().toString().trim();
        if (soal.isEmpty()) {
            Toast.makeText(this, "Soal masih kosong!", Toast.LENGTH_SHORT).show();
            return;
        }
        resultText.setText("Sedang memproses jawaban...");

        executor.execute(() -> {
            try {
                boolean isGemini = apiKey.startsWith("AIza");
                URL url;
                String payloadStr;

                if (isGemini) {
                    url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey);
                    JSONObject part = new JSONObject().put("text", instruksi + soal);
                    JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
                    payloadStr = new JSONObject().put("contents", new JSONArray().put(content)).toString();
                } else {
                    // Endpoint standar OpenAI / compatible
                    url = new URL("https://api.openai.com/v1/chat/completions");
                    JSONObject message = new JSONObject().put("role", "user").put("content", instruksi + soal);
                    payloadStr = new JSONObject()
                            .put("model", "gpt-4o-mini")
                            .put("messages", new JSONArray().put(message))
                            .toString();
                }

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                if (!isGemini) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payloadStr.getBytes("UTF-8"));
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(
                        conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream()
                ));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);

                JSONObject jsonRes = new JSONObject(response.toString());
                String aiReply;
                if (isGemini) {
                    aiReply = jsonRes.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");
                } else {
                    aiReply = jsonRes.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                }

                mainHandler.post(() -> resultText.setText(aiReply));
            } catch (Exception e) {
                mainHandler.post(() -> resultText.setText("Gagal: " + e.getMessage()));
            }
        });
    }

    private void startMyForeground() {
        String channelId = "assist_ai_service";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Assist AI Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, channelId)
                    .setContentTitle("Assist AI Berjalan di Latar Belakang")
                    .setContentText("Ketuk gelembung ✦ AI di layar untuk bertanya")
                    .setSmallIcon(android.R.drawable.btn_star_big_on)
                    .build();
            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}
