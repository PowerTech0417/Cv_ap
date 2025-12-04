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
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    
    // 您的 Worker 地址 (用于 WebView 加载和作为 Referer)
    private static final String TARGET_URL = "https://linkapp.powertech.workers.dev/";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 假设您的布局文件 R.layout.activity_main 包含 WebView 和 ProgressBar
        setContentView(R.layout.activity_main); 

        // 初始化视图
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);

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
        
        // 【关键】注入 JavaScript 接口，名称为 "Android"
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

        // 设置 WebChromeClient 来处理进度条
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress < 100) {
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(ProgressBar.GONE);
                }
            }
        });

        // 加载目标网站
        webView.loadUrl(TARGET_URL);
    }

    // 处理返回键
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 如果 WebView 可以返回，则执行返回操作
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

    // ==================================================================
    // 【关键】定义 JavaScript 接口类
    // ==================================================================
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        /**
         * 暴露给 JavaScript 的方法：获取剪贴板内容
         * JS 调用: Android.getClipboardText()
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
         * 暴露给 JavaScript 的方法：启动下载任务（尝试启动 1DM+）
         * JS 调用: Android.startDownload(downloadUrl, fileName)
         */
        @JavascriptInterface
        public void startDownload(String downloadUrl, String fileName) {
            // 1DM+（IDM+）的常用包名
            final String IDM_PACKAGE = "com.dv.aidm.downloader"; 
            // 另一个常见的 IDM 变体包名
            final String IDM_PACKAGE_ALT = "com.dv.aidm"; 

            // 优化文件名，确保给下载器的建议名称以 .mp4 结尾，以鼓励下载器自动封装
            final String suggestedFileName = fileName.replace(".m3u8", ".mp4").trim(); 

            runOnUiThread(() -> {
                boolean success = false;
                
                // 1. 尝试使用 IDM+ 的主包名启动 Intent
                success = attemptStartIDM(IDM_PACKAGE, downloadUrl, suggestedFileName);

                // 2. 如果失败，尝试使用备用包名
                if (!success) {
                     success = attemptStartIDM(IDM_PACKAGE_ALT, downloadUrl, suggestedFileName);
                }

                // 3. 如果所有尝试都失败，提示用户并回退到复制链接
                if (!success) {
                     Toast.makeText(mContext, "⚠️ 找不到 1DM+ 或启动失败，请检查是否已安装。", Toast.LENGTH_LONG).show();
                     
                     // 复制链接到剪贴板
                     ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                     ClipData clip = ClipData.newPlainText("Download Link", downloadUrl);
                     clipboard.setPrimaryClip(clip);
                     Toast.makeText(mContext, "下载链接已复制到剪贴板。", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        /**
         * 辅助方法：尝试启动特定包名的下载器
         */
        private boolean attemptStartIDM(String packageName, String downloadUrl, String fileName) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(downloadUrl));
                
                // 【关键】使用 setPackage 强制指定目标下载器
                intent.setPackage(packageName); 
                
                // 尝试添加额外信息
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                intent.putExtra("url", downloadUrl); 
                intent.putExtra("Referer", TARGET_URL); // 添加 Referer
                
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // 检查是否有应用可以处理这个 Intent（即 1DM+ 是否安装）
                if (intent.resolveActivity(mContext.getPackageManager()) != null) {
                    mContext.startActivity(intent);
                    Toast.makeText(mContext, "🚀 任务已发送给 1DM+：" + fileName, Toast.LENGTH_LONG).show();
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}
