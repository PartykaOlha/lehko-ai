package ua.lehko.ai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.WindowManager;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_FILE = 1001;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private ChatGPTAuth auth;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(248,245,255));
        getWindow().setNavigationBarColor(Color.rgb(248,245,255));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        auth = new ChatGPTAuth(this);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(), "LehkoAndroid");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent i = params.createIntent();
                    startActivityForResult(i, PICK_FILE);
                    return true;
                } catch (Exception ex) {
                    fileCallback = null;
                    return false;
                }
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return route(url);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,
                    android.webkit.WebResourceRequest request) {
                return route(request.getUrl().toString());
            }
            private boolean route(String url) {
                if (url.startsWith("file:///android_asset/")) return false;
                if (url.startsWith("https://")) {
                    try { openInBrowser(url); }
                    catch (Exception ignored) {}
                }
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    public final class Bridge {
        @JavascriptInterface public String status() { return auth.status().toString(); }
        @JavascriptInterface public void connect() {
            background.execute(() -> {
                try { auth.signIn(url -> runOnUiThread(() -> {
                    try { openInBrowser(url); }
                    catch (Exception ex) { notifyStatus(); }
                })); }
                catch (Exception ignored) { /* Report no token and stay disconnected. */ }
                notifyStatus();
            });
        }
        @JavascriptInterface public void disconnect() {
            background.execute(() -> { auth.signOut(); notifyStatus(); });
        }
        @JavascriptInterface public void ask(String id, String prompt, String photo) {
            if (id == null || id.length() > 80 || prompt == null || prompt.length() > 14000
                    || (photo != null && photo.length() > 6_000_000)) {
                sendResult(id == null ? "" : id, false, "Занадто великий запит.");
                return;
            }
            background.execute(() -> {
                try { sendResult(id, true, auth.respond(prompt, photo)); }
                catch (Exception e) {
                    sendResult(id, false, e.getMessage() == null ? "Не вдається виконати запит." : e.getMessage());
                }
            });
        }
    }

    // Use the same browser for ChatGPT pre-login and OAuth to retain Google sessions.
    private void openInBrowser(String url) {
        if (!url.startsWith("https://")) throw new IllegalArgumentException("HTTPS URL required");
        Intent chrome = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        chrome.setPackage("com.android.chrome");
        try { startActivity(chrome); }
        catch (android.content.ActivityNotFoundException ignored) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }

    void notifyStatus() {
        JSONObject status = auth.status();
        eval("window.LehkoNative && window.LehkoNative.onStatus(" + status.toString() + ")");
    }
    void sendResult(String id, boolean ok, String value) {
        eval("window.LehkoNative && window.LehkoNative.complete("
                + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(value) + ")");
    }
    private void eval(String script) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(script, null); });
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        background.shutdownNow();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
