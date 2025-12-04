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
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Main Activity for the Links App.
 * Handles WebView setup, immersive mode, and JavaScript bridge integration.
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;

    // Your Worker Address (used for WebView loading and as Referer)
    private static final String TARGET_URL = "https://powertech.m3u8-ads.workers.dev/";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ==================================================================
        // Set immersive fullscreen mode (must be called before setting content for best effect)
        // ==================================================================
        View decorView = getWindow().getDecorView();
        // Hides status bar, navigation bar, and enables sticky immersive mode
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
        // ==================================================================

        // Assumes R.layout.activity_main contains WebView (id: webview) and ProgressBar (id: progress_bar)
        setContentView(R.layout.activity_main);

        // Initialize views
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);

        // Configure WebView settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // Mandatory for JS interaction
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

        // Inject JavaScript Interface, name it "Android"
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // Set WebViewClient to handle page loading and navigation
        webView.setWebViewClient(new WebViewClient() {
            // Deprecated, but good for older Android versions
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // All links open within the WebView
                view.loadUrl(url);
                return true;
            }

            // Standard for modern Android
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // All links open within the WebView
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // Show progress bar when page starts loading
                progressBar.setVisibility(ProgressBar.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Hide progress bar when page finishes loading
                progressBar.setVisibility(ProgressBar.GONE);
            }
        });

        // Set WebChromeClient to handle progress updates
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress < 100) {
                    progressBar.setProgress(newProgress);
                    // Ensure visibility check is done here too, for initial loading
                    if (progressBar.getVisibility() != ProgressBar.VISIBLE) {
                        progressBar.setVisibility(ProgressBar.VISIBLE);
                    }
                } else {
                    progressBar.setVisibility(ProgressBar.GONE);
                }
            }
        });

        // Load the target website
        webView.loadUrl(TARGET_URL);
    }

    // Handle back button press
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // If WebView can go back, perform the back operation
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // Prevent WebView memory leak
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
                // Log exception but return empty string to avoid JS crash
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
        public void startDownload(String downloadUrl, String fileName) {
            // Common package names for 1DM+ (IDM+)
            final String IDM_PACKAGE = "com.dv.aidm.downloader";
            final String IDM_PACKAGE_ALT = "com.dv.aidm";

            // Suggest .mp4 extension for M3U8 links to encourage downloader auto-packaging
            String suggestedFileName = fileName;
            if (fileName.toLowerCase().endsWith(".m3u8")) {
                suggestedFileName = fileName.replace(".m3u8", ".mp4").trim();
            } else {
                suggestedFileName = fileName.trim();
            }

            // UI operations (like Toast) must run on the main thread
            runOnUiThread(() -> {
                boolean success = false;

                // 1. Try starting with the primary 1DM+ package name
                success = attemptStartIDM(IDM_PACKAGE, downloadUrl, suggestedFileName);

                // 2. If failed, try the alternative package name
                if (!success) {
                    success = attemptStartIDM(IDM_PACKAGE_ALT, downloadUrl, suggestedFileName);
                }

                // 3. If all attempts failed, notify user and fallback to copying the link
                if (!success) {
                    Toast.makeText(mContext, "⚠️ Cannot find 1DM+ or launch failed. Please ensure it is installed.", Toast.LENGTH_LONG).show();

                    // Copy link to clipboard
                    ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Download Link", downloadUrl);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(mContext, "Download link copied to clipboard.", Toast.LENGTH_SHORT).show();
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
                // This check is necessary for Android's security model (resolveActivity)
                if (mContext.getPackageManager().resolveActivity(intent, 0) != null) {
                    mContext.startActivity(intent);
                    Toast.makeText(mContext, "🚀 Task sent to 1DM+: " + fileName, Toast.LENGTH_LONG).show();
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
