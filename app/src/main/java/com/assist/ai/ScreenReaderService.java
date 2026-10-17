package com.assist.ai;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenReaderService extends AccessibilityService {

    private WindowManager windowManager;
    private LinearLayout rootContainer;
    private LinearLayout menuCard;
    private LinearLayout statusBanner;
    private TextView statusText;
    private ProgressBar progressBar;
    private TextView mascotView;
    private TextView explanationBox;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private boolean isStealthMode = false;

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
        initFloatingUI();
    }

    private void initFloatingUI() {
        if (windowManager != null && rootContainer != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setGravity(Gravity.CENTER_HORIZONTAL);

        menuCard = new LinearLayout(this);
        menuCard.setOrientation(LinearLayout.VERTICAL);
        menuCard.setPadding(36, 36, 36, 28);
        menuCard.setVisibility(View.GONE);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(42f);
        cardBg.setStroke(2, Color.parseColor("#E2E8F0"));
        menuCard.setBackground(cardBg);

        menuCard.addView(createMenuItem("✨", "Jawab soal otomatis", v -> {
            menuCard.setVisibility(View.GONE);
            jalankanAI("AUTO_PILGAN");
        }));

        menuCard.addView(createMenuItem("✏️", "Jawaban diisi AI", v -> {
            menuCard.setVisibility(View.GONE);
            jalankanAI("AUTO_ESAI");
        }));

        menuCard.addView(createMenuItem("🎯", "Auto nilai di atas KKM", v -> {
            menuCard.setVisibility(View.GONE);
            jalankanAI("AUTO_KKM");
        }));

        menuCard.addView(createMenuItem("📖", "Penjelasan lengkap", v -> {
            jalankanAI("PENJELASAN");
        }));

        menuCard.addView(createMenuItem("🔑", "Atur Kunci API", v -> {
            showApiKeyDialog();
        }));

        LinearLayout stealthRow = new LinearLayout(this);
        stealthRow.setOrientation(LinearLayout.HORIZONTAL);
        stealthRow.setGravity(Gravity.CENTER_VERTICAL);
        stealthRow.setPadding(16, 22, 16, 22);

        TextView stealthIcon = new TextView(this);
        stealthIcon.setText("👁️‍🗨️");
        stealthIcon.setTextSize(16f);
        stealthIcon.setPadding(18, 12, 28, 12);

        TextView stealthLabel = new TextView(this);
        stealthLabel.setText("Mode samaran");
        stealthLabel.setTextColor(Color.parseColor("#1E293B"));
        stealthLabel.setTextSize(14f);
        stealthLabel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        stealthLabel.setLayoutParams(lpLabel);

        Switch stealthSwitch = new Switch(this);
        stealthSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            isStealthMode = isChecked;
            mascotView.setAlpha(isChecked ? 0.25f : 1.0f);
            statusBanner.setAlpha(isChecked ? 0.35f : 1.0f);
        });

        stealthRow.addView(stealthIcon);
        stealthRow.addView(stealthLabel);
        stealthRow.addView(stealthSwitch);
        menuCard.addView(stealthRow);

        explanationBox = new TextView(this);
        explanationBox.setTextColor(Color.parseColor("#334155"));
        explanationBox.setTextSize(12f);
        explanationBox.setPadding(20, 16, 20, 16);
        explanationBox.setVisibility(View.GONE);
        menuCard.addView(explanationBox);

        TextView footer = new TextView(this);
        footer.setText("ASSIST AI • OMNIKEY QWEN");
        footer.setTextColor(Color.parseColor("#94A3B8"));
        footer.setTextSize(10f);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 14, 0, 4);
        menuCard.addView(footer);

        mascotView = new TextView(this);
        mascotView.setText("🎓\n🤖");
        mascotView.setTextSize(22f);
        mascotView.setGravity(Gravity.CENTER);
        mascotView.setPadding(24, 16, 24, 16);

        GradientDrawable mascotBg = new GradientDrawable();
        mascotBg.setColor(Color.parseColor("#EEF2FF"));
        mascotBg.setCornerRadius(100f);
        mascotBg.setStroke(4, Color.parseColor("#4F46E5"));
        mascotView.setBackground(mascotBg);

        statusBanner = new LinearLayout(this);
        statusBanner.setOrientation(LinearLayout.VERTICAL);
        statusBanner.setPadding(36, 20, 36, 20);
        statusBanner.setVisibility(View.GONE);

        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(Color.WHITE);
        bannerBg.setCornerRadius(30f);
        bannerBg.setStroke(2, Color.parseColor("#CBD5E1"));
        statusBanner.setBackground(bannerBg);

        statusText = new TextView(this);
        statusText.setText("✍️ Mengerjakan soal");
        statusText.setTextColor(Color.parseColor("#1E293B"));
        statusText.setTextSize(13f);
        statusText.setTypeface(null, Typeface.BOLD);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setPadding(0, 10, 0, 0);

        statusBanner.addView(statusText);
        statusBanner.addView(progressBar, new LinearLayout.LayoutParams(420, 24));

        rootContainer.addView(menuCard, new LinearLayout.LayoutParams(680, LinearLayout.LayoutParams.WRAP_CONTENT));
        rootContainer.addView(mascotView);
        rootContainer.addView(statusBanner);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 150;
        params.y = 450;

        windowManager.addView(rootContainer, params);

        mascotView.setOnTouchListener(new View.OnTouchListener() {
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
                        windowManager.updateViewLayout(rootContainer, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (System.currentTimeMillis() - touchStartTime < 220) {
                            boolean show = menuCard.getVisibility() == View.GONE;
                            menuCard.setVisibility(show ? View.VISIBLE : View.GONE);
                            explanationBox.setVisibility(View.GONE);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private View createMenuItem(String emoji, String title, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(16, 22, 16, 22);

        TextView icon = new TextView(this);
        icon.setText(emoji);
        icon.setTextSize(16f);
        icon.setGravity(Gravity.CENTER);
        icon.setPadding(18, 12, 28, 12);

        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.parseColor("#F1F5F9"));
        iconBg.setCornerRadius(20f);
        icon.setBackground(iconBg);

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.parseColor("#1E293B"));
        label.setTextSize(14f);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(24, 0, 0, 0);

        row.addView(icon);
        row.addView(label);
        row.setOnClickListener(listener);
        return row;
    }

    private void showApiKeyDialog() {
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        windowManager.updateViewLayout(rootContainer, params);

        EditText input = new EditText(this);
        input.setHint("Tempel Kunci Omnikey (Omnikey-...)");
        input.setText(prefs.getString("api_key", ""));
        input.setTextColor(Color.BLACK);
        input.setPadding(40, 30, 40, 30);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.WHITE);
        box.setPadding(30, 30, 30, 30);

        TextView btnSave = new TextView(this);
        btnSave.setText("💾 SIMPAN KUNCI OMNIKEY");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setBackgroundColor(Color.parseColor("#4F46E5"));
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setPadding(20, 24, 20, 24);
        btnSave.setOnClickListener(v -> {
            prefs.edit().putString("api_key", input.getText().toString().trim()).apply();
            menuCard.removeView(box);
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(rootContainer, params);
            Toast.makeText(this, "Kunci Omnikey tersimpan!", Toast.LENGTH_SHORT).show();
        });

        box.addView(input);
        box.addView(btnSave);
        menuCard.addView(box, 0);
    }

    private void jalankanAI(String mode) {
        String apiKey = prefs.getString("api_key", "").trim();
        if (apiKey.isEmpty()) {
            menuCard.setVisibility(View.VISIBLE);
            showApiKeyDialog();
            Toast.makeText(this, "Masukkan Kunci API Omnikey terlebih dahulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        statusBanner.setVisibility(View.VISIBLE);
        statusText.setText("✍️ Mengerjakan soal");

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            statusText.setText("⚠️ Layar tidak terbaca");
            mainHandler.postDelayed(() -> statusBanner.setVisibility(View.GONE), 2000);
            return;
        }

        StringBuilder screenText = new StringBuilder();
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        collectNodes(rootNode, screenText, allNodes);

        String konten = screenText.toString().trim();
        if (konten.isEmpty()) {
            statusText.setText("⚠️ Teks soal kosong");
            mainHandler.postDelayed(() -> statusBanner.setVisibility(View.GONE), 2000);
            return;
        }

        executor.execute(() -> {
            try {
                String prompt;
                if ("AUTO_PILGAN".equals(mode) || "AUTO_KKM".equals(mode)) {
                    prompt = "Berikut adalah teks soal dan pilihan ganda:\n\n" + konten +
                            "\n\nTugas: Berikan HANYA teks persis dari pilihan jawaban yang benar (tanpa penjelasan tambahan).";
                } else if ("AUTO_ESAI".equals(mode)) {
                    prompt = "Berikut adalah soal esai:\n\n" + konten +
                            "\n\nTugas: Tuliskan jawaban esai secara singkat, padat, dan jelas dalam 1 paragraf.";
                } else {
                    prompt = "Berikan jawaban dan penjelasan lengkap langkah demi langkah untuk soal ini:\n\n" + konten;
                }

                String aiReply = requestOmnikey(apiKey, prompt).trim();

                mainHandler.post(() -> {
                    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("AI", aiReply));

                    if ("AUTO_PILGAN".equals(mode) || "AUTO_KKM".equals(mode)) {
                        boolean clicked = autoClickOption(allNodes, aiReply);
                        statusText.setText(clicked ? "✅ Dijawab: " + aiReply : "📋 Jawaban: " + aiReply);
                    } else if ("AUTO_ESAI".equals(mode)) {
                        boolean typed = autoTypeEssay(allNodes, aiReply);
                        statusText.setText(typed ? "✅ Esai terisi!" : "📋 Disalin ke Clipboard!");
                    } else {
                        menuCard.setVisibility(View.VISIBLE);
                        explanationBox.setVisibility(View.VISIBLE);
                        explanationBox.setText("💡 Jawaban:\n" + aiReply);
                        statusText.setText("✅ Selesai");
                    }

                    mainHandler.postDelayed(() -> statusBanner.setVisibility(View.GONE), 3000);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    statusText.setText("❌ Gagal Terhubung");
                    menuCard.setVisibility(View.VISIBLE);
                    explanationBox.setVisibility(View.VISIBLE);
                    explanationBox.setText("Eror:\n" + e.getMessage());
                    mainHandler.postDelayed(() -> statusBanner.setVisibility(View.GONE), 3000);
                });
            }
        });
    }

    private void collectNodes(AccessibilityNodeInfo node, StringBuilder sb, List<AccessibilityNodeInfo> list) {
        if (node == null) return;
        list.add(node);
        if (node.getText() != null && node.getText().length() > 0) {
            sb.append(node.getText().toString()).append("\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNodes(node.getChild(i), sb, list);
        }
    }

    private boolean autoClickOption(List<AccessibilityNodeInfo> nodes, String targetAnswer) {
        String cleanTarget = targetAnswer.replaceAll("^[A-Ea-e][\\.\\)]\\s*", "").trim().toLowerCase();
        for (AccessibilityNodeInfo node : nodes) {
            if (node.getText() != null) {
                String nodeText = node.getText().toString().trim().toLowerCase();
                if (!cleanTarget.isEmpty() && (nodeText.equals(cleanTarget) || nodeText.contains(cleanTarget))) {
                    AccessibilityNodeInfo clickable = node;
                    while (clickable != null && !clickable.isClickable()) {
                        clickable = clickable.getParent();
                    }
                    if (clickable != null) {
                        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    } else {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean autoTypeEssay(List<AccessibilityNodeInfo> nodes, String essayAnswer) {
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isEditable() || (node.getClassName() != null && node.getClassName().toString().contains("EditText"))) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, essayAnswer);
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            }
        }
        return false;
    }

    private String requestOmnikey(String apiKey, String prompt) throws Exception {
        URL url = new URL("https://www.ommikey.my.id/api/v1/chat/completions");
        
        JSONObject message = new JSONObject().put("role", "user").put("content", prompt);
        JSONObject payload = new JSONObject()
                .put("model", "qwen-max")
                .put("messages", new JSONArray().put(message));

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes("UTF-8"));
        }

        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()
        ));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) response.append(line);

        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + ": " + response.toString());
        }

        JSONObject jsonRes = new JSONObject(response.toString());
        return jsonRes.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (rootContainer != null) windowManager.removeView(rootContainer);
    }
}
