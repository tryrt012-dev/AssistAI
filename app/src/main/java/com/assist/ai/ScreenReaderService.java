package com.assist.ai;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ScreenReaderService extends AccessibilityService {
    private WindowManager wm;
    private LinearLayout root, menuCard, banner, apiBox;
    private TextView statusTxt, mascot, expBox, footer;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String DEFAULT_KEY = "";

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = getSharedPreferences("AssistAIPrefs", MODE_PRIVATE);
        if (prefs.getString("api_key", "").isEmpty()) {
            prefs.edit().putString("api_key", DEFAULT_KEY).apply();
        }
        initUI();
    }

    private GradientDrawable bg(String hex, float rad, String strokeHex, int strokeW) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(hex));
        d.setCornerRadius(rad);
        if (strokeW > 0) d.setStroke(strokeW, Color.parseColor(strokeHex));
        return d;
    }

    private void initUI() {
        if (wm != null && root != null) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        menuCard = new LinearLayout(this);
        menuCard.setOrientation(LinearLayout.VERTICAL);
        menuCard.setPadding(32, 32, 32, 24);
        menuCard.setVisibility(View.GONE);
        menuCard.setBackground(bg("#FFFFFF", 42f, "#E2E8F0", 2));

        menuCard.addView(item("✨", "Jawab soal otomatis", v -> runAI("PILGAN")));
        menuCard.addView(item("✏️", "Jawaban diisi AI", v -> runAI("ESAI")));
        menuCard.addView(item("🎯", "Auto nilai di atas KKM", v -> runAI("KKM")));
        menuCard.addView(item("📖", "Penjelasan lengkap", v -> runAI("JELAS")));
        menuCard.addView(item("🔑", "Atur API & Model (OpenCode/Groq)", v -> toggleApiBox()));

        LinearLayout sRow = new LinearLayout(this);
        sRow.setGravity(Gravity.CENTER_VERTICAL);
        sRow.setPadding(16, 18, 16, 18);
        TextView sLbl = new TextView(this);
        sLbl.setText("👁️‍🗨️   Mode samaran");
        sLbl.setTextColor(Color.parseColor("#1E293B"));
        sLbl.setTextSize(14f);
        sLbl.setTypeface(null, Typeface.BOLD);
        sRow.addView(sLbl, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch sw = new Switch(this);
        sw.setOnCheckedChangeListener((b, chk) -> {
            mascot.setAlpha(chk ? 0.22f : 1f);
            banner.setAlpha(chk ? 0.35f : 1f);
        });
        sRow.addView(sw);
        menuCard.addView(sRow);

        expBox = new TextView(this);
        expBox.setTextColor(Color.parseColor("#1E293B"));
        expBox.setTextSize(12f);
        expBox.setPadding(20, 16, 20, 16);
        expBox.setBackground(bg("#F8FAFC", 20f, "#CBD5E1", 1));
        expBox.setVisibility(View.GONE);
        menuCard.addView(expBox);

        footer = new TextView(this);
        footer.setText("ASSIST AI • MULTI-ENGINE (OPENCODE / GROQ)");
        footer.setTextColor(Color.parseColor("#94A3B8"));
        footer.setTextSize(10f);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 14, 0, 2);
        menuCard.addView(footer);

        mascot = new TextView(this);
        mascot.setText("🎓\n🤖");
        mascot.setTextSize(21f);
        mascot.setGravity(Gravity.CENTER);
        mascot.setPadding(24, 14, 24, 14);
        mascot.setBackground(bg("#EEF2FF", 100f, "#4F46E5", 4));

        banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(32, 18, 32, 18);
        banner.setVisibility(View.GONE);
        banner.setBackground(bg("#FFFFFF", 28f, "#CBD5E1", 2));

        statusTxt = new TextView(this);
        statusTxt.setText("✍️ Mengerjakan soal");
        statusTxt.setTextColor(Color.parseColor("#1E293B"));
        statusTxt.setTextSize(13f);
        statusTxt.setTypeface(null, Typeface.BOLD);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(true);
        banner.addView(statusTxt);
        banner.addView(pb, new LinearLayout.LayoutParams(400, 22));

        root.addView(menuCard, new LinearLayout.LayoutParams(680, -2));
        root.addView(mascot);
        root.addView(banner);

        params = new WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 120;
        params.y = 400;
        wm.addView(root, params);

        mascot.setOnTouchListener(new View.OnTouchListener() {
            int ix, iy; float tx, ty; long t0;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    ix = params.x; iy = params.y; tx = e.getRawX(); ty = e.getRawY();
                    t0 = System.currentTimeMillis(); return true;
                } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = ix + (int)(e.getRawX() - tx);
                    params.y = iy + (int)(e.getRawY() - ty);
                    wm.updateViewLayout(root, params); return true;
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (System.currentTimeMillis() - t0 < 220) {
                        boolean show = menuCard.getVisibility() == View.GONE;
                        menuCard.setVisibility(show ? View.VISIBLE : View.GONE);
                        if (!show) setFocusable(false);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private View item(String ic, String txt, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(14, 18, 14, 18);
        TextView i = new TextView(this);
        i.setText(ic); i.setTextSize(15f); i.setPadding(18, 10, 18, 10);
        i.setBackground(bg("#F1F5F9", 18f, "#00000000", 0));
        TextView t = new TextView(this);
        t.setText(txt); t.setTextColor(Color.parseColor("#1E293B"));
        t.setTextSize(14f); t.setTypeface(null, Typeface.BOLD);
        t.setPadding(22, 0, 0, 0);
        r.addView(i); r.addView(t); r.setOnClickListener(l);
        return r;
    }

    private void setFocusable(boolean focus) {
        params.flags = focus ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                             : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        wm.updateViewLayout(root, params);
    }

    private void toggleApiBox() {
        if (apiBox != null && apiBox.getParent() != null) {
            menuCard.removeView(apiBox);
            setFocusable(false);
            return;
        }
        setFocusable(true);
        apiBox = new LinearLayout(this);
        apiBox.setOrientation(LinearLayout.VERTICAL);
        apiBox.setPadding(20, 20, 20, 20);
        apiBox.setBackground(bg("#F8FAFC", 24f, "#CBD5E1", 2));

        EditText inKey = new EditText(this);
        inKey.setHint("API Key (OpenCode / gsk_ / AIza / sk-)");
        inKey.setText(prefs.getString("api_key", DEFAULT_KEY));
        inKey.setTextColor(Color.BLACK); inKey.setTextSize(12f);

        EditText inUrl = new EditText(this);
        inUrl.setHint("URL (Kosongkan = Auto Detect)");
        inUrl.setText(prefs.getString("api_url", ""));
        inUrl.setTextColor(Color.BLACK); inUrl.setTextSize(12f);

        EditText inMod = new EditText(this);
        inMod.setHint("Model (Kosongkan = Auto)");
        inMod.setText(prefs.getString("api_model", ""));
        inMod.setTextColor(Color.BLACK); inMod.setTextSize(12f);

        LinearLayout presets = new LinearLayout(this);
        presets.addView(presetBtn("OpenCode", () -> {
            inUrl.setText("https://opencode.ai/zen/v1/chat/completions");
            inMod.setText("big-pickle");
        }));
        presets.addView(presetBtn("Groq", () -> {
            inUrl.setText("https://api.groq.com/openai/v1/chat/completions");
            inMod.setText("llama-3.3-70b-versatile");
        }));
        presets.addView(presetBtn("Auto", () -> {
            inUrl.setText(""); inMod.setText("");
        }));

        TextView save = new TextView(this);
        save.setText("💾 SIMPAN PENGATURAN");
        save.setTextColor(Color.WHITE); save.setGravity(Gravity.CENTER);
        save.setPadding(16, 20, 16, 20);
        save.setBackground(bg("#4F46E5", 18f, "#00000000", 0));
        save.setOnClickListener(v -> {
            prefs.edit()
                .putString("api_key", fixKey(inKey.getText().toString()))
                .putString("api_url", inUrl.getText().toString().trim())
                .putString("api_model", inMod.getText().toString().trim())
                .apply();
            menuCard.removeView(apiBox);
            setFocusable(false);
            Toast.makeText(this, "Tersimpan!", Toast.LENGTH_SHORT).show();
        });

        apiBox.addView(presets);
        apiBox.addView(inKey);
        apiBox.addView(inUrl);
        apiBox.addView(inMod);
        apiBox.addView(save);
        menuCard.addView(apiBox, 0);
    }

    private TextView presetBtn(String name, Runnable r) {
        TextView b = new TextView(this);
        b.setText(name); b.setTextSize(11f); b.setTextColor(Color.parseColor("#4F46E5"));
        b.setPadding(18, 10, 18, 10);
        b.setBackground(bg("#EEF2FF", 14f, "#4F46E5", 1));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private String fixKey(String k) {
        k = k.trim();
        if (k.startsWith("Gsk_")) return "g" + k.substring(1);
        if (k.startsWith("Sk-")) return "s" + k.substring(1);
        if (k.startsWith("Aiza")) return "AI" + k.substring(2);
        return k;
    }

    private void runAI(String mode) {
        if (!"JELAS".equals(mode)) menuCard.setVisibility(View.GONE);
        setFocusable(false);
        String key = fixKey(prefs.getString("api_key", DEFAULT_KEY));
        if (key.isEmpty()) { menuCard.setVisibility(View.VISIBLE); toggleApiBox(); return; }

        banner.setVisibility(View.VISIBLE);
        statusTxt.setText("ESAI".equals(mode) ? "✍️ Menulis jawaban esai" : "✍️ Mengerjakan soal");

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            statusTxt.setText("⚠️ Layar tidak terbaca");
            handler.postDelayed(() -> banner.setVisibility(View.GONE), 2000);
            return;
        }

        StringBuilder sb = new StringBuilder();
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        scan(rootNode, sb, nodes);
        String text = sb.toString().trim();

        if (text.isEmpty()) {
            statusTxt.setText("⚠️ Teks soal kosong");
            handler.postDelayed(() -> banner.setVisibility(View.GONE), 2000);
            return;
        }

        exec.execute(() -> {
            try {
                String prompt = "PILGAN".equals(mode) || "KKM".equals(mode)
                    ? "Soal di layar:\n" + text + "\n\nBerikan HANYA teks persis opsi jawaban yang benar tanpa penjelasan."
                    : "ESAI".equals(mode)
                    ? "Soal esai:\n" + text + "\n\nTuliskan jawaban singkat, padat, dan langkah intinya dalam 1 paragraf."
                    : "Jelaskan langkah penyelesaian dan jawaban soal ini secara singkat:\n" + text;

                String ans = callAPI(key, prompt).trim();
                handler.post(() -> {
                    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("AI", ans));

                    if ("PILGAN".equals(mode) || "KKM".equals(mode)) {
                        boolean ok = clickOpt(nodes, ans);
                        statusTxt.setText((ok ? "✅ Dijawab: " : "📋 Jawaban: ") + ans);
                    } else if ("ESAI".equals(mode)) {
                        boolean ok = fillEssay(nodes, ans);
                        statusTxt.setText(ok ? "✅ Esai diisi otomatis!" : "📋 Disalin ke Clipboard!");
                    } else {
                        menuCard.setVisibility(View.VISIBLE);
                        expBox.setVisibility(View.VISIBLE);
                        expBox.setText("💡 Jawaban:\n" + ans);
                        statusTxt.setText("✅ Selesai");
                    }
                    handler.postDelayed(() -> banner.setVisibility(View.GONE), 3500);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    statusTxt.setText("❌ Eror API");
                    menuCard.setVisibility(View.VISIBLE);
                    expBox.setVisibility(View.VISIBLE);
                    expBox.setText("⚠️ Gagal:\n" + e.getMessage());
                    handler.postDelayed(() -> banner.setVisibility(View.GONE), 3500);
                });
            }
        });
    }

    private void scan(AccessibilityNodeInfo n, StringBuilder sb, List<AccessibilityNodeInfo> list) {
        if (n == null) return;
        list.add(n);
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        if (t != null && t.length() > 0) sb.append(t).append("\n");
        else if (d != null && d.length() > 0) sb.append(d).append("\n");
        for (int i = 0; i < n.getChildCount(); i++) scan(n.getChild(i), sb, list);
    }

    private boolean clickOpt(List<AccessibilityNodeInfo> nodes, String ans) {
        String clean = ans.replaceAll("^[A-Ea-e][\\.\\)]\\s*", "").trim().toLowerCase();
        for (AccessibilityNodeInfo n : nodes) {
            String t = n.getText() != null ? n.getText().toString() :
                       (n.getContentDescription() != null ? n.getContentDescription().toString() : "");
            t = t.trim().toLowerCase();
            if (!clean.isEmpty() && !t.isEmpty() && (t.equals(clean) || t.contains(clean))) {
                AccessibilityNodeInfo c = n;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null) return c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
        return false;
    }

    private boolean fillEssay(List<AccessibilityNodeInfo> nodes, String ans) {
        for (AccessibilityNodeInfo n : nodes) {
            if (n.isEditable() || (n.getClassName() != null && n.getClassName().toString().contains("EditText"))) {
                Bundle b = new Bundle();
                b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, ans);
                n.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
            }
        }
        return false;
    }

    private String callAPI(String key, String prompt) throws Exception {
        String customUrl = prefs.getString("api_url", "").trim();
        String customMod = prefs.getString("api_model", "").trim();

        if (key.startsWith("AIza") || key.startsWith("AQ.")) {
            URL u = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + key);
            JSONObject p = new JSONObject().put("contents", new JSONArray().put(
                new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))));
            JSONObject res = post(u, null, p.toString());
            return res.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                      .getJSONArray("parts").getJSONObject(0).getString("text");
        }

        String endpoint = !customUrl.isEmpty() ? customUrl :
            key.startsWith("gsk_") ? "https://api.groq.com/openai/v1/chat/completions" :
            key.startsWith("sk-or-") ? "https://openrouter.ai/api/v1/chat/completions" :
            key.startsWith("Omnikey-") ? "https://www.ommikey.my.id/api/v1/chat/completions" :
            "https://opencode.ai/zen/v1/chat/completions";

        String model = !customMod.isEmpty() ? customMod :
            endpoint.contains("groq.com") ? "llama-3.3-70b-versatile" :
            endpoint.contains("openrouter.ai") ? "qwen/qwen-2.5-72b-instruct:free" :
            endpoint.contains("ommikey") ? "gpt-4o-mini" : "big-pickle";

        JSONObject msg = new JSONObject().put("role", "user").put("content", prompt);
        JSONObject body = new JSONObject().put("model", model).put("messages", new JSONArray().put(msg));
        JSONObject res = post(new URL(endpoint), key, body.toString());
        return res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }

    private JSONObject post(URL url, String bearer, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) { os.write(json.getBytes("UTF-8")); }
        int code = c.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
            code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + sb);
        return new JSONObject(sb.toString());
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (root != null) wm.removeView(root);
    }
}
