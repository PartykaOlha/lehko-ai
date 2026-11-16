package ua.atelier.balance;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_REQUEST = 7;
    private WebView webView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(250, 248, 255));
        getWindow().setNavigationBarColor(Color.rgb(250, 248, 255));
        getWindow().getDecorView().setSystemUiVisibility(
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ReminderScheduler.createChannel(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(250, 248, 255));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        webView.getSettings().setAllowFileAccessFromFileURLs(false);
        webView.addJavascriptInterface(new NativeBridge(), "NativeAtelier");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri uri = req.getUrl();
                if ("file".equals(uri.getScheme()) && "/android_asset/index.html".equals(uri.getPath())) return false;
                if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception ignored) {}
                    return true;
                }
                return true;
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    public final class NativeBridge {
        @JavascriptInterface public void shareText(String body) {
            if (body == null || body.length() > 250000) return;
            runOnUiThread(() -> {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, body);
                try { startActivity(Intent.createChooser(share, "Поділитися через…")); }
                catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Немає застосунку для поширення", Toast.LENGTH_SHORT).show();
                }
            });
        }
        @JavascriptInterface public void syncReminders(String json) {
            runOnUiThread(() -> ReminderScheduler.storeAndSchedule(MainActivity.this, json, false));
        }
        @JavascriptInterface public void enableReminders(String json) {
            runOnUiThread(() -> {
                ReminderScheduler.storeAndSchedule(MainActivity.this, json, true);
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
                } else {
                    Toast.makeText(MainActivity.this, "Нагадування увімкнено", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == NOTIFICATION_REQUEST) {
            boolean allowed = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this, allowed ? "Сповіщення дозволено" : "Сповіщення не дозволено", Toast.LENGTH_LONG).show();
            if (allowed) ReminderScheduler.scheduleAll(this);
        }
    }

    @Override @Deprecated public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
    @Override public void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
