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
 * 兼容：Android TV, 手机, 平板
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
        
        // 保持屏幕常亮 (播放视频必备)
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // 沉浸式 UI 设置 (黑底白字)
        setupSystemUI();
        
        setContentView(R.layout.activity_main); 

        activityMainRoot = findViewById(R.id.activity_main_root); 
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        
        // WebView 核心配置
        configureWebView();
        
        // 注入原生接口
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        webView.setWebViewClient(new CustomWebViewClient());
        webView.setWebChromeClient(new CustomWebChromeClient());

        // TV 专用按键监听器 (仅在检测到按键时触发)
        webView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return handleTvInput(keyCode);
            }
            return false;
        });

        webView.loadUrl(TARGET_URL, getRefererHeaders());
    }

    /**
     * 判断当前是否为电视设备
     */
    private boolean isTvDevice() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
               getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION);
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
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // 如果是 TV，增强焦点获取
        if (isTvDevice()) {
            webView.setFocusable(true);
            webView.setFocusableInTouchMode(true);
            webView.requestFocus();
        }
    }

    private void setupSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                );
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

    /**
     * 处理 TV 遥控器逻辑
     */
    private boolean handleTvInput(int keyCode) {
        // 1. 媒体控制键 (快进/快退) - 通用
        if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            executeJavaScript("if(window.videoSeek) window.videoSeek(" + SEEK_SECONDS + ");");
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            executeJavaScript("if(window.videoSeek) window.videoSeek(-" + SEEK_SECONDS + ");");
            return true;
        }

        // 2. D-Pad 导航逻辑 (仅在非全屏且是 TV 时处理)
        if (mCustomView == null && isTvDevice()) { 
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    executeJavaScript("if(window.handleTvKey) window.handleTvKey(" + keyCode + ");");
                    return false; // 返回 false 让系统继续处理焦点循环
            }
        } 
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 全屏模式下，返回键退出全屏
        if (keyCode == KeyEvent.KEYCODE_BACK && mCustomView != null) {
            webView.getWebChromeClient().onHideCustomView(); 
            return true;
        }
        
        // 网页模式下，返回键回退历史
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        
        // 尝试处理 TV 特有按键
        if (handleTvInput(keyCode)) return true;
        
        return super.onKeyDown(keyCode, event);
    }

    // --- 内部类保持原有优化逻辑 ---

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
            
            // 全屏时完全隐藏 UI
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null); 
        }

        @Override
        public void onHideCustomView() {
            if (mCustomView == null) return;
            activityMainRoot.removeView(mCustomView);
            mCustomView = null;
            webView.setVisibility(View.VISIBLE);
            if (mCustomViewCallback != null) mCustomViewCallback.onCustomViewHidden();
            
            setupSystemUI(); // 恢复 UI

            handler.postDelayed(() -> {
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
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
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                Toast.makeText(MainActivity.this, "连接失败，请检查网络", Toast.LENGTH_SHORT).show();
            }
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
            webView.removeJavascriptInterface("Android");
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
                return item != null && item.getText() != null ? item.getText().toString() : "";
            }
            return "";
        }
    }
}
