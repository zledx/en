package com.enabcd.navigation;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://enabcd.cn/";
    private static final String HOT_URL = "https://enabcd.cn/hot.php";
    private static final String MAGIC_URL = "https://enabcd.cn/magic.php";

    private static final int PAGE_BG = Color.rgb(247, 248, 251);
    private static final int TEXT = Color.rgb(31, 35, 41);
    private static final int MUTED = Color.rgb(126, 133, 145);
    private static final int ACCENT = Color.rgb(3, 103, 253);

    private FrameLayout root;
    private WebView webView;
    private FrameLayout navShell;
    private ImageView glassBackdrop;
    private View glassTint;
    private LinearLayout bottomNav;
    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private SharedPreferences prefs;

    private final Map<String, LinearLayout> navItems = new HashMap<>();
    private final Map<String, TextView> navLabels = new HashMap<>();

    private boolean showingAbout = false;
    private boolean showingSettings = false;
    private int homeHistoryIndex = -1;
    private boolean glassRefreshPosted = false;
    private Bitmap glassBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("en_navigation_app", MODE_PRIVATE);
        configureSystemBars();
        buildShell();
        configureWebView();
        installPermanentLightMode();

        webView.setAlpha(0f);
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            webView.postDelayed(this::revealWebView, 180);
        } else {
            webView.loadUrl(HOME_URL + "?enapp=1");
        }
        setActiveNav("home");
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(PAGE_BG);
        window.setNavigationBarColor(PAGE_BG);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(PAGE_BG);

        webView = new WebView(this);
        webView.setBackgroundColor(PAGE_BG);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        navShell = new FrameLayout(this);
        navShell.setBackground(roundRect(Color.TRANSPARENT, dp(999)));
        navShell.setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) navShell.setElevation(dp(10));

        glassBackdrop = new ImageView(this);
        glassBackdrop.setScaleType(ImageView.ScaleType.FIT_XY);
        navShell.addView(glassBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        glassTint = new View(this);
        glassTint.setBackgroundColor(Color.argb(138, 255, 255, 255));
        navShell.addView(glassTint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(5), dp(5), dp(5), dp(5));
        navShell.addView(bottomNav, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        addNavItem("home", "首页", this::showHomeWithoutReload);
        addNavItem("hot", "热榜", () -> navigateTo("hot", HOT_URL));
        addNavItem("magic", "魔盒", () -> navigateTo("magic", MAGIC_URL));
        addNavItem("about", "关于", this::showAboutPage);
        addNavItem("settings", "设置", this::showSettingsPage);

        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52),
                Gravity.BOTTOM
        );
        navParams.setMargins(dp(18), 0, dp(18), dp(10));
        root.addView(navShell, navParams);
        setContentView(root);

        navShell.postDelayed(this::scheduleGlassRefresh, 350);
    }

    private void addNavItem(String key, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setPadding(dp(2), dp(2), dp(2), dp(2));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextColor(MUTED);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        item.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        item.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(.97f).scaleY(.97f).setDuration(55).withEndAction(() ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
            action.run();
        });

        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        itemLp.setMargins(dp(1), 0, dp(1), 0);
        bottomNav.addView(item, itemLp);
        navItems.put(key, item);
        navLabels.put(key, labelView);
    }

    private void setActiveNav(String key) {
        for (Map.Entry<String, LinearLayout> entry : navItems.entrySet()) {
            boolean active = entry.getKey().equals(key);
            LinearLayout item = entry.getValue();
            TextView label = navLabels.get(entry.getKey());
            item.setBackground(active ? roundRect(Color.argb(145, 238, 245, 255), dp(999)) : null);
            if (label != null) label.setTextColor(active ? ACCENT : MUTED);
        }
    }

    private void navigateTo(String key, String url) {
        showingAbout = false;
        showingSettings = false;
        setActiveNav(key);
        String current = webView.getUrl();
        if (current != null && current.equals(url)) return;
        webView.loadUrl(url);
    }

    private void showHomeWithoutReload() {
        setActiveNav("home");
        if (!showingAbout && !showingSettings && isHomeUrl(webView.getUrl())) return;

        showingAbout = false;
        showingSettings = false;

        WebBackForwardList list = webView.copyBackForwardList();
        int currentIndex = list.getCurrentIndex();
        int targetIndex = -1;

        if (homeHistoryIndex >= 0 && homeHistoryIndex < list.getSize()) {
            WebHistoryItem item = list.getItemAtIndex(homeHistoryIndex);
            if (item != null && isHomeUrl(item.getUrl())) targetIndex = homeHistoryIndex;
        }

        if (targetIndex < 0) {
            for (int i = currentIndex - 1; i >= 0; i--) {
                WebHistoryItem item = list.getItemAtIndex(i);
                if (item != null && isHomeUrl(item.getUrl())) {
                    targetIndex = i;
                    break;
                }
            }
        }

        if (targetIndex >= 0) {
            homeHistoryIndex = targetIndex;
            webView.goBackOrForward(targetIndex - currentIndex);
        } else {
            webView.loadUrl(HOME_URL + "?enapp=1");
        }
    }

    private void showAboutPage() {
        showingAbout = true;
        showingSettings = false;
        setActiveNav("about");
        webView.setAlpha(1f);
        webView.loadDataWithBaseURL("https://enabcd.cn/app-about", buildAboutHtml(), "text/html", "UTF-8", null);
    }

    private void showSettingsPage() {
        showingSettings = true;
        showingAbout = false;
        setActiveNav("settings");
        webView.setAlpha(1f);
        webView.loadDataWithBaseURL("https://enabcd.cn/app-settings", buildSettingsHtml(), "text/html", "UTF-8", null);
    }

    private String buildAboutHtml() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'>" +
                "<style>*{box-sizing:border-box}html,body{margin:0;background:#f7f8fb;color:#1f2329;color-scheme:light;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif}body{padding:74px 18px 94px}.head{padding:2px 3px 22px}.name{font-size:27px;font-weight:800;letter-spacing:-.02em}.desc{margin-top:6px;font-size:13px;line-height:1.65;color:#7e8591}.card{margin:0 6px 12px;background:#fff;border-radius:15px;overflow:hidden;box-shadow:0 5px 18px rgba(30,45,70,.035)}a{height:56px;padding:0 17px;text-decoration:none;color:#20242b;display:flex;align-items:center;justify-content:space-between;font-size:14px;font-weight:700;border-bottom:1px solid #edf0f4}a:last-child{border-bottom:0}.arrow{color:#a2a9b3;font-size:20px;font-weight:400}</style></head><body>" +
                "<div class='head'><div class='name'>en导航</div><div class='desc'>优质资源极简主义导航</div></div>" +
                "<div class='card'><a href='https://enabcd.cn/privacy.php'><span>隐私政策</span><span class='arrow'>›</span></a>" +
                "<a href='https://enabcd.cn/disclaimer.php'><span>免责声明</span><span class='arrow'>›</span></a>" +
                "<a href='https://enabcd.cn/contact.php'><span>联系我们</span><span class='arrow'>›</span></a></div></body></html>";
    }

    private String buildSettingsHtml() {
        boolean announcement = prefs.getBoolean("show_announcement", true);
        boolean randomVideo = prefs.getBoolean("show_random_video", true);
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'>" +
                "<style>*{box-sizing:border-box}html,body{margin:0;background:#f7f8fb;color:#1f2329;color-scheme:light;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif}body{padding:74px 16px 94px}.title{font-size:27px;font-weight:800;padding:2px 3px 4px}.sub{font-size:12px;color:#858c98;padding:0 3px 22px}.section{font-size:11px;font-weight:800;color:#989faa;padding:5px 6px 8px}.card{background:#fff;border-radius:15px;overflow:hidden;margin:0 4px 14px;box-shadow:0 5px 18px rgba(30,45,70,.035)}.row{min-height:62px;padding:10px 15px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #edf0f4;text-decoration:none;color:#20242b}.row:last-child{border-bottom:0}.txt{min-width:0;padding-right:14px}.name{font-size:14px;font-weight:700}.desc{font-size:11px;color:#8a919d;margin-top:4px;line-height:1.4}.switch{width:44px;height:25px;border-radius:13px;background:#d9dee6;padding:3px;flex:none}.switch:after{content:'';display:block;width:19px;height:19px;border-radius:50%;background:#fff;box-shadow:0 2px 5px rgba(0,0,0,.18)}.switch.on{background:#0367fd}.switch.on:after{transform:translateX(19px)}.arrow{font-size:20px;color:#a2a9b3}.version{text-align:center;color:#a0a6b0;font-size:10.5px;padding:8px}</style></head><body>" +
                "<div class='title'>设置</div><div class='sub'>仅对 Android APP 生效 · 固定浅色模式</div>" +
                "<div class='section'>内容显示</div><div class='card'>" +
                settingsToggleRow("公告栏", "显示站点公告与往期公告入口", "show_announcement", announcement) +
                settingsToggleRow("随机小姐姐", "显示随机视频入口", "show_random_video", randomVideo) +
                "</div><div class='section'>页面</div><div class='card'>" +
                "<a class='row' href='enapp://reload'><div class='txt'><div class='name'>重新加载首页</div><div class='desc'>仅在需要时手动刷新首页内容</div></div><div class='arrow'>›</div></a>" +
                "<a class='row' href='enapp://clear-cache'><div class='txt'><div class='name'>清除页面缓存</div><div class='desc'>清理 WebView 缓存并重新载入首页</div></div><div class='arrow'>›</div></a>" +
                "</div><div class='version'>en导航 Android · v1.7.0</div></body></html>";
    }

    private String settingsToggleRow(String title, String desc, String key, boolean enabled) {
        return "<a class='row' href='enapp://toggle?key=" + key + "'><div class='txt'><div class='name'>" + title + "</div><div class='desc'>" + desc + "</div></div><div class='switch " + (enabled ? "on" : "") + "'></div></a>";
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(settings.getUserAgentString() + " ENNavigationApp/1.7 Android LightOnly");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) settings.setForceDark(WebSettings.FORCE_DARK_OFF);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setCookie(HOME_URL, "theme=light; Path=/; SameSite=Lax");
        cookieManager.setCookie(HOME_URL, "en_theme=light; Path=/; SameSite=Lax");
        cookieManager.flush();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (shouldBlockDarkModeAsset(uri)) {
                    String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
                    String mime = path.contains(".css") ? "text/css" : "application/javascript";
                    return new WebResourceResponse(mime, "UTF-8", new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (!showingAbout && !showingSettings && isOwnWebPage(url)) {
                    view.animate().cancel();
                    view.setAlpha(0f);
                    view.setBackgroundColor(PAGE_BG);
                    injectLightFallback();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!showingAbout && !showingSettings) {
                    updateNavForUrl(url);
                    injectAppUi(url, () -> revealAfterVisualCommit(url));
                    if (isHomeUrl(url)) {
                        WebBackForwardList list = webView.copyBackForwardList();
                        homeHistoryIndex = list.getCurrentIndex();
                    }
                } else {
                    webView.setAlpha(1f);
                    scheduleGlassRefresh();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) showOfflinePage();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams fileChooserParams) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), 1001);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
                navShell.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                webView.setVisibility(View.VISIBLE);
                navShell.setVisibility(View.VISIBLE);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                customViewCallback = null;
                scheduleGlassRefresh();
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    request.addRequestHeader("User-Agent", userAgent);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) request.addRequestHeader("Cookie", cookies);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setTitle("正在下载");
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "en_download_" + System.currentTimeMillis());
                    ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                    Toast.makeText(MainActivity.this, "已开始下载", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    openExternal(Uri.parse(url));
                }
            }
        });
    }

    private boolean shouldBlockDarkModeAsset(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null || !(host.equals("enabcd.cn") || host.equals("www.enabcd.cn") || host.endsWith(".enabcd.cn"))) return false;
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        String file = path.substring(path.lastIndexOf('/') + 1);
        boolean css = file.endsWith(".css");
        boolean js = file.endsWith(".js");
        if (css && (file.contains("dark") || file.contains("night") || file.contains("theme-dark"))) return true;
        if (js && (file.contains("dark") || file.contains("night") || file.equals("theme.js") || file.contains("theme-toggle") || file.contains("theme-mode"))) return true;
        return false;
    }

    private void installPermanentLightMode() {
        String script = "(function(){" +
                "var keys={'theme':1,'en_theme':1,'en-theme':1};" +
                "try{var gs=Storage.prototype.getItem,ss=Storage.prototype.setItem;Storage.prototype.getItem=function(k){if(keys[k])return 'light';return gs.call(this,k)};Storage.prototype.setItem=function(k,v){return ss.call(this,k,keys[k]?'light':v)};localStorage.setItem('theme','light');localStorage.setItem('en_theme','light');localStorage.setItem('en-theme','light')}catch(e){}" +
                "try{var mm=window.matchMedia.bind(window);window.matchMedia=function(q){var s=String(q||'');if(/prefers-color-scheme\\s*:\\s*dark/i.test(s))return{matches:false,media:s,onchange:null,addListener:function(){},removeListener:function(){},addEventListener:function(){},removeEventListener:function(){},dispatchEvent:function(){return false}};if(/prefers-color-scheme\\s*:\\s*light/i.test(s))return{matches:true,media:s,onchange:null,addListener:function(){},removeListener:function(){},addEventListener:function(){},removeEventListener:function(){},dispatchEvent:function(){return false}};return mm(q)}}catch(e){}" +
                "var d=document.documentElement;if(d){d.classList.remove('dark','theme-dark','night','night-mode');d.classList.add('light');d.setAttribute('data-theme','light');d.style.background='#f7f8fb';d.style.colorScheme='light'}" +
                "})();";
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(webView, script, Collections.singleton("https://enabcd.cn"));
            } catch (Throwable ignored) {}
        }
    }

    private void injectLightFallback() {
        if (webView == null || showingAbout || showingSettings) return;
        String js = "(function(){try{var d=document.documentElement;if(!d)return;d.classList.remove('dark','theme-dark','night','night-mode');d.classList.add('light');d.setAttribute('data-theme','light');d.style.background='#f7f8fb';d.style.colorScheme='light';try{localStorage.setItem('theme','light');localStorage.setItem('en_theme','light');localStorage.setItem('en-theme','light')}catch(e){}}catch(e){}})();";
        webView.evaluateJavascript(js, null);
    }

    private void revealAfterVisualCommit(String url) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                webView.postVisualStateCallback(System.nanoTime(), new WebView.VisualStateCallback() {
                    @Override
                    public void onComplete(long requestId) {
                        revealWebView();
                        scheduleGlassRefresh();
                    }
                });
                webView.postDelayed(this::revealWebView, 420);
                return;
            } catch (Throwable ignored) {}
        }
        webView.postDelayed(() -> {
            revealWebView();
            scheduleGlassRefresh();
        }, 90);
    }

    private void revealWebView() {
        if (webView == null || webView.getAlpha() >= .99f) return;
        webView.animate().cancel();
        webView.animate().alpha(1f).setDuration(75).start();
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if ("enapp".equalsIgnoreCase(scheme)) {
            handleAppAction(uri);
            return true;
        }

        String host = uri.getHost();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (host != null && (host.equals("enabcd.cn") || host.equals("www.enabcd.cn") || host.endsWith(".enabcd.cn"))) {
                showingAbout = false;
                showingSettings = false;
                return false;
            }
            openExternal(uri);
            return true;
        }
        openExternal(uri);
        return true;
    }

    private void handleAppAction(Uri uri) {
        String host = uri.getHost();
        if (host == null) return;
        if (host.equals("toggle")) {
            String key = uri.getQueryParameter("key");
            if ("show_announcement".equals(key) || "show_random_video".equals(key)) {
                boolean current = prefs.getBoolean(key, true);
                prefs.edit().putBoolean(key, !current).apply();
                showSettingsPage();
            }
        } else if (host.equals("reload")) {
            forceReloadHome();
        } else if (host.equals("clear-cache")) {
            webView.clearCache(true);
            CookieManager.getInstance().flush();
            Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show();
            forceReloadHome();
        }
    }

    private void forceReloadHome() {
        showingAbout = false;
        showingSettings = false;
        setActiveNav("home");
        webView.loadUrl(HOME_URL + "?enapp=1&_=" + System.currentTimeMillis());
    }

    private void injectAppUi(String url, Runnable after) {
        if (webView == null || showingAbout || showingSettings) {
            if (after != null) after.run();
            return;
        }
        boolean showAnnouncement = prefs.getBoolean("show_announcement", true);
        boolean showRandom = prefs.getBoolean("show_random_video", true);
        boolean home = isHomeUrl(url);
        int topSpace = home ? 0 : 24;

        String js = "(function(){try{" +
                "var d=document.documentElement,b=document.body;if(!d||!b)return;" +
                "d.classList.remove('dark','theme-dark','night','night-mode','en-quicknav-off');d.classList.add('light','en-app-webview');d.setAttribute('data-theme','light');d.style.colorScheme='light';" +
                "try{localStorage.setItem('theme','light');localStorage.setItem('en_theme','light');localStorage.setItem('en-theme','light')}catch(e){}" +
                "document.querySelectorAll('link[rel=stylesheet][href*=dark i],link[rel=stylesheet][href*=night i],script[src*=dark i],script[src*=night i],style[data-theme=dark],style[id*=dark i]').forEach(function(n){n.remove()});" +
                "d.classList.toggle('en-announcement-off'," + (!showAnnouncement) + ");d.classList.toggle('en-random-video-off'," + (!showRandom) + ");" +
                "b.classList.toggle('en-app-home'," + home + ");b.classList.toggle('en-app-nonhome'," + (!home) + ");" +
                "var s=document.getElementById('en-android-app-style');if(!s){s=document.createElement('style');s.id='en-android-app-style';document.head.insertBefore(s,document.head.firstChild);}s.textContent=`" +
                "html,body{color-scheme:light!important;background:#f7f8fb!important}body{padding-bottom:80px!important;margin-top:0!important}body.en-app-home{padding-top:0!important}body.en-app-nonhome{padding-top:" + topSpace + "px!important}" +
                ".site-topbar,.site-topbar-spacer,.site-mobile-nav-trigger,.topbar-placeholder,.top-component,.top-widget,.top-tools,.top-matrix,.matrix-clock,.dot-matrix,.top-clock,.time-widget,.clock-widget,#topClock,#matrixClock,[data-top-component]{display:none!important}" +
                ".site-footer,.footer-wrap,footer.site-footer{display:none!important}" +
                ".theme-switch,.theme-toggle,[data-theme-toggle],#themeSwitch,.appearance-switch{display:none!important}" +
                "#settingsBtn,.search-settings-btn,[data-open-settings],.search-setting-trigger{display:none!important}" +
                ".en-app-webview.en-announcement-off .announcement-card,.en-app-webview.en-announcement-off .announcement-wrap,.en-app-webview.en-announcement-off [data-announcement]{display:none!important}" +
                ".en-app-webview.en-random-video-off .random-video,.en-app-webview.en-random-video-off .random-girl,.en-app-webview.en-random-video-off [data-random-video]{display:none!important}" +
                ".en-app-home main,.en-app-home .main-wrap,.en-app-home .page-main,.en-app-home .content-main{margin-top:0!important;padding-top:0!important}" +
                "#en-app-search-logo-wrap{display:flex!important;align-items:center!important;justify-content:center!important;width:100%!important;margin:3px 0 12px!important;pointer-events:none!important}" +
                "#en-app-search-logo{display:block!important;width:54px!important;height:54px!important;object-fit:contain!important;border-radius:14px!important;box-shadow:0 8px 22px rgba(31,45,70,.10)!important}" +
                "@media(max-width:760px){body{padding-left:0!important;padding-right:0!important}}`;" +
                (home ? "var input=document.querySelector('input[type=search],#searchInput,.search-input,input[name=q],input[name=keyword],input[placeholder*=搜索]');if(input){var anchor=input.closest('.search-card,.search-panel,.search-container,.search-wrap,.search-box')||input.closest('form')||input.parentElement;if(anchor&&anchor.parentNode&&!document.getElementById('en-app-search-logo-wrap')){var lw=document.createElement('div');lw.id='en-app-search-logo-wrap';var li=document.createElement('img');li.id='en-app-search-logo';li.src='https://enabcd.cn/favicon.ico';li.alt='en导航';lw.appendChild(li);anchor.parentNode.insertBefore(lw,anchor);}}" : "") +
                "}catch(e){}})();";
        webView.evaluateJavascript(js, value -> {
            if (after != null) after.run();
        });
    }

    private boolean isOwnWebPage(String url) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return host != null && (host.equals("enabcd.cn") || host.equals("www.enabcd.cn") || host.endsWith(".enabcd.cn"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isHomeUrl(String url) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            String path = uri.getPath();
            boolean own = host != null && (host.equals("enabcd.cn") || host.equals("www.enabcd.cn") || host.endsWith(".enabcd.cn"));
            return own && (path == null || path.isEmpty() || path.equals("/") || path.endsWith("/index.php"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void updateNavForUrl(String url) {
        if (showingAbout) { setActiveNav("about"); return; }
        if (showingSettings) { setActiveNav("settings"); return; }
        if (url == null) { setActiveNav("home"); return; }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("hot.php")) setActiveNav("hot");
        else if (lower.contains("magic.php")) setActiveNav("magic");
        else if (lower.contains("privacy.php") || lower.contains("disclaimer.php") || lower.contains("contact.php")) setActiveNav("about");
        else setActiveNav("home");
    }

    private void scheduleGlassRefresh() {
        if (glassRefreshPosted || navShell == null || webView == null) return;
        glassRefreshPosted = true;
        navShell.postDelayed(() -> {
            glassRefreshPosted = false;
            updateGlassBlur();
        }, 220);
    }

    private void updateGlassBlur() {
        if (navShell.getWidth() <= 0 || navShell.getHeight() <= 0 || webView.getWidth() <= 0 || webView.getHeight() <= 0) return;
        try {
            int[] navLoc = new int[2];
            int[] webLoc = new int[2];
            navShell.getLocationOnScreen(navLoc);
            webView.getLocationOnScreen(webLoc);

            int width = navShell.getWidth();
            int height = navShell.getHeight();
            int sample = 5;
            int smallW = Math.max(1, width / sample);
            int smallH = Math.max(1, height / sample);
            Bitmap source = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(source);
            float scale = 1f / sample;
            canvas.scale(scale, scale);
            canvas.translate(-(navLoc[0] - webLoc[0]), -(navLoc[1] - webLoc[1]));
            webView.draw(canvas);

            Bitmap blurred = blurBitmap(source, 16f);
            source.recycle();
            if (blurred != null) {
                Bitmap old = glassBitmap;
                glassBitmap = blurred;
                glassBackdrop.setImageBitmap(blurred);
                if (old != null && old != blurred && !old.isRecycled()) old.recycle();
            }
        } catch (Throwable ignored) {
            glassBackdrop.setImageDrawable(null);
        }
    }

    private Bitmap blurBitmap(Bitmap input, float radius) {
        RenderScript rs = null;
        Allocation in = null;
        Allocation out = null;
        ScriptIntrinsicBlur blur = null;
        try {
            Bitmap output = Bitmap.createBitmap(input.getWidth(), input.getHeight(), Bitmap.Config.ARGB_8888);
            rs = RenderScript.create(this);
            in = Allocation.createFromBitmap(rs, input);
            out = Allocation.createFromBitmap(rs, output);
            blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            blur.setRadius(Math.max(0.1f, Math.min(25f, radius)));
            blur.setInput(in);
            blur.forEach(out);
            out.copyTo(output);
            return output;
        } catch (Throwable ignored) {
            return input.copy(Bitmap.Config.ARGB_8888, false);
        } finally {
            if (blur != null) blur.destroy();
            if (in != null) in.destroy();
            if (out != null) out.destroy();
            if (rs != null) rs.destroy();
        }
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOfflinePage() {
        showingAbout = false;
        showingSettings = false;
        webView.setAlpha(1f);
        String html = "<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0;background:#f7f8fb;font-family:sans-serif;display:grid;place-items:center;min-height:100vh;color:#202124'><div style='text-align:center;padding:32px'><img src='https://enabcd.cn/favicon.ico' style='width:64px;height:64px;border-radius:16px'><h2 style='margin-bottom:8px'>网络连接失败</h2><p style='color:#777;font-size:13px'>请检查网络后重新加载</p><button onclick=\"location.href='" + HOME_URL + "'\" style='border:0;border-radius:12px;background:#0367fd;color:#fff;padding:12px 24px;font-size:14px;font-weight:700'>重新加载</button></div></body></html>";
        webView.loadDataWithBaseURL(HOME_URL, html, "text/html", "UTF-8", null);
    }

    private GradientDrawable roundRect(int fillColor, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    result = new Uri[count];
                    for (int i = 0; i < count; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.postDelayed(this::scheduleGlassRefresh, 300);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (glassBitmap != null && !glassBitmap.isRecycled()) glassBitmap.recycle();
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
