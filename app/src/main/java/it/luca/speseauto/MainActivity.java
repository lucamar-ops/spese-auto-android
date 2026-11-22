package it.luca.speseauto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private class Bridge {

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
            new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean ok = false;
                    String body;
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setRequestProperty("User-Agent", "SpeseAutoLucaGiada/1.0 (app personale Android)");
                        c.setRequestProperty("Accept-Language", "it");
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(20000);
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
                        body = ok ? sb.toString() : ("Errore del servizio (HTTP " + code + ")");
                        c.disconnect();
                    } catch (Exception e) {
                        body = "Connessione non riuscita";
                    }
                    final String js = "window.__httpCb(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(body) + ")";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            web.evaluateJavascript(js, null);
                        }
                    });
                }
            }).start();
        }
    }
}
