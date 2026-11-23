package it.luca.speseauto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private WebView web;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("speseauto", Context.MODE_PRIVATE);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        boolean dark = isNight();
        web.setBackgroundColor(dark ? 0xFF0B0D14 : 0xFFF3F4F9);
        applyBars(dark ? "#0B0D14" : "#F3F4F9", !dark);
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    @SuppressWarnings("deprecation")
    private void applyBars(String hex, boolean darkIcons) {
        int c = Color.parseColor(hex);
        getWindow().setStatusBarColor(c);
        getWindow().setNavigationBarColor(c);
        if (web != null) web.setBackgroundColor(c);
        View d = getWindow().getDecorView();
        int f = d.getSystemUiVisibility();
        if (darkIcons) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            if (darkIcons) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else f &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        d.setSystemUiVisibility(f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.resumeTimers();
            web.evaluateJavascript("window.onAppResume && onAppResume()", null);
        }
    }

    @Override
    protected void onPause() {
        if (web != null) web.pauseTimers();
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (web != null) web.evaluateJavascript("window.applyTheme && applyTheme()", null);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("(window.onBack && window.onBack()) ? 'y' : 'n'", value -> {
            if (value == null || !value.contains("y")) finish();
        });
    }

    private class Bridge {

        @JavascriptInterface
        public boolean isDark() {
            return isNight();
        }

        @JavascriptInterface
        public void setBars(final String hex, final boolean darkIcons) {
            runOnUiThread(() -> applyBars(hex, darkIcons));
        }

        @JavascriptInterface
        public String load() {
            return prefs.getString("data", "");
        }

        @JavascriptInterface
        public void save(String json) {
            prefs.edit().putString("data", json).apply();
        }

        @JavascriptInterface
        public void share(final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, "Condividi"));
                }
            });
        }

        @JavascriptInterface
        public void httpGet(final String id, final String url) {
            httpRequest(id, "GET", url, null);
        }

        @JavascriptInterface
        public void httpRequest(final String id, final String method, final String url, final String reqBody) {
            new Thread(() -> {
                boolean ok = false;
                String body;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod(method);
                    c.setRequestProperty("User-Agent", "SpeseAutoLucaGiada/1.0 (app personale Android)");
                    c.setRequestProperty("Accept-Language", "it");
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    if (reqBody != null) {
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        OutputStream os = c.getOutputStream();
                        os.write(reqBody.getBytes("UTF-8"));
                        os.close();
                    }
                    int code = c.getResponseCode();
                    InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
                    StringBuilder sb = new StringBuilder();
                    if (in != null) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line).append('\n');
                        r.close();
                    }
                    ok = code < 400;
                    body = ok ? sb.toString() : ("HTTP " + code + " " + sb.toString());
                    c.disconnect();
                } catch (Exception e) {
                    body = "Connessione non riuscita";
                }
                final String js = "window.__httpCb(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(body) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }
    }
}
