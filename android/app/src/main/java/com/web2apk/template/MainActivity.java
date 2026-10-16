package com.web2apk.template;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    static class Site { String name, icon, url; }

    private final List<Site> sites = new ArrayList<Site>();
    private final List<WebView> views = new ArrayList<WebView>();
    private final List<View> menuItems = new ArrayList<View>();
    private int current = -1;
    private int themeColor = Color.parseColor("#1976D2");

    private LinearLayout titleBar, drawer, drawerList;
    private View menuBtn, scrim, drawerHeader;
    private ImageView bgImage;
    private VideoView bgVideo;
    private TextView titleText, drawerTitle;
    private ProgressBar progress;
    private FrameLayout container;

    private boolean drawerOpen = false;
    private float downX, downY;
    private boolean edge = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        titleBar = (LinearLayout) findViewById(R.id.titleBar);
        menuBtn = findViewById(R.id.menuBtn);
        titleText = (TextView) findViewById(R.id.titleText);
        progress = (ProgressBar) findViewById(R.id.progress);
        container = (FrameLayout) findViewById(R.id.container);
        scrim = findViewById(R.id.scrim);
        drawer = (LinearLayout) findViewById(R.id.drawer);
        drawerList = (LinearLayout) findViewById(R.id.drawerList);
        drawerTitle = (TextView) findViewById(R.id.drawerTitle);
        drawerHeader = findViewById(R.id.drawerHeader);
        bgImage = (ImageView) findViewById(R.id.bgImage);
        bgVideo = (VideoView) findViewById(R.id.bgVideo);

        boolean showTitle = true;
        String appName = getString(R.string.app_name);
        String dTitle = null, bgType = "color", bgSrc = "";
        try {
            JSONObject cfg = new JSONObject(readAsset("config.json"));
            appName = cfg.optString("appName", appName);
            themeColor = Color.parseColor(cfg.optString("themeColor", "#1976D2"));
            showTitle = cfg.optBoolean("showTitleBar", true);
            JSONObject d = cfg.optJSONObject("drawer");
            if (d != null) {
                dTitle = d.optString("title", null);
                bgType = d.optString("bgType", "color");
                bgSrc = d.optString("bgSrc", "");
            }
            JSONArray arr = cfg.getJSONArray("sites");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Site s = new Site();
                s.url = o.getString("url");
                s.name = o.optString("name", s.url);
                s.icon = o.optString("icon", "");
                sites.add(s);
            }
        } catch (Exception e) {
            Toast.makeText(this, "config.json error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (sites.isEmpty()) { finish(); return; }

        boolean multi = sites.size() > 1;

        titleBar.setBackgroundColor(themeColor);
        drawerHeader.setBackgroundColor(themeColor);
        drawerTitle.setText(dTitle != null && dTitle.length() > 0 ? dTitle : appName);
        titleText.setText(appName);
        getWindow().setStatusBarColor(themeColor);

        // Banyak URL: ada tombol garis tiga + drawer. Cuma 1 URL: tanpa drawer.
        titleBar.setVisibility((showTitle || multi) ? View.VISIBLE : View.GONE);
        if (multi) {
            menuBtn.setVisibility(View.VISIBLE);
            menuBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openDrawer();
                }
            });
            scrim.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawer();
                }
            });
            drawer.setTranslationX(-dp(280));
            if ("image".equals(bgType) && bgSrc.length() > 0) {
                bgImage.setVisibility(View.VISIBLE);
                loadIcon(bgSrc, bgImage);
            } else if ("video".equals(bgType) && bgSrc.length() > 0) {
                setupVideo(bgSrc);
            }
            for (int i = 0; i < sites.size(); i++) buildMenuItem(i);
        } else {
            menuBtn.setVisibility(View.GONE);
            titleText.setPadding(dp(16), 0, dp(16), 0);
            drawer.setVisibility(View.GONE);
        }

        for (int i = 0; i < sites.size(); i++) views.add(null);
        select(0);
    }

    private void buildMenuItem(final int idx) {
        Site s = sites.get(idx);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(16), 0, dp(16), 0);

        ImageView iv = new ImageView(this);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(28), dp(28));
        ip.rightMargin = dp(16);
        iv.setLayoutParams(ip);
        loadIcon(s.icon, iv);
        item.addView(iv);

        TextView tv = new TextView(this);
        tv.setText(s.name);
        tv.setTextSize(16);
        tv.setTextColor(Color.parseColor("#222222"));
        tv.setSingleLine(true);
        item.addView(tv);

        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                select(idx);
                closeDrawer();
            }
        });
        drawerList.addView(item, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        menuItems.add(item);
    }

    private void select(int idx) {
        if (idx == current) return;
        if (current >= 0 && views.get(current) != null) views.get(current).setVisibility(View.GONE);
        current = idx;

        WebView w = views.get(idx);
        if (w == null) {
            w = createWebView(idx);
            views.set(idx, w);
            container.addView(w, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        w.setVisibility(View.VISIBLE);

        if (sites.size() > 1) {
            titleText.setText(sites.get(idx).name);
            int tint = Color.argb(40, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor));
            for (int i = 0; i < menuItems.size(); i++) {
                menuItems.get(i).setBackgroundColor(i == idx ? tint : Color.TRANSPARENT);
            }
        }
        progress.setVisibility(View.GONE);
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        scrim.setAlpha(0f);
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(200);
        drawer.animate().translationX(0).setDuration(200);
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawer.animate().translationX(-dp(280)).setDuration(200);
        scrim.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!drawerOpen) scrim.setVisibility(View.GONE);
            }
        });
    }

    // Geser dari tepi kiri layar untuk buka drawer
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (sites.size() > 1) {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                edge = downX < dp(24) && !drawerOpen;
            } else if (a == MotionEvent.ACTION_UP) {
                if (edge && e.getX() - downX > dp(60) && Math.abs(e.getY() - downY) < dp(80)) {
                    openDrawer();
                }
                edge = false;
            }
        }
        return super.dispatchTouchEvent(e);
    }

    private WebView createWebView(final int idx) {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("http://") || u.startsWith("https://")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception ignored) {}
                return true;
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (idx != current) return;
                progress.getProgressDrawable().setTint(themeColor);
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        w.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {}
            }
        });
        w.loadUrl(sites.get(idx).url);
        return w;
    }

    private void setupVideo(String src) {
        try {
            Uri uri;
            if (src.startsWith("http")) {
                uri = Uri.parse(src);
            } else {
                File f = new File(getCacheDir(), "drawer_bg.mp4");
                InputStream in = getAssets().open(src);
                FileOutputStream out = new FileOutputStream(f);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                uri = Uri.fromFile(f);
            }
            bgVideo.setVisibility(View.VISIBLE);
            bgVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                    mp.setVolume(0f, 0f);
                }
            });
            bgVideo.setVideoURI(uri);
            bgVideo.start();
        } catch (Exception e) {
            bgVideo.setVisibility(View.GONE);
        }
    }

    private void loadIcon(final String src, final ImageView iv) {
        if (src == null || src.isEmpty()) { iv.setVisibility(View.GONE); return; }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    InputStream in = src.startsWith("http")
                            ? new URL(src).openStream() : getAssets().open(src);
                    bmp = BitmapFactory.decodeStream(in);
                    in.close();
                } catch (Exception ignored) {}
                final Bitmap f = bmp;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (f != null) iv.setImageBitmap(f);
                        else iv.setVisibility(View.GONE);
                    }
                });
            }
        }).start();
    }

    private String readAsset(String name) throws Exception {
        InputStream in = getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) { closeDrawer(); return; }
        WebView w = current >= 0 ? views.get(current) : null;
        if (w != null && w.canGoBack()) w.goBack(); else super.onBackPressed();
    }
}
