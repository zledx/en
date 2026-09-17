package com.enabcd.navigation;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://enabcd.cn/";
    private static final String HOT_URL = "https://enabcd.cn/hot.php";
    private static final String MAGIC_URL = "https://enabcd.cn/magic.php";

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout root;
    private LinearLayout bottomNav;

    private final Map<String, LinearLayout> navItems = new HashMap<>();
    private final Map<String, TextView> navIcons = new HashMap<>();
    private final Map<String, TextView> navLabels = new HashMap<>();

    private boolean darkMode;
    private boolean showingAbout = false;
    private boolean settingsSelected = false;
    private boolean pendingOpenSettings = false;
    private String footerText = "";

    private int pageBg;
    private int navBg;
    private int textColor;
    private int mutedColor;
    private final int accentColor = Color.rgb(3, 103, 253);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        darkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        pageBg = darkMode ? Color.rgb(17, 19, 24) : Color.rgb(247, 248, 251);
        navBg = darkMode ? Color.rgb(30, 33, 40) : Color.WHITE;
        textColor = darkMode ? Color.rgb(239, 241, 245) : Color.rgb(32, 33, 36);
        mutedColor = darkMode ? Color.rgb(153, 160, 173) : Color.rgb(119, 124, 135);

        configureSystemBars();
        buildShell();
        configureWebView();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
        setActiveNav("home");
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(pageBg);
        window.setNavigationBarColor(pageBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility();
            if (!darkMode) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!darkMode) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(pageBg);

        webView = new WebView(this);
        webView.setBackgroundColor(pageBg);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        webParams.topMargin = dp(10); // Keep the first page component away from the Android status bar.
        webParams.bottomMargin = dp(84);
        root.addView(webView, webParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accentColor));
        progressBar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.topMargin = dp(10);
        root.addView(progressBar, progressParams);

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(6), dp(6), dp(6), dp(6));
        bottomNav.setBackground(roundRect(navBg, dp(22), darkMode ? Color.rgb(52, 56, 67) : Color.rgb(231, 234, 240), 1));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) bottomNav.setElevation(dp(12));

        addNavItem("home", "⌂", "首页", () -> navigateTo("home", HOME_URL));
        addNavItem("hot", "♨", "热榜", () -> navigateTo("hot", HOT_URL));
        addNavItem("magic", "◇", "魔盒", () -> navigateTo("magic", MAGIC_URL));
        addNavItem("about", "ⓘ", "关于", this::showAboutPage);
        addNavItem("settings", "⚙", "设置", this::openSettingsTab);

        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(66),
                Gravity.BOTTOM
        );
        navParams.setMargins(dp(12), 0, dp(12), dp(10));
        root.addView(bottomNav, navParams);
        setContentView(root);
    }

    private void addNavItem(String key, String icon, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(3), dp(4), dp(3), dp(4));
        item.setClickable(true);
        item.setFocusable(true);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        iconView.setTextColor(mutedColor);
        iconView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        labelView.setTextColor(mutedColor);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        item.addView(iconView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(27)));
        item.addView(labelView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
        item.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        bottomNav.addView(item, params);

        navItems.put(key, item);
        navIcons.put(key, iconView);
        navLabels.put(key, labelView);
    }

    private void setActiveNav(String key) {
        for (String itemKey : navItems.keySet()) {
            boolean active = itemKey.equals(key);
            LinearLayout item = navItems.get(itemKey);
            TextView icon = navIcons.get(itemKey);
            TextView label = navLabels.get(itemKey);
            if (item == null || icon == null || label == null) continue;
            item.setBackground(active ? roundRect(accentColor, dp(15), Color.TRANSPARENT, 0) : null);
            icon.setTextColor(active ? Color.WHITE : mutedColor);
            label.setTextColor(active ? Color.WHITE : mutedColor);
        }
    }

    private GradientDrawable roundRect(int fillColor, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private void navigateTo(String key, String url) {
        showingAbout = false;
        settingsSelected = false;
        pendingOpenSettings = false;
        setActiveNav(key);
        webView.loadUrl(url);
    }

    private void openSettingsTab() {
        showingAbout = false;
        settingsSelected = true;
        setActiveNav("settings");
        String current = webView.getUrl();
        if (isHomeUrl(current)) {
            pendingOpenSettings = false;
            triggerSiteSettings();
        } else {
            pendingOpenSettings = true;
            webView.loadUrl(HOME_URL);
        }
    }

    private void showAboutPage() {
        settingsSelected = false;
        pendingOpenSettings = false;
        showingAbout = true;
        setActiveNav("about");
        webView.loadDataWithBaseURL(HOME_URL, buildAboutHtml(), "text/html", "UTF-8", null);
    }

    private String buildAboutHtml() {
        String bg = darkMode ? "#111318" : "#f7f8fb";
        String card = darkMode ? "#1e2128" : "#ffffff";
        String text = darkMode ? "#eff1f5" : "#202124";
        String muted = darkMode ? "#99a0ad" : "#777c87";
        String border = darkMode ? "#343843" : "#e7eaf0";
        String info = footerText == null || footerText.trim().isEmpty()
                ? "优质资源极简主义导航\n网站：enabcd.cn\n© 2026 en导航"
                : footerText.trim();
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'>" +
                "<style>*{box-sizing:border-box}body{margin:0;background:" + bg + ";color:" + text + ";font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC',sans-serif;padding:14px 16px 28px}.hero{padding:26px 20px 18px;text-align:center}.logo{width:72px;height:72px;border-radius:20px;object-fit:cover;box-shadow:0 10px 28px rgba(0,0,0,.14)}h1{font-size:24px;margin:13px 0 5px}.sub{font-size:13px;color:" + muted + ";line-height:1.7}.card{background:" + card + ";border:1px solid " + border + ";border-radius:20px;padding:18px;margin-top:12px}.label{font-size:11px;color:#0367fd;font-weight:800;letter-spacing:.08em;margin-bottom:9px}.info{white-space:pre-line;font-size:13px;line-height:1.9;color:" + muted + "}.links{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:12px}.links a{display:flex;align-items:center;justify-content:center;min-height:48px;text-decoration:none;color:" + text + ";background:" + card + ";border:1px solid " + border + ";border-radius:15px;font-size:13px;font-weight:700}.links a.primary{background:#0367fd;color:#fff;border-color:#0367fd;grid-column:1/-1}.domain{text-align:center;color:" + muted + ";font-size:11px;padding:18px 0}</style></head><body>" +
                "<div class='hero'><img class='logo' src='https://enabcd.cn/favicon.ico'><h1>en导航</h1><div class='sub'>把网站底部信息集中到 APP 的「关于」页面</div></div>" +
                "<div class='card'><div class='label'>网站信息</div><div class='info'>" + htmlEscape(info) + "</div></div>" +
                "<div class='links'><a href='https://enabcd.cn/privacy.php'>隐私政策</a><a href='https://enabcd.cn/disclaimer.php'>免责声明</a><a href='https://enabcd.cn/contact.php'>联系我们</a><a href='https://enabcd.cn/'>网站首页</a><a class='primary' href='https://enabcd.cn/'>返回首页</a></div>" +
                "<div class='domain'>enabcd.cn · en导航 Android</div></body></html>";
    }

    private String htmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
        settings.setUserAgentString(settings.getUserAgentString() + " ENNavigationApp/1.1 Android");

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

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
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (showingAbout) return;
                injectAppUi();
                captureFooterInfo();
                if (pendingOpenSettings && isHomeUrl(url)) {
                    pendingOpenSettings = false;
                    webView.postDelayed(MainActivity.this::triggerSiteSettings, 260);
                }
                updateNavForUrl(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) showOfflinePage();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

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
                bottomNav.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                webView.setVisibility(View.VISIBLE);
                bottomNav.setVisibility(View.VISIBLE);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                customViewCallback = null;
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
                    request.setTitle("en导航下载");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "en_download_" + System.currentTimeMillis());
                    }
                    DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    manager.enqueue(request);
                    Toast.makeText(MainActivity.this, "已开始下载", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    openExternal(Uri.parse(url));
                }
            }
        });
    }

    private void injectAppUi() {
        String js = "(function(){" +
                "document.documentElement.classList.add('en-native-app');" +
                "var s=document.getElementById('en-native-app-style');if(!s){s=document.createElement('style');s.id='en-native-app-style';s.textContent='" +
                ":root{--topbar-height:0px!important;--header-height:0px!important;}" +
                ".site-topbar,.site-topbar-wrap,.site-header,.topbar-wrapper{display:none!important;height:0!important;min-height:0!important;}" +
                ".site-footer,.footer-shell,footer{display:none!important;}" +
                "#settingsBtn,#settingBtn,[data-open-settings],[data-settings],.search-settings-btn,.settings-trigger,.setting-trigger{display:none!important;}" +
                "html,body{margin-top:0!important;padding-top:0!important;}" +
                "body{padding-bottom:12px!important;}" +
                "main,.main-shell,.home-shell,.page-shell,.content-shell{margin-top:0!important;}" +
                ".search-card,.search-shell,.hero-search,.home-hero{margin-top:10px!important;}" +
                "';document.head.appendChild(s);}" +
                "document.querySelectorAll('button,a').forEach(function(el){var t=((el.innerText||'')+' '+(el.getAttribute('aria-label')||'')+' '+(el.title||'')).trim();if(t.indexOf('设置')>=0&&el.closest('.search-card,.search-shell,.search-box,.search-wrap,.hero-search'))el.style.setProperty('display','none','important');});" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void captureFooterInfo() {
        String js = "(function(){var f=document.querySelector('.site-footer,.footer-shell,footer');return f?(f.innerText||'').trim():'';})()";
        webView.evaluateJavascript(js, value -> {
            if (value == null || value.equals("null") || value.equals("\"\"")) return;
            try {
                String decoded = new JSONArray("[" + value + "]").getString(0);
                if (decoded != null && !decoded.trim().isEmpty()) footerText = decoded.trim();
            } catch (Exception ignored) {
            }
        });
    }

    private void triggerSiteSettings() {
        settingsSelected = true;
        setActiveNav("settings");
        String js = "(function(){var selectors=['#settingsBtn','#settingBtn','[data-open-settings]','[data-settings]','.search-settings-btn','.settings-trigger','.setting-trigger'];for(var i=0;i<selectors.length;i++){var e=document.querySelector(selectors[i]);if(e){e.click();return 'opened';}}var all=document.querySelectorAll('button,a');for(var j=0;j<all.length;j++){var t=((all[j].innerText||'')+' '+(all[j].getAttribute('aria-label')||'')+' '+(all[j].title||'')).trim();if(t.indexOf('设置')>=0){all[j].click();return 'opened';}}return 'missing';})()";
        webView.evaluateJavascript(js, value -> {
            if (value != null && value.contains("missing")) {
                Toast.makeText(MainActivity.this, "当前页面没有找到设置入口", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateNavForUrl(String url) {
        if (showingAbout) {
            setActiveNav("about");
            return;
        }
        if (settingsSelected) {
            setActiveNav("settings");
            return;
        }
        if (url == null) return;
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.endsWith("/hot.php") || path.endsWith("/hot.html")) setActiveNav("hot");
            else if (path.endsWith("/magic.php") || path.endsWith("/magic.html")) setActiveNav("magic");
            else setActiveNav("home");
        } catch (Exception ignored) {
            setActiveNav("home");
        }
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (host != null && (host.equals("enabcd.cn") || host.endsWith(".enabcd.cn"))) {
                showingAbout = false;
                settingsSelected = false;
                pendingOpenSettings = false;
                return false;
            }
            openExternal(uri);
            return true;
        }
        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOfflinePage() {
        showingAbout = false;
        settingsSelected = false;
        setActiveNav("home");
        String bg = darkMode ? "#111318" : "#f7f8fb";
        String fg = darkMode ? "#eff1f5" : "#202124";
        String html = "<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0;background:" + bg + ";font-family:sans-serif;display:grid;place-items:center;min-height:100vh;color:" + fg + "'><div style='text-align:center;padding:32px'><div style='font-size:42px'>!</div><h2>网络连接失败</h2><p style='color:#888'>请检查网络后重新加载</p><button onclick=\"location.href='" + HOME_URL + "'\" style='border:0;border-radius:12px;background:#0367fd;color:#fff;padding:12px 22px;font-size:15px'>重新加载</button></div></body></html>";
        webView.loadDataWithBaseURL(HOME_URL, html, "text/html", "UTF-8", null);
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
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
        } else if (showingAbout) {
            navigateTo("home", HOME_URL);
        } else if (settingsSelected) {
            settingsSelected = false;
            setActiveNav("home");
            webView.evaluateJavascript("(function(){var x=document.querySelector('.settings-close,[data-close-settings],.dialog-close,.modal-close');if(x)x.click();document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));})()", null);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
