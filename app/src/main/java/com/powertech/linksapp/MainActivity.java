package com.powertech.linksapp;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Main Activity for the Links App.
 * Handles WebView setup, immersive mode, JavaScript bridge, and video fullscreen support.
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    
    // 用于处理视频全屏的视图和容器
    private View mCustomView;
    private FrameLayout mCustomViewContainer;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;

    // 您的 Worker 地址 (用于 WebView 加载和作为 Referer)
    private static final String TARGET_URL = "https://powertech.m3u8-ads.workers.dev/";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ==================================================================
        // Set immersive fullscreen mode
        // ==================================================================
        final View decorView = getWindow().getDecorView();
        // Hides status bar, navigation bar, and enables sticky immersive mode
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
        
        // 监听 Window 焦点变化，确保全屏模式始终有效
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                // 恢复全屏模式
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                );
            }
        });
        // ==================================================================
        
        // Assumes R.layout.activity_main contains WebView (id: webview) and ProgressBar (id: progress_bar)
        setContentView(R.layout.activity_main); 

        // 初始化视图
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        // 初始化全屏容器
        mCustomViewContainer = findViewById(android.R.id.content); // 使用默认的 content 容器

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
        
        // 允许 HTML5 视频播放全屏
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false); // 允许自动播放
        
        // Inject JavaScript Interface, name it "Android"
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // 设置 WebViewClient 来处理页面加载
        webView.setWebViewClient(new WebViewClient() {
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
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(ProgressBar.GONE);
            }
        });

        // 设置 WebChromeClient 来处理进度条和视频全屏
        webView.setWebChromeClient(new MyWebChromeClient());

        // 加载目标网站
        webView.loadUrl(TARGET_URL);
    }

    /**
     * 自定义的 WebChromeClient，处理视频全屏逻辑
     */
    public class MyWebChromeClient extends WebChromeClient {
        
        // 处理进度条变化
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (newProgress < 100) {
                progressBar.setProgress(newProgress);
                if (progressBar.getVisibility() != ProgressBar.VISIBLE) {
                    progressBar.setVisibility(ProgressBar.VISIBLE);
                }
            } else {
                progressBar.setVisibility(ProgressBar.GONE);
            }
        }

        // 处理视频全屏请求
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (mCustomView != null) {
                callback.onCustomViewHidden();
                return;
            }
            
            // 1. 隐藏 WebView 进度条
            progressBar.setVisibility(View.GONE);
            
            // 2. 隐藏 WebView
            webView.setVisibility(View.GONE);
            
            // 3. 设置全屏视图
            mCustomView = view;
            mCustomViewCallback = callback;

            // 4. 将全屏视频视图添加到容器中
            mCustomViewContainer.addView(mCustomView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            mCustomViewContainer.setVisibility(View.VISIBLE);
            
            // 5. 隐藏系统的导航栏和状态栏（针对全屏视频）
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
            );
        }

        // 处理退出视频全屏请求
        @Override
        public void onHideCustomView() {
            if (mCustomView == null) {
                return;
            }

            // 1. 恢复系统的导航栏和状态栏（返回应用全屏模式）
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );

            // 2. 移除全屏视频视图
            mCustomViewContainer.removeView(mCustomView);
            mCustomViewContainer.setVisibility(View.GONE);
            mCustomView = null;
            mCustomViewCallback.onCustomViewHidden();
            
            // 3. 显示 WebView
            webView.setVisibility(View.VISIBLE);
        }
    }

    // 处理返回键：优先退出视频全屏
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 1. 如果当前处于视频全屏模式，按返回键先退出全屏
        if (keyCode == KeyEvent.KEYCODE_BACK && mCustomView != null) {
            ((MyWebChromeClient) webView.getWebChromeClient()).onHideCustomView();
            return true;
        }
        
        // 2. 如果 WebView 可以返回，则执行页面返回操作
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // 防止 WebView 内存泄漏
    @Override
    protected void onDestroy() {
        if (webView != null) {
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
         * JS Call: Android.getClipboardText()
         * @return The text content of the primary clip, or an empty string.
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
                e.printStackTrace();
            }
            return "";
        }

        /**
         * Exposed to JavaScript: starts a download task (attempts to launch 1DM+).
         * JS Call: Android.startDownload(downloadUrl, fileName)
         * @param downloadUrl The actual URL to download (e.g., M3U8 link).
         * @param fileName The suggested name for the downloaded file.
         */
        @JavascriptInterface
        public void startDownload(final String downloadUrl, final String fileName) {
            // 【关键修复】使用用户提供的正确的 1DM+ 包名
            final String IDM_PACKAGE = "idm.internet.download.manager.plus"; 

            // 1. 创建 final 变量来保存文件名
            String tempFileName = fileName.trim();
            if (tempFileName.toLowerCase().endsWith(".m3u8")) {
                tempFileName = tempFileName.replace(".m3u8", ".mp4").trim();
            } 
            
            // 将处理后的文件名声明为 final，供 Lambda 表达式使用
            final String finalSuggestedFileName = tempFileName;

            // UI operations (like Toast) must run on the main thread
            runOnUiThread(() -> {
                boolean success = false;

                // 1. 尝试使用用户提供的包名启动
                success = attemptStartIDM(IDM_PACKAGE, downloadUrl, finalSuggestedFileName); 

                // 2. 如果启动失败，通知用户并回退到复制链接
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
         * @param packageName The package name of the target downloader (e.g., com.dv.aidm.downloader).
         * @param downloadUrl The URL to pass to the downloader.
         * @param fileName The suggested file name.
         * @return true if the Intent was successfully launched, false otherwise.
         */
        private boolean attemptStartIDM(String packageName, String downloadUrl, String fileName) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(downloadUrl));

                // Force the intent to be handled by the specific downloader app
                intent.setPackage(packageName);

                // Add extra information (title and Referer are important for download managers)
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                intent.putExtra("url", downloadUrl);
                intent.putExtra("Referer", TARGET_URL); // Pass the worker URL as the Referer

                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Check if any app is installed that can handle this Intent (i.e., 1DM+)
                if (mContext.getPackageManager().resolveActivity(intent, 0) != null) {
                    mContext.startActivity(intent);
                    Toast.makeText(mContext, "🚀 任务已发送给 1DM+：" + fileName, Toast.LENGTH_LONG).show();
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Return false on exception (e.g., security exception)
                return false;
            }
        }
    }
}
