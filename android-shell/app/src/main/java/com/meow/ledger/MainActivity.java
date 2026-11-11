package com.meow.ledger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 把 dist 产物装进 WebView。页面从 https://appassets.androidplatform.net 加载，
 * 这样是安全上下文，IndexedDB 可用。资源全部来自 assets，不访问网络。
 */
public class MainActivity extends Activity {
    private static final String HOST = "https://appassets.androidplatform.net";

    private static final Map<String, String> TYPES = new HashMap<>();

    static {
        TYPES.put("html", "text/html");
        TYPES.put("js", "text/javascript");
        TYPES.put("css", "text/css");
        TYPES.put("svg", "image/svg+xml");
        TYPES.put("json", "application/json");
        TYPES.put("png", "image/png");
        TYPES.put("woff2", "font/woff2");
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 内容延伸到状态栏后面，由页面用 safe-area-inset 自行留白
        Window w = getWindow();
        w.setStatusBarColor(0x00000000);
        w.setNavigationBarColor(0x00000000);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // 页面按设备 dp 宽度排版，禁止 WebView 再按物理像素放大，否则右边会被切掉。
        web.setInitialScale(100);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String path = request.getUrl().getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                String asset = path.startsWith("/") ? path.substring(1) : path;
                try {
                    InputStream in = getAssets().open(asset);
                    String ext = asset.contains(".") ? asset.substring(asset.lastIndexOf('.') + 1) : "";
                    String type = TYPES.getOrDefault(ext, "application/octet-stream");
                    return new WebResourceResponse(type, "utf-8", in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                            null, new ByteArrayInputStream(new byte[0]));
                }
            }
        });
        setContentView(web);
        web.loadUrl(HOST + "/index.html");
        web.addJavascriptInterface(new Probe(), "Probe");
    }

    /** 验收用：把页面内的探针结果写到 logcat，标签 MeowProbe。 */
    private static class Probe {
        @android.webkit.JavascriptInterface
        public void log(String s) {
            android.util.Log.i("MeowProbe", s);
        }
    }

    /** 返回键交给页面内导航，而不是直接退出。页面没有历史时才退出。 */
    @Override
    public void onBackPressed() {
        WebView web = (WebView) ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
