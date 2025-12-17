package com.powertech.linksapp; 

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
 * Links App 主活动。
 * 已添加：Android TV 遥控器 (D-Pad, 媒体键) 兼容性增强。
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    
    // Activity 的根容器，用于添加/移除全屏视图
    private FrameLayout activityMainRoot; 
    
    // 用于处理视频全屏的视图和回调
    private View mCustomView; 
    private WebChromeClient.CustomViewCallback mCustomViewCallback; 

    // 您的 Worker 地址 (仅用于 WebView 加载和作为 Referer)
    private static final String TARGET_URL = "https://app.key-3b8.workers.dev/";
    
    // 引入 Handler 用于执行延迟操作
    private final Handler handler = new Handler(); 
    
    // 【TV 增强】常量：定义遥控器快进/快退的秒数
    private static final int SEEK_SECONDS = 10; 

    /**
     * Helper: 注入 JavaScript 执行 D-Pad 导航模拟和媒体键操作。
     * @param jsCode 要执行的 JavaScript 字符串。
     */
    private void executeJavaScript(String jsCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(jsCode, null);
        } else {
            webView.loadUrl("javascript:" + jsCode);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "InlinedApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 【屏幕常亮修复】保持屏幕常亮
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // 设置状态栏和导航栏样式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                );
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getWindow().setNavigationBarColor(Color.BLACK);
            getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        }
        
        setContentView(R.layout.activity_main); 

        // 初始化根容器
        activityMainRoot = findViewById(R.id.activity_main_root); 
        
        // 初始化视图
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        
        // 配置 WebView 设置 (保持不变)
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); 
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(false);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false); 
        
        // 处理混合内容 (保持不变)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        // 注入 JavaScript 接口 (保持不变)
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // 设置 Client (保持不变)
        webView.setWebViewClient(new CustomWebViewClient());
        webView.setWebChromeClient(new CustomWebChromeClient());

        // ********************************************************
        // 【TV 增强】监听 WebView 的键盘事件，捕获 D-Pad 输入
        // ********************************************************
        webView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                // 仅在按下时处理 TV 输入，并返回 true 表示已处理
                return handleTvInput(keyCode);
            }
            return false;
        });
        // ********************************************************

        // 显式加载目标网站并设置 Referer (保持不变)
        webView.loadUrl(TARGET_URL, getRefererHeaders());
    }
    
    /**
     * Helper: 获取包含 Referer 的 Header Map，用于初始加载。
     */
    private Map<String, String> getRefererHeaders() {
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("Referer", TARGET_URL);
        return extraHeaders;
    }

    // ... (CustomWebViewClient 保持不变)
    public class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            view.loadUrl(request.getUrl().toString());
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(ProgressBar.VISIBLE);
            progressBar.setProgress(0); 
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                String description = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M 
                                     ? error.getDescription().toString() 
                                     : "加载失败";
                Toast.makeText(MainActivity.this, "网页加载错误: " + description, Toast.LENGTH_LONG).show();
            }
        }
        
        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (failingUrl.equals(view.getUrl())) {
                Toast.makeText(MainActivity.this, "网页加载错误: " + description, Toast.LENGTH_LONG).show();
            }
        }
    }

    // ... (CustomWebChromeClient 保持不变)
    public class CustomWebChromeClient extends WebChromeClient {
        
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress < 100) {
                progressBar.setProgress(newProgress);
                if (progressBar.getVisibility() != ProgressBar.VISIBLE) {
                    progressBar.setVisibility(ProgressBar.VISIBLE);
                }
            } else {
                progressBar.setVisibility(View.GONE);
            }
        }
        
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
             Log.d("WebViewConsole", consoleMessage.message() + " -- From line "
                   + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
            return true;
        }

        /**
         * 【核心全屏处理】显示自定义视图（视频全屏）。
         */
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            
            if (mCustomView != null) {
                callback.onCustomViewHidden();
                return;
            }
            
            // 记录状态
            mCustomView = view;
            mCustomViewCallback = callback;
            
            // 隐藏 WebView 和 ProgressBar
            webView.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE); 
            
            // 【增强代码】进入沉浸式全屏模式，隐藏状态栏和导航栏 (保持不变)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                
                getWindow().getDecorView().setSystemUiVisibility(uiOptions);
            }
            
            // 【关键修复】将全屏视图添加到 Activity 根布局
            activityMainRoot.addView(mCustomView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.MATCH_PARENT));
            
            // **[防黑屏修复 1/2] 切换到软件渲染，防止 SurfaceView 残留**
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null); 
        }

        /**
         * 【核心防黑屏修复】退出自定义视图（视频全屏）。
         */
        @Override
        public void onHideCustomView() {
            super.onHideCustomView();

            if (mCustomView == null) {
                return;
            }
            
            // 【关键修复】从 Activity 根布局中移除全屏视图
            activityMainRoot.removeView(mCustomView);
            
            // 恢复 WebView 的可见性 
            webView.setVisibility(View.VISIBLE);
            
            // 退出全屏后，清空状态
            mCustomView = null;
            if (mCustomViewCallback != null) {
                mCustomViewCallback.onCustomViewHidden();
                mCustomViewCallback = null;
            }
            
            // 【增强代码】退出全屏，恢复到 onCreate 中设置的 UI 模式 (保持不变)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    uiOptions &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    uiOptions &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                
                getWindow().getDecorView().setSystemUiVisibility(uiOptions);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    getWindow().setNavigationBarColor(Color.BLACK);
                }
            }
            

            // **[防黑屏修复 2/2] 使用 Handler 强制进行分步重绘和硬件加速重置** (保持不变)
            handler.postDelayed(() -> {
                // 1. 强制重新启用硬件加速 
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                
                // 2. 强制请求布局和重绘
                webView.requestLayout();
                webView.invalidate();
                webView.loadUrl("javascript:void(0)"); 

                // 3. 延迟执行 Scroll Hack，进一步刺激渲染
                handler.postDelayed(() -> {
                     webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                     webView.scrollTo(webView.getScrollX() + 1, webView.getScrollY());
                     webView.scrollTo(webView.getScrollX() - 1, webView.getScrollY());
                     Log.d("BlackScreenFix", "全屏退出黑屏修复完成 (Scroll Hack)。");
                }, 200); 
                
            }, 50); 
        }
    }
    
    
    /**
     * 【新增方法】处理 TV 遥控器的输入事件。
     */
    private boolean handleTvInput(int keyCode) {
        // 1. 处理 TV 媒体键：快进/快退 (通常在全屏播放时有效)
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                // 注入 JS 快进操作：要求前端定义 window.videoSeek(seconds) 方法
                executeJavaScript("window.videoSeek(" + SEEK_SECONDS + ")");
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                // 注入 JS 快退操作：要求前端定义 window.videoSeek(seconds) 方法
                executeJavaScript("window.videoSeek(-" + SEEK_SECONDS + ")");
                return true;
        }

        // 2. 处理 D-Pad 导航 (非全屏模式下)
        if (mCustomView == null) { 
            switch (keyCode) {
                // TV D-Pad 方向键
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    // 示例：将遥控器按键事件和代码传递给 Web JS (需要前端配合)
                    // 要求前端定义 window.handleTvKey(keyCode) 方法
                    executeJavaScript("window.handleTvKey(" + keyCode + ")"); 
                    // 返回 false，让 WebView 继续尝试处理焦点（最佳实践）
                    return false; 
            }
        } 
        
        return false;
    }


    /**
     * 【核心修改】处理返回键：优先退出视频全屏，其次是页面回退。
     * 现在将 D-Pad 处理合并到 onKeyDown 中，确保在 Activity 层面拦截。
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 1. 优先处理全屏退出
        if (keyCode == KeyEvent.KEYCODE_BACK && mCustomView != null) {
            webView.getWebChromeClient().onHideCustomView(); 
            return true;
        }
        
        // 2. 其次处理页面回退
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        
        // 3. 最后处理 TV D-Pad 和媒体键
        if (handleTvInput(keyCode)) {
            // 如果 handleTvInput 返回 true (比如处理了媒体键)，则消耗事件
            return true;
        }
        
        return super.onKeyDown(keyCode, event);
    }
    
    // 确保在 Activity 暂停或停止时也隐藏自定义视图 (保持不变)
    @Override
    protected void onPause() {
        super.onPause();
        if (mCustomView != null) {
            webView.getWebChromeClient().onHideCustomView(); 
        }
    }


    // 防止 WebView 内存泄漏 (保持不变)
    @Override
    protected void onDestroy() {
        // 【屏幕常亮清理】
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        if (webView != null) {
            webView.removeJavascriptInterface("Android"); 
            webView.destroy();
        }
        super.onDestroy();
    }

    /**
     * JavaScript Interface Class: 仅保留 getClipboardText 方法。(保持不变)
     */
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        /**
         * Exposed to JavaScript: retrieves clipboard text content.
         */
        @JavascriptInterface
        public String getClipboardText() {
            try {
                ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                    if (item != null && item.getText() != null) {
                        return item.getText().toString().trim();
                    }
                }
            } catch (Exception e) {
                Log.e("WebAppInterface", "Error accessing clipboard.", e);
            }
            return "";
        }
    }
}
