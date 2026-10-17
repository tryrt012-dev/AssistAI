package com.assist.ai;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
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

public class ScreenReaderService extends AccessibilityService {

    private WindowManager windowManager;
    private View floatingView;
    private LinearLayout menuLayout;
    private TextView bubbleBtn;
    private TextView resultText;
    private EditText inputApiKey;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = getSharedPreferences("AssistAIPrefs", MODE_PRIVATE);
        initFloatingOverlay();
    }

    private void initFloatingOverlay() {
        if (windowManager != null && floatingView != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);

        // Gelembung Utama
        bubbleBtn = new TextView(this);
        bubbleBtn.setText("✦ BACA LAYAR");
        bubbleBtn.setTextColor(Color.WHITE);
        bubbleBtn.setTextSize(13f);
        bubbleBtn.setGravity(Gravity.CENTER);
        bubbleBtn.setPadding(28, 20, 28, 20);

        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setColor(Color.parseColor("#4F46E5"));
        bubbleBg.setCornerRadius(100f);
        bubbleBg.setStroke(3, Color.WHITE);
        bubbleBtn.setBackground(bubbleBg);

        // Menu Hasil
        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setPadding(28, 28, 28, 28);
        menuLayout.setVisibility(View.GONE);

        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(Color.parseColor("#181825"));
        menuBg.setCornerRadius(32f);
        menuBg.setStroke(3, Color.parseColor("#6366F1"));
        menuLayout.setBackground(menuBg);

        inputApiKey = new EditText(this);
        inputApiKey.setHint("Tempel API Key di sini (cukup sekali)...");
        inputApiKey.setHintTextColor(Color.GRAY);
        inputApiKey.setTextColor(Color.parseColor("#A6E3A1"));
        inputApiKey.setTextSize(11f);
        inputApiKey.setSingleLine(true);
        inputApiKey.setText(prefs.getString("api_key", ""));

        ScrollView scrollResult = new ScrollView(this);
        resultText = new TextView(this);
        resultText.setText("Tekan tombol di gelembung untuk scan layar tugas.");
        resultText.setTextColor(Color.parseColor("#CDD6F4"));
        resultText.setTextSize(13f);
        resultText.setPadding(8, 16, 8, 16);
        scrollResult.addView(resultText);

        LinearLayout rowButtons = new LinearLayout(this);
        rowButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCopy = new Button(this);
        btnCopy.setText("Salin");
        btnCopy.setTextSize(11f);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("AI", resultText.getText().toString()));
            Toast.makeText(this, "Jawaban disalin!", Toast.LENGTH_SHORT).show();
        });

        Button btnHide = new Button(this);
        btnHide.setText("Tutup");
        btnHide.setTextSize(11f);
        btnHide.setOnClickListener(v -> toggleMenu(false));

        rowButtons.addView(btnCopy);
        rowButtons.addView(btnHide);

        menuLayout.addView(inputApiKey);
        menuLayout.addView(scrollResult, new LinearLayout.LayoutParams(760, 420));
        menuLayout.addView(rowButtons);

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
                        if (System.currentTimeMillis() - touchStartTime < 250) {
                            // JIKA DIKLIK SEKALI: BACA LAYAR DAN JAWAB OTOMATIS!
                            bacaDanJawabLayar();
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

    // FUNGSI INTI: Membaca seluruh teks yang sedang aktif di layar HP
    private void bacaDanJawabLayar() {
        toggleMenu(true);
        bubbleBtn.setText("⏳ Memindai...");
        resultText.setText("Sedang membaca teks dari layar...");

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            bubbleBtn.setText("✦ BACA LAYAR");
            resultText.setText("Gagal membaca layar. Pastikan layar tugas sedang terbuka di depan!");
            return;
        }

        StringBuilder screenContent = new StringBuilder();
        extractText(rootNode, screenContent);

        String contextText = screenContent.toString().trim();
        if (contextText.isEmpty()) {
            bubbleBtn.setText("✦ BACA LAYAR");
            resultText.setText("Tidak ada teks yang terdeteksi di layar saat ini.");
            return;
        }

        resultText.setText("Teks layar terbaca! Sedang mengirim ke AI...\n\nPreview isi layar:\n" + 
                (contextText.length() > 150 ? contextText.substring(0, 150) + "..." : contextText));

        kirimKeAI(contextText);
    }

    private void extractText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null && node.getText().length() > 0) {
            sb.append(node.getText().toString()).append("\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            extractText(node.getChild(i), sb);
        }
    }

    private void kirimKeAI(String kontenLayar) {
        String apiKey = inputApiKey.getText().toString().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Isi dulu API Key di baris atas!", Toast.LENGTH_SHORT).show();
            bubbleBtn.setText("✦ BACA LAYAR");
            return;
        }
        prefs.edit().putString("api_key", apiKey).apply();

        executor.execute(() -> {
            try {
                String promptInstruksi = "Kamu adalah asisten tugas. Berikut adalah teks lengkap yang diambil langsung dari layar HP pengguna (termasuk soal ujian/tugas dan pilihannya):\n\n"
                        + kontenLayar + "\n\n"
                        + "Tugasmu: Temukan pertanyaan tugas yang ada di teks di atas, lalu berikan jawaban yang benar, singkat, dan tepat.";

                boolean isGemini = apiKey.startsWith("AIza");
                URL url;
                String payloadStr;

                if (isGemini) {
                    url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey);
                    JSONObject part = new JSONObject().put("text", promptInstruksi);
                    JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
                    payloadStr = new JSONObject().put("contents", new JSONArray().put(content)).toString();
                } else {
                    url = new URL("https://api.openai.com/v1/chat/completions");
                    JSONObject message = new JSONObject().put("role", "user").put("content", promptInstruksi);
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

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()
                ));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);

                JSONObject jsonRes = new JSONObject(response.toString());

                if (code >= 200 && code < 300) {
                    String aiReply = isGemini ? 
                            jsonRes.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                            : jsonRes.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

                    mainHandler.post(() -> {
                        bubbleBtn.setText("✓ Selesai");
                        resultText.setText(aiReply);
                        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cb.setPrimaryClip(ClipData.newPlainText("AI", aiReply));
                        Toast.makeText(ScreenReaderService.this, "Jawaban otomatis tersalin ke Clipboard!", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    mainHandler.post(() -> {
                        bubbleBtn.setText("⚠ Eror");
                        resultText.setText("Eror API (" + code + "):\n" + response);
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    bubbleBtn.setText("⚠ Eror");
                    resultText.setText("Koneksi gagal: " + e.getMessage());
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}
