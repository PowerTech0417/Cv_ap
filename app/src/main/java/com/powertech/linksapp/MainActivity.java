package com.powertech.linksapp; 

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout; 
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * PowerTech 影视TV - 全设备通用版
 * 修复说明：支持手机/平板 LAUNCHER 与 电视 LEANBACK_LAUNCHER 双重启动
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout activityMainRoot; 
    private View mCustomView; 
    private WebChromeClient.CustomViewCallback mCustomViewCallback; 

    private static final String TARGET_URL = "https://app.key-3b8.workers.dev/";
    private final Handler handler = new Handler(); 
    private static final int SEEK_SECONDS = 10; 

    @SuppressLint({"SetJavaScriptEnabled", "InlinedApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 视频播放防息屏
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // UI 样式初始化
        setupSystemUI();
        
        setContentView(R.layout.activity_main); 

        activityMainRoot = findViewById(R.id.activity_main_root); 
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        
        // 核心配置
        configureWebView();
        
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        webView.setWebViewClient(new CustomWebViewClient());
        webView.setWebChromeClient(new CustomWebChromeClient());

        // 处理键盘/遥控器按键
        webView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return handleTvInput(keyCode);
            }
            return false;
        });

        webView.loadUrl(TARGET_URL, getRefererHeaders());
    }

    /**
     * 智能识别 TV 设备
     */
    private boolean isTvDevice() {
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
               pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
               pm.hasSystemFeature("android.hardware.type.television");
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); 
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false); 
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        // 通用焦点设置，确保手机点选和电视遥控都有效
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        if (isTvDevice()) {
            webView.requestFocus();
        }
    }

    private void setupSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            // 关闭浅色状态栏模式（确保图标是白色的）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = getWindow().getDecorView();
                decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
    }

    private void executeJavaScript(String jsCode) {
        handler.post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript(jsCode, null);
            } else {
                webView.loadUrl("javascript:" + jsCode);
            }
        });
    }

    private boolean handleTvInput(int keyCode) {
        // 媒体键快进快退
        if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            executeJavaScript("if(window.videoSeek) window.videoSeek(" + SEEK_SECONDS + ");");
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            executeJavaScript("if(window.videoSeek) window.videoSeek(-" + SEEK_SECONDS + ");");
            return true;
        }

        // TV 导航键传递给 JS
        if (mCustomView == null && isTvDevice()) { 
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    executeJavaScript("if(window.handleTvKey) window.handleTvKey(" + keyCode + ");");
                    return false; 
            }
        } 
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mCustomView != null) {
                webView.getWebChromeClient().onHideCustomView(); 
                return true;
            }
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        if (handleTvInput(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }

    // --- 核心 WebChromeClient (处理全屏) ---

    public class CustomWebChromeClient extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (mCustomView != null) {
                callback.onCustomViewHidden();
                return;
            }
            mCustomView = view;
            mCustomViewCallback = callback;
            webView.setVisibility(View.GONE);
            activityMainRoot.addView(mCustomView, new FrameLayout.LayoutParams(-1, -1));
            
            // 开启沉浸式全屏
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }

        @Override
        public void onHideCustomView() {
            if (mCustomView == null) return;
            activityMainRoot.removeView(mCustomView);
            mCustomView = null;
            webView.setVisibility(View.VISIBLE);
            if (mCustomViewCallback != null) mCustomViewCallback.onCustomViewHidden();
            
            setupSystemUI(); // 恢复手机状态栏

            handler.postDelayed(() -> {
                webView.requestLayout();
                webView.invalidate();
            }, 100);
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress == 100 ? View.GONE : View.VISIBLE);
        }
    }

    public class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            view.loadUrl(request.getUrl().toString(), getRefererHeaders());
            return true;
        }
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // 页面加载完后再次确保焦点
            if (isTvDevice()) webView.requestFocus();
        }
    }

    private Map<String, String> getRefererHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", TARGET_URL);
        return headers;
    }

    @Override
    protected void onDestroy() {
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    public class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) { mContext = c; }
        @JavascriptInterface
        public String getClipboardText() {
            ClipboardManager cm = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                if (item != null && item.getText() != null) return item.getText().toString();
            }
            return "";
        }
    }
}
