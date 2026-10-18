package com.androidtvlite.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String[] PREFERRED = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.globo.globotv",
            "com.disney.disneyplus",
            "com.apple.atve.androidtv.appletv",
            "com.crunchyroll.crunchyroid"
    };
    private static final String PLAY_STORE = "com.android.vending";
    private static final String TV_SETTINGS = "com.android.tv.settings";
    private static final int COLUMNS = 4;

    private static class Entry {
        CharSequence label;
        Drawable image;
        boolean banner;
        Intent intent;
    }

    private LinearLayout grid;
    private ScrollView gridScroll;
    private final List<View> tabs = new ArrayList<View>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        bg.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.bg, opts));
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xA0000000, 0x30000000, 0x20000000, 0x80000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        // Grade de apps (centro)
        gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(false);
        gridScroll.setFillViewport(true);
        gridScroll.setClipChildren(false);
        gridScroll.setClipToPadding(false);
        gridScroll.setPadding(dp(24), dp(20), dp(24), dp(20));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER);
        grid.setClipChildren(false);
        gridScroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-1, -1);
        glp.topMargin = dp(80);
        root.addView(gridScroll, glp);

        // Barra superior: data | navegacao | horario
        FrameLayout top = new FrameLayout(this);
        top.setClipChildren(false);
        top.setClipToPadding(false);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        tlp.setMargins(dp(40), dp(22), dp(40), 0);
        root.addView(top, tlp);

        // Data e hora seguem o idioma e o formato (12h/24h) configurados na TV.
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        String datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM");
        String timePattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm";

        TextClock date = new TextClock(this);
        date.setFormat24Hour(datePattern);
        date.setFormat12Hour(datePattern);
        date.setTextColor(0xE6FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date.setShadowLayer(dp(6), 0, dp(2), 0x80000000);
        top.addView(date, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour(timePattern);
        clock.setFormat12Hour(timePattern);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setShadowLayer(dp(6), 0, dp(2), 0x80000000);
        top.addView(clock, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(5), dp(5), dp(5), dp(5));
        nav.setClipChildren(false);
        nav.setClipToPadding(false);
        GradientDrawable navBg = new GradientDrawable();
        navBg.setCornerRadius(dp(40));
        navBg.setColor(0x66000000);
        nav.setBackground(navBg);
        nav.addView(makeTab(getString(R.string.nav_home), true, new Runnable() {
            @Override
            public void run() {
                focusFirstTile();
            }
        }));
        nav.addView(makeTab("Google Play", false, new Runnable() {
            @Override
            public void run() {
                openPlayStore();
            }
        }));
        nav.addView(makeTab(getString(R.string.nav_settings), false, new Runnable() {
            @Override
            public void run() {
                openSettings();
            }
        }));
        top.addView(nav, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        populate();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        focusFirstTile();
    }

    @Override
    public void onBackPressed() {
        // Tela inicial: o botao Voltar nao faz nada.
    }

    private void focusFirstTile() {
        gridScroll.smoothScrollTo(0, 0);
        if (grid.getChildCount() > 0) {
            View firstRow = grid.getChildAt(0);
            if (firstRow instanceof LinearLayout && ((LinearLayout) firstRow).getChildCount() > 0) {
                ((LinearLayout) firstRow).getChildAt(0).requestFocus();
            }
        }
    }

    private void launch(Intent i) {
        try {
            startActivity(i);
        } catch (Exception ex) {
            // app removido ou sem atividade: ignora
        }
    }

    private void openPlayStore() {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(PLAY_STORE);
        if (i == null) i = pm.getLaunchIntentForPackage(PLAY_STORE);
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launch(i);
        }
    }

    private void openSettings() {
        launch(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void populate() {
        PackageManager pm = getPackageManager();
        List<Entry> entries = new ArrayList<Entry>();

        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.equals(PLAY_STORE) || pkg.equals(TV_SETTINGS)) continue;

            int order = 100;
            for (int i = 0; i < PREFERRED.length; i++) {
                if (PREFERRED[i].equals(pkg)) order = i;
            }
            boolean system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            // So streaming: apps conhecidos + qualquer app instalado pelo usuario.
            if (order == 100 && system) continue;

            Entry e = new Entry();
            e.label = ri.loadLabel(pm);
            Drawable d = ri.activityInfo.loadBanner(pm);
            e.banner = d != null;
            e.image = d != null ? d : ri.loadIcon(pm);
            e.intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            entries.add(e);
        }

        final Collator collator = Collator.getInstance(new Locale("pt", "BR"));
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return collator.compare(a.label.toString(), b.label.toString());
            }
        });

        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < entries.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
                rlp.setMargins(0, dp(6), 0, dp(6));
                grid.addView(row, rlp);
            }
            row.addView(makeTile(entries.get(i)));
        }
        wireNavigation();
        focusFirstTile();
    }

    /** Liga a primeira linha da grade as abas da barra (cima/baixo), sem depender da geometria. */
    private void wireNavigation() {
        if (grid.getChildCount() == 0 || tabs.isEmpty()) return;
        LinearLayout first = (LinearLayout) grid.getChildAt(0);
        int n = first.getChildCount();
        if (n == 0) return;
        int[] upTab = {0, 1, 2, 2};
        for (int c = 0; c < n; c++) {
            View tile = first.getChildAt(c);
            tile.setId(View.generateViewId());
            tile.setNextFocusUpId(tabs.get(Math.min(upTab[Math.min(c, 3)], tabs.size() - 1)).getId());
        }
        for (int t = 0; t < tabs.size(); t++) {
            tabs.get(t).setNextFocusDownId(first.getChildAt(Math.min(t, n - 1)).getId());
        }
    }

    private TextView makeTab(String text, boolean selected, final Runnable action) {
        final TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(7), dp(18), dp(7));
        t.setFocusable(true);
        t.setClickable(true);
        t.setId(View.generateViewId());
        tabs.add(t);

        final GradientDrawable on = new GradientDrawable();
        on.setCornerRadius(dp(40));
        on.setColor(0xFFF2F2F2);
        final GradientDrawable sel = new GradientDrawable();
        sel.setCornerRadius(dp(40));
        sel.setColor(0x33FFFFFF);
        final Drawable idle = selected ? sel : null;
        t.setBackground(idle);
        t.setTextColor(Color.WHITE);

        final DecelerateInterpolator ease = new DecelerateInterpolator();
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                t.setBackground(hasFocus ? on : idle);
                t.setTextColor(hasFocus ? 0xFF111111 : Color.WHITE);
                float s = hasFocus ? 1.04f : 1f;
                v.animate().scaleX(s).scaleY(s).setInterpolator(ease).setDuration(140).start();
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(2), 0, dp(2), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private View makeTile(final Entry e) {
        final int radius = dp(12);
        final int lift = dp(5);
        final int cardW = dp(120);
        final int cardH = dp(68);

        final LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setFocusable(true);
        item.setClickable(true);
        item.setClipChildren(false);
        item.setAlpha(0.88f);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, -2);
        ilp.setMargins(dp(14), 0, dp(14), 0);
        item.setLayoutParams(ilp);

        final FrameLayout card = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(radius);
        cardBg.setColor(0x66202028);
        card.setBackground(cardBg);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        card.setElevation(dp(4));

        ImageView img = new ImageView(this);
        img.setImageDrawable(e.image);
        img.setScaleType(e.banner ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_INSIDE);
        if (!e.banner) img.setPadding(dp(20), dp(10), dp(20), dp(10));
        card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        item.addView(card, new LinearLayout.LayoutParams(cardW, cardH));

        final TextView label = new TextView(this);
        label.setText(e.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setShadowLayer(dp(4), 0, dp(1), 0xA0000000);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(cardW, -2);
        llp.topMargin = dp(10);
        item.addView(label, llp);

        final DecelerateInterpolator ease = new DecelerateInterpolator();
        item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.15f : 1f;
                v.animate().scaleX(s).scaleY(s).translationY(hasFocus ? -lift : 0)
                        .alpha(hasFocus ? 1f : 0.88f).setInterpolator(ease).setDuration(160).start();
                card.animate().translationZ(hasFocus ? dp(12) : 0).setDuration(160).start();
                label.animate().alpha(hasFocus ? 1f : 0f).setDuration(160).start();
            }
        });
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(e.intent);
            }
        });
        return item;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
