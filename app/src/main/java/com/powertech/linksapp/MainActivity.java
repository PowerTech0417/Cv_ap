package com.powertech.vip.app; 

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
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
 * 针对 HLS.js 前端优化：
 * 1. 移除自定义 WebChromeClient 视频全屏拦截 (onShowCustomView/onHideCustomView)，
 * 交由系统原生处理 <video> 元素的全屏请求，以避免与前端 HLS.js 播放器冲突。
 * 2. 保留强大的返回键处理和 WebView 硬件加速重置机制（防黑屏）。
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    
    // 用于处理视频全屏的视图和容器 (保留定义，用于 onKeyDown 检查和硬件加速修复)
    private View mCustomView; // 用于跟踪当前是否有自定义全屏视图
    private FrameLayout mCustomViewContainer; 
    private WebChromeClient.CustomViewCallback mCustomViewCallback; // 用于退出全屏

    // 您的 Worker 地址 (用于 WebView 加载和作为 Referer)
    private static final String TARGET_URL = "https://app.key-3b8.workers.dev/";
    // 1DM+ 的包名
    private static final String IDM_PACKAGE = "idm.internet.download.manager.plus";
    
    // 引入 Handler
    private final Handler handler = new Handler(); 

    @SuppressLint({"SetJavaScriptEnabled", "InlinedApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 假设 R.layout.activity_main 包含 WebView (id: webview) 和 ProgressBar (id: progress_bar)
        setContentView(R.layout.activity_main); 

        // 初始化视图
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        
        // 使用根视图 (android.R.id.content) 作为全屏视频的容器
        // **注意：由于移除了自定义全屏逻辑，这个容器主要用于原生 WebChromeClient 的默认行为。**
        mCustomViewContainer = (FrameLayout) findViewById(android.R.id.content); 

        // 配置 WebView 设置
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
        webSettings.setMediaPlaybackRequiresUserGesture(false); // 允许自动播放
        
        // 处理混合内容：允许 HTTPS 页面加载 HTTP 资源 (对媒体流至关重要)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        // 注入 JavaScript 接口，名称为 "Android"
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // 设置 WebViewClient 来处理页面加载、链接跳转和错误
        webView.setWebViewClient(new CustomWebViewClient());

        // 设置 WebChromeClient 来处理进度条和视频全屏、Console Log
        // 使用新的 CustomWebChromeClient
        webView.setWebChromeClient(new CustomWebChromeClient());

        // 显式加载目标网站并设置 Referer
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

    /**
     * 自定义的 WebViewClient，处理页面加载、链接跳转和错误。
     */
    public class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            // 所有链接都在 WebView 内打开
            view.loadUrl(request.getUrl().toString());
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(ProgressBar.VISIBLE);
            progressBar.setProgress(0); // 重置进度条
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
        }

        // 处理页面加载错误 (API 23+)
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                String description = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M 
                                     ? error.getDescription().toString() 
                                     : "加载失败";
                Toast.makeText(MainActivity.this, "网页加载错误: " + description, Toast.LENGTH_LONG).show();
            }
        }
        
        // 处理页面加载错误 (API < 23)
        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (failingUrl.equals(view.getUrl())) {
                Toast.makeText(MainActivity.this, "网页加载错误: " + description, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 自定义的 WebChromeClient，处理进度条、视频全屏和 Console Log。
     * **修复：移除自定义的 onShowCustomView 和 onHideCustomView，让系统原生处理全屏。**
     * **保留：利用 onShowCustomView 的回调来更新 mCustomView 状态，并强制进行硬件加速重置，作为防黑屏的最后保险。**
     */
    public class CustomWebChromeClient extends WebChromeClient {
        
        // 处理进度条变化
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
        
        // 捕获 JS Console 输出，用于调试
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
             Log.d("WebViewConsole", consoleMessage.message() + " -- From line "
                   + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
            return true;
        }

        // **【保留：用于状态跟踪和预处理】**
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            // 确保退出旧视图 (原生 WebChromeClient 行为)
            if (mCustomView != null) {
                callback.onCustomViewHidden();
                return;
            }
            
            progressBar.setVisibility(View.GONE);
            
            // **[优化] 记录状态**
            mCustomView = view;
            mCustomViewCallback = callback;
            
            // **[增强修复 1/2] 进入全屏前，临时切换到软件渲染，防止底层 SurfaceView 残留**
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null); 

            // **交由原生 WebChromeClient 的默认实现来添加 View 和设置全屏标志**
            super.onShowCustomView(view, callback);
        }

        // **【保留：用于状态跟踪和防黑屏修复】**
        @Override
        public void onHideCustomView() {
            if (mCustomView == null) {
                super.onHideCustomView(); // 执行原生退出逻辑
                return;
            }

            // **[优化] 记录状态**
            mCustomView = null;
            // **必须先调用 super，让系统移除 View 并恢复 UI 标志**
            super.onHideCustomView();

            // **4. 【核心黑屏修复 2/2】使用 Handler 强制进行分步重绘和硬件加速重置**
            handler.postDelayed(() -> {
                Log.d("BlackScreenFix", "Phase 1: Starting hardware acceleration reset.");
                
                // 4.1. 强制重新启用硬件加速 (关键步骤)
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                
                // 4.2. 强制请求布局和重绘
                webView.requestLayout();
                webView.invalidate();

                // 4.3. 临时加载一个空白 URL，迫使 WebView 引擎刷新
                webView.loadUrl("javascript:void(0)");
                
                // 4.4. 延迟 200ms 后，再次确认硬件加速状态，并执行 Scroll Hack，进一步刺激渲染
                handler.postDelayed(() -> {
                     webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                     // 滚动 hack：移动 1 像素再移回，强制重绘
                     webView.scrollTo(webView.getScrollX() + 1, webView.getScrollY());
                     webView.scrollTo(webView.getScrollX() - 1, webView.getScrollY());
                     Log.d("BlackScreenFix", "Phase 2: Final render scroll hack executed.");
                }, 200); 
                
            }, 50); // 延迟 50ms 运行
        }
    }

    // 处理返回键：优先退出视频全屏
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 1. 如果当前处于视频全屏模式，按返回键先退出全屏
        // mCustomView 是在 onShowCustomView 中设置的，只要系统 WebChromeClient 触发了全屏，这个就会被设置。
        if (keyCode == KeyEvent.KEYCODE_BACK && mCustomView != null) {
            // 使用 WebChromeClient 的 onHideCustomView 方法
            webView.getWebChromeClient().onHideCustomView();
            return true;
        }
        
        // 2. 如果 WebView 可以返回，则执行页面返回操作
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    // 【增强】确保在暂停时也隐藏自定义视图，防止 Activity 生命周期导致的问题
    @Override
    protected void onPause() {
        super.onPause();
        // 必须通过 getWebChromeClient() 调用 onHideCustomView()
        if (mCustomView != null) {
            webView.getWebChromeClient().onHideCustomView();
        }
    }


    // 防止 WebView 内存泄漏
    @Override
    protected void onDestroy() {
        if (webView != null) {
            // 移除所有接口，防止泄漏
            webView.removeJavascriptInterface("Android"); 
            // 销毁 WebView 实例
            webView.destroy();
        }
        super.onDestroy();
    }

    /**
     * JavaScript Interface Class: exposes native Android methods to WebView JS code.
     * JS object name: "Android"
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

        /**
         * Exposed to JavaScript: starts a download task (attempts to launch 1DM+).
         */
        @JavascriptInterface
        public void startDownload(final String downloadUrl, final String fileName) {

            Log.d("DownloadTask", "JS successfully called startDownload. URL: " + downloadUrl + ", File: " + fileName);
            
            // 1. 文件名处理
            String tempFileName = fileName.trim();
            if (tempFileName.isEmpty()) { tempFileName = "download_task"; }
            // 确保文件名有后缀
            if (!tempFileName.toLowerCase().contains(".")) {
                 tempFileName += ".mp4"; 
            } else if (tempFileName.toLowerCase().endsWith(".m3u8")) {
                tempFileName = tempFileName.replace(".m3u8", ".mp4").trim();
            } 
            
            final String finalSuggestedFileName = tempFileName;

            // UI operations (like Toast) must run on the main thread
            runOnUiThread(() -> {
                boolean success = attemptStartIDM(IDM_PACKAGE, downloadUrl, finalSuggestedFileName); 
                
                Log.d("DownloadTask", "Attempting 1DM+ launch result: " + (success ? "SUCCESS" : "FAILED"));

                if (!success) {
                    Toast.makeText(mContext, "⚠️ 找不到 1DM+ 或启动失败，请检查是否已安装正确的版本。", Toast.LENGTH_LONG).show();

                    // Copy link to clipboard
                    ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Download Link", downloadUrl);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(mContext, "下载链接已复制到剪贴板。", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        /**
         * Helper method: attempts to launch a downloader with a specific package name.
         */
        private boolean attemptStartIDM(String packageName, String downloadUrl, String fileName) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(downloadUrl));

                intent.setPackage(packageName);

                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                intent.putExtra("url", downloadUrl);
                intent.putExtra("Referer", TARGET_URL); // 传递 worker URL 作为 Referer

                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (mContext.getPackageManager().resolveActivity(intent, 0) != null) {
                    mContext.startActivity(intent);
                    Toast.makeText(mContext, "🚀 任务已发送给 1DM+：" + fileName, Toast.LENGTH_LONG).show();
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                Log.e("DownloadTask", "Error attempting to launch IDM+.", e); 
                return false;
            }
        }
    }
}
