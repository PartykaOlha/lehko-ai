package ua.atelier.day;

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
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 8;
    private static final int REQ_EXPORT = 101;
    private static final int REQ_IMPORT = 102;
    private static final int MAX_IMPORT_SIZE = 2_000_000;
    private WebView webView;
    private String pendingBackup = null;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(249, 249, 255));
        getWindow().setNavigationBarColor(Color.rgb(249, 249, 255));
        getWindow().getDecorView().setSystemUiVisibility(
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        TaskScheduler.createChannel(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(249, 249, 255));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setAllowFileAccessFromFileURLs(false);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        webView.addJavascriptInterface(new Bridge(), "NativePlanner");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri uri = req.getUrl();
                if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); } catch(Exception ignored) {}
                }
                return true;
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    public final class Bridge {
        @JavascriptInterface public void syncTasks(String json) {
            runOnUiThread(() -> TaskScheduler.saveAndSchedule(MainActivity.this,json));
        }
        @JavascriptInterface public void enableReminders(String json) {
            runOnUiThread(() -> {
                TaskScheduler.saveAndSchedule(MainActivity.this,json);
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
                } else Toast.makeText(MainActivity.this,"Нагадування ввімкнено",Toast.LENGTH_SHORT).show();
            });
        }
        @JavascriptInterface public void shareBackup(String raw) {
            if(raw == null || raw.length() > MAX_IMPORT_SIZE)return;
            runOnUiThread(() -> {
                pendingBackup=raw;
                Intent out=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                out.setType("application/json");
                out.addCategory(Intent.CATEGORY_OPENABLE);
                out.putExtra(Intent.EXTRA_TITLE,"Atelier_Day_backup.json");
                startActivityForResult(out,REQ_EXPORT);
            });
        }
        @JavascriptInterface public void pickBackup() {
            runOnUiThread(() -> {
                Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                in.setType("*/*");
                in.addCategory(Intent.CATEGORY_OPENABLE);
                in.addCategory(Intent.CATEGORY_DEFAULT);
                startActivityForResult(in,REQ_IMPORT);
            });
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode == REQ_NOTIFICATIONS) {
            boolean ok=grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED;
            if(ok)TaskScheduler.scheduleAll(this);
            Toast.makeText(this,ok?"Сповіщення дозволено":"Сповіщення не дозволено",Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onActivityResult(int code,int resultCode,Intent data) {
        super.onActivityResult(code,resultCode,data);
        if(resultCode != RESULT_OK || data == null || data.getData() == null)return;
        Uri uri=data.getData();
        if(code==REQ_EXPORT && pendingBackup!=null) {
            try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null) throw new IllegalStateException("Unable to write document");
                out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this,"Копію збережено",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Не вдалося зберегти копію",Toast.LENGTH_SHORT).show();}
            finally {pendingBackup=null;}
        }else if(code==REQ_IMPORT) {
            try(InputStream in=getContentResolver().openInputStream(uri);
                ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                if(in==null)throw new IllegalStateException("Unable to read document");
                byte[] chunk=new byte[8192];int n,total=0;
                while((n=in.read(chunk))!=-1){total+=n;if(total>MAX_IMPORT_SIZE)throw new IllegalArgumentException("Backup too large");output.write(chunk,0,n);}
                String raw=output.toString(StandardCharsets.UTF_8.name());
                String script="window.AtelierReceiveBackup("+JSONObject.quote(raw)+")";
                webView.evaluateJavascript(script,null);
            }catch(Exception e){Toast.makeText(this,"Не вдалося відкрити копію",Toast.LENGTH_SHORT).show();}
        }
    }
    @Override @Deprecated public void onBackPressed() {
        if(webView!=null && webView.canGoBack())webView.goBack();else super.onBackPressed();
    }
    @Override public void onDestroy() {
        if(webView!=null)webView.destroy();super.onDestroy();
    }
}
