package com.enabcd.navigation;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://enabcd.cn/";
    private static final String HOT_URL = "https://enabcd.cn/hot.php";
    private static final String MAGIC_URL = "https://enabcd.cn/magic.php";

    private static final int PAGE_BG = Color.rgb(247, 248, 251);
    private static final int CARD_BG = Color.WHITE;
    private static final int TEXT = Color.rgb(31, 35, 41);
    private static final int MUTED = Color.rgb(119, 126, 139);
    private static final int LINE = Color.rgb(232, 236, 242);
    private static final int ACCENT = Color.rgb(3, 103, 253);
    private static final int ACCENT_SOFT = Color.rgb(237, 244, 255);

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout bottomNav;
    private FrameLayout navShell;
    private FrameLayout settingsOverlay;
    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private final Map<String, LinearLayout> navItems = new HashMap<>();
    private final Map<String, ImageView> navIcons = new HashMap<>();
    private final Map<String, TextView> navLabels = new HashMap<>();

    private SharedPreferences prefs;
    private boolean showingAbout = false;
    private int statusInset = 0;
    private int navInset = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("en_navigation_app", MODE_PRIVATE);
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
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(PAGE_BG);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(PAGE_BG);

        webView = new WebView(this);
        webView.setBackgroundColor(PAGE_BG);
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progressBar.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        root.addView(progressBar, progressParams);

        navShell = new FrameLayout(this);
        navShell.setBackground(roundRect(CARD_BG, dp(27), LINE, 1));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) navShell.setElevation(dp(18));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(6), dp(6), dp(6), dp(6));
        navShell.addView(bottomNav, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addNavItem("home", R.drawable.ic_home, "首页", () -> navigateTo("home", HOME_URL));
        addNavItem("hot", R.drawable.ic_hot, "热榜", () -> navigateTo("hot", HOT_URL));
        addNavItem("magic", R.drawable.ic_magic, "魔盒", () -> navigateTo("magic", MAGIC_URL));
        addNavItem("about", R.drawable.ic_about, "关于", this::showAboutPage);
        addNavItem("settings", R.drawable.ic_settings, "设置", this::showSettingsSheet);

        root.addView(navShell, createNavLayoutParams());
        setContentView(root);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            statusInset = Math.max(0, insets.getSystemWindowInsetTop());
            navInset = Math.max(0, insets.getSystemWindowInsetBottom());
            FrameLayout.LayoutParams navLp = createNavLayoutParams();
            navShell.setLayoutParams(navLp);
            FrameLayout.LayoutParams progressLp = (FrameLayout.LayoutParams) progressBar.getLayoutParams();
            progressLp.topMargin = statusInset;
            progressBar.setLayoutParams(progressLp);
            injectAppUi();
            return insets;
        });
    }

    private FrameLayout.LayoutParams createNavLayoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70), Gravity.BOTTOM);
        lp.setMargins(dp(14), 0, dp(14), dp(10) + navInset);
        return lp;
    }

    private void addNavItem(String key, int iconRes, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(4), dp(4), dp(4));
        item.setClickable(true);
        item.setFocusable(true);

        FrameLayout iconBadge = new FrameLayout(this);
        iconBadge.setTag("iconBadge");
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(36), dp(34));
        badgeLp.gravity = Gravity.CENTER_HORIZONTAL;
        item.addView(iconBadge, badgeLp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImageTintList(ColorStateList.valueOf(MUTED));
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        iconBadge.addView(icon, iconLp);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextColor(MUTED);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20));
        item.addView(labelView, labelLp);

        item.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(.92f).scaleY(.92f).setDuration(70).withEndAction(() ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()).start();
            action.run();
        });

        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        itemLp.setMargins(dp(1), 0, dp(1), 0);
        bottomNav.addView(item, itemLp);
        navItems.put(key, item);
        navIcons.put(key, icon);
        navLabels.put(key, labelView);
    }

    private void setActiveNav(String key) {
        for (Map.Entry<String, LinearLayout> e : navItems.entrySet()) {
            boolean active = e.getKey().equals(key);
            LinearLayout item = e.getValue();
            ImageView icon = navIcons.get(e.getKey());
            TextView label = navLabels.get(e.getKey());
            View badge = item.findViewWithTag("iconBadge");
            if (badge != null) badge.setBackground(active ? roundRect(ACCENT_SOFT, dp(13), Color.TRANSPARENT, 0) : null);
            if (icon != null) icon.setImageTintList(ColorStateList.valueOf(active ? ACCENT : MUTED));
            if (label != null) label.setTextColor(active ? ACCENT : MUTED);
        }
    }

    private void navigateTo(String key, String url) {
        dismissSettingsSheet(false);
        showingAbout = false;
        setActiveNav(key);
        webView.loadUrl(url);
    }

    private void showAboutPage() {
        dismissSettingsSheet(false);
        showingAbout = true;
        setActiveNav("about");
        webView.loadDataWithBaseURL(HOME_URL, buildAboutHtml(), "text/html", "UTF-8", null);
    }

    private String buildAboutHtml() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'>" +
                "<style>*{box-sizing:border-box}html{background:#f7f8fb;color-scheme:light}body{margin:0;background:#f7f8fb;color:#1f2329;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;padding:calc(var(--app-status-inset,24px) + 18px) 16px 118px}.head{padding:16px 5px 22px}.name{font-weight:800;font-size:28px;letter-spacing:-.02em}.desc{margin-top:7px;color:#7a818d;font-size:13px;line-height:1.7}.group{background:#fff;border:1px solid #e8ecf2;border-radius:22px;overflow:hidden;box-shadow:0 8px 28px rgba(35,48,70,.05)}a{height:58px;padding:0 18px;text-decoration:none;color:#20242b;display:flex;align-items:center;justify-content:space-between;font-size:14px;font-weight:700;border-bottom:1px solid #edf0f4}a:last-child{border-bottom:0}.arrow{color:#a3a9b2;font-size:21px;font-weight:400}</style></head><body>" +
                "<div class='head'><div class='name'>en导航</div><div class='desc'>优质资源极简主义导航</div></div>" +
                "<div class='group'><a href='https://enabcd.cn/privacy.php'><span>隐私政策</span><span class='arrow'>›</span></a>" +
                "<a href='https://enabcd.cn/disclaimer.php'><span>免责声明</span><span class='arrow'>›</span></a>" +
                "<a href='https://enabcd.cn/contact.php'><span>联系我们</span><span class='arrow'>›</span></a></div></body></html>";
    }

    private void showSettingsSheet() {
        if (settingsOverlay != null) return;
        setActiveNav("settings");

        settingsOverlay = new FrameLayout(this);
        settingsOverlay.setBackgroundColor(Color.argb(88, 12, 18, 28));
        settingsOverlay.setClickable(true);
        settingsOverlay.setOnClickListener(v -> dismissSettingsSheet(true));

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(10), dp(18), dp(18));
        sheet.setBackground(roundRect(CARD_BG, dp(28), LINE, 1));
        sheet.setClickable(true);
        sheet.setOnClickListener(v -> {});
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) sheet.setElevation(dp(24));

        View handle = new View(this);
        handle.setBackground(roundRect(Color.rgb(218, 223, 231), dp(3), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(40), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(15);
        sheet.addView(handle, handleLp);

        TextView title = textView("设置", 23, TEXT, true);
        sheet.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        TextView sub = textView("仅对 Android APP 生效 · 固定浅色模式", 12, MUTED, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        subLp.bottomMargin = dp(8);
        sheet.addView(sub, subLp);

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setBackground(roundRect(Color.rgb(250, 251, 253), dp(20), LINE, 1));
        options.setPadding(dp(4), dp(2), dp(4), dp(2));
        sheet.addView(options, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSwitchRow(options, "快捷导航", "显示首页快捷分类导航", "show_quick_nav", true);
        addDivider(options);
        addSwitchRow(options, "公告栏", "显示站点公告与往期公告入口", "show_announcement", true);
        addDivider(options);
        addSwitchRow(options, "随机小姐姐", "显示随机视频入口", "show_random_video", true);

        TextView section = textView("页面", 11, MUTED, true);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        sectionLp.topMargin = dp(12);
        section.setGravity(Gravity.CENTER_VERTICAL);
        sheet.addView(section, sectionLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setBackground(roundRect(Color.rgb(250, 251, 253), dp(20), LINE, 1));
        actions.setPadding(dp(4), dp(2), dp(4), dp(2));
        sheet.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addActionRow(actions, "重新加载当前页面", "刷新内容并重新应用 APP 适配", () -> {
            dismissSettingsSheet(true);
            webView.reload();
        });
        addDivider(actions);
        addActionRow(actions, "清除页面缓存", "清理 WebView 缓存后重新加载", () -> {
            webView.clearCache(true);
            CookieManager.getInstance().flush();
            dismissSettingsSheet(true);
            webView.reload();
            Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show();
        });

        TextView version = textView("en导航 Android · v1.2.0", 10.5f, Color.rgb(160, 166, 176), false);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        versionLp.topMargin = dp(8);
        sheet.addView(version, versionLp);

        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        sheetLp.setMargins(dp(12), 0, dp(12), dp(92) + navInset);
        settingsOverlay.addView(sheet, sheetLp);
        root.addView(settingsOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        settingsOverlay.setAlpha(0f);
        sheet.setTranslationY(dp(30));
        settingsOverlay.animate().alpha(1f).setDuration(170).start();
        sheet.animate().translationY(0f).setDuration(220).start();
    }

    private void addSwitchRow(LinearLayout parent, String title, String desc, String prefKey, boolean defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = textView(title, 14, TEXT, true);
        TextView d = textView(desc, 11, MUTED, false);
        texts.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(23)));
        texts.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        row.addView(texts, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(prefKey, defaultValue));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sw.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{ACCENT, Color.WHITE}));
            sw.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{Color.rgb(153, 193, 255), Color.rgb(215, 220, 228)}));
        }
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(prefKey, isChecked).apply();
            applyFeaturePrefs();
            buttonView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        });
        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        row.addView(sw, new LinearLayout.LayoutParams(dp(55), dp(44)));
        parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
    }

    private void addActionRow(LinearLayout parent, String title, String desc, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setClickable(true);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(textView(title, 14, TEXT, true), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(23)));
        texts.addView(textView(desc, 11, MUTED, false), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        row.addView(texts, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView arrow = textView("›", 22, Color.rgb(161, 168, 178), false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(26), dp(42)));
        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            action.run();
        });
        parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
    }

    private void addDivider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(235, 238, 243));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(12), 0, dp(12), 0);
        parent.addView(line, lp);
    }

    private TextView textView(String text, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private void dismissSettingsSheet(boolean restoreNav) {
        if (settingsOverlay == null) return;
        FrameLayout overlay = settingsOverlay;
        settingsOverlay = null;
        overlay.animate().alpha(0f).setDuration(130).withEndAction(() -> root.removeView(overlay)).start();
        if (restoreNav) updateNavForUrl(webView.getUrl());
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
        settings.setUserAgentString(settings.getUserAgentString() + " ENNavigationApp/1.2 Android LightOnly");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) settings.setForceDark(WebSettings.FORCE_DARK_OFF);

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

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
                injectAppUi();
                if (!showingAbout) updateNavForUrl(url);
                webView.postDelayed(MainActivity.this::injectAppUi, 350);
                webView.postDelayed(MainActivity.this::injectAppUi, 1100);
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

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (host != null && (host.equals("enabcd.cn") || host.equals("www.enabcd.cn") || host.endsWith(".enabcd.cn"))) {
                showingAbout = false;
                return false;
            }
            openExternal(uri);
            return true;
        }
        openExternal(uri);
        return true;
    }

    private void injectAppUi() {
        if (webView == null) return;
        boolean showQuick = prefs.getBoolean("show_quick_nav", true);
        boolean showAnnouncement = prefs.getBoolean("show_announcement", true);
        boolean showRandom = prefs.getBoolean("show_random_video", true);
        String js = "(function(){try{" +
                "var d=document.documentElement,b=document.body;if(!d||!b)return;" +
                "d.classList.remove('dark','theme-dark');d.classList.add('light','en-app-webview');d.setAttribute('data-theme','light');d.style.colorScheme='light';" +
                "try{localStorage.setItem('theme','light');localStorage.setItem('en_theme','light');localStorage.setItem('en-theme','light');}catch(e){}" +
                "d.classList.toggle('en-quicknav-off'," + (!showQuick) + ");" +
                "d.classList.toggle('en-announcement-off'," + (!showAnnouncement) + ");" +
                "d.classList.toggle('en-random-video-off'," + (!showRandom) + ");" +
                "d.style.setProperty('--app-status-inset','" + statusInset + "px');d.style.setProperty('--app-nav-inset','" + navInset + "px');" +
                "var s=document.getElementById('en-android-app-style');if(!s){s=document.createElement('style');s.id='en-android-app-style';document.head.appendChild(s);}s.textContent=`" +
                "html,body{color-scheme:light!important;background:#f7f8fb!important}body{padding-top:calc(var(--app-status-inset,0px) + 4px)!important;padding-bottom:calc(106px + var(--app-nav-inset,0px))!important}" +
                ".site-topbar,.site-topbar-spacer,.site-mobile-nav-trigger,.topbar-placeholder{display:none!important}" +
                ".site-footer,.footer-wrap,footer.site-footer{display:none!important}" +
                ".theme-switch,.theme-toggle,[data-theme-toggle],#themeSwitch,.appearance-switch{display:none!important}" +
                "#settingsBtn,.search-settings-btn,[data-open-settings],.search-setting-trigger{display:none!important}" +
                ".en-app-webview.en-quicknav-off .quick-nav,.en-app-webview.en-quicknav-off .quick-nav-sidebar,.en-app-webview.en-quicknav-off [data-quick-nav]{display:none!important}" +
                ".en-app-webview.en-announcement-off .announcement-card,.en-app-webview.en-announcement-off .announcement-wrap,.en-app-webview.en-announcement-off [data-announcement]{display:none!important}" +
                ".en-app-webview.en-random-video-off .random-video,.en-app-webview.en-random-video-off .random-girl,.en-app-webview.en-random-video-off [data-random-video]{display:none!important}" +
                "@media(max-width:760px){body{padding-left:0!important;padding-right:0!important}}`;}catch(e){}})();";
        webView.evaluateJavascript(js, null);
    }

    private void applyFeaturePrefs() {
        injectAppUi();
        webView.postDelayed(this::injectAppUi, 250);
    }

    private void updateNavForUrl(String url) {
        if (settingsOverlay != null) return;
        if (showingAbout) {
            setActiveNav("about");
            return;
        }
        if (url == null) {
            setActiveNav("home");
            return;
        }
        String lower = url.toLowerCase();
        if (lower.contains("hot.php")) setActiveNav("hot");
        else if (lower.contains("magic.php")) setActiveNav("magic");
        else if (lower.contains("privacy.php") || lower.contains("disclaimer.php") || lower.contains("contact.php")) setActiveNav("about");
        else setActiveNav("home");
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
        String html = "<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0;background:#f7f8fb;font-family:sans-serif;display:grid;place-items:center;min-height:100vh;color:#202124'><div style='text-align:center;padding:32px'><div style='font-size:44px'>⌁</div><h2 style='margin-bottom:8px'>网络连接失败</h2><p style='color:#777;font-size:13px'>请检查网络后重新加载</p><button onclick=\"location.href='" + HOME_URL + "'\" style='border:0;border-radius:14px;background:#0367fd;color:#fff;padding:12px 24px;font-size:14px;font-weight:700'>重新加载</button></div></body></html>";
        webView.loadDataWithBaseURL(HOME_URL, html, "text/html", "UTF-8", null);
    }

    private GradientDrawable roundRect(int fillColor, int radiusPx, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(radiusPx);
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
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
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (settingsOverlay != null) {
            dismissSettingsSheet(true);
        } else if (customView != null) {
            ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
        } else if (showingAbout) {
            navigateTo("home", HOME_URL);
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
}
