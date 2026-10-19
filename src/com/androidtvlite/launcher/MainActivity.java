package com.androidtvlite.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
    private static final String PREFS = "home";
    private static final String PREF_ORDER = "order";
    private static final String PREF_HIDDEN = "hidden";

    private static class Entry {
        String key;
        String pkg;
        boolean system;
        CharSequence label;
        CollationKey sortKey;
        ResolveInfo info;
        Drawable image;
        boolean banner;
        Intent intent;
    }

    private FrameLayout root;
    private LinearLayout grid;
    private ScrollView gridScroll;
    private final List<View> tabs = new ArrayList<View>();

    private SharedPreferences prefs;
    private Collator collator;
    private final Map<String, Entry> all = new LinkedHashMap<String, Entry>();
    private List<String> order = new ArrayList<String>();
    private final Set<String> hidden = new HashSet<String>();
    private final List<Entry> home = new ArrayList<Entry>();
    private final Map<String, View> tileViews = new LinkedHashMap<String, View>();

    // Modo de edicao
    private boolean editing;
    private String movingKey;
    private String menuKey;
    private View menu;
    private LinearLayout bottomBar;
    private TextView addBtn;
    private TextView doneBtn;
    private boolean dirty; // ordem alterada e ainda nao gravada (gravamos so ao soltar / sair)
    private String lastSig; // ordem da ultima grade desenhada, para pular reconstrucoes inuteis
    private boolean appsStale = true; // lista de apps do sistema mudou (ou ainda nao foi lida)
    private boolean cacheInvalid; // pacote instalado/atualizado/removido: nao reaproveitar Entry
    private boolean relayouting; // reposicionando views: os listeners de foco ficam quietos
    private final List<View> tmpTiles = new ArrayList<View>();
    private static final int[] UP_TAB = {0, 1, 2, 2};

    // Avisa quando um app e instalado, atualizado ou removido (evita consultar o sistema a toda volta).
    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            appsStale = true;
            cacheInvalid = true;
            lastSig = null;
        }
    };

    // Tremulacao: UM animador move todos os cards (menos objetos e callbacks por quadro).
    private ValueAnimator wobbler;
    private final List<View> wobbleCards = new ArrayList<View>();
    private float[] wobblePhase = new float[0];
    private final Random random = new Random();

    // Reaproveitados por todos os tiles/pilulas
    private final DecelerateInterpolator ease = new DecelerateInterpolator();
    private ViewOutlineProvider cardOutline;

    // Tela "Adicionar apps"
    private FrameLayout addOverlay;
    private LinearLayout addGrid;
    private int[] savedFocus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        collator = Collator.getInstance(new Locale("pt", "BR"));
        collator.setStrength(Collator.PRIMARY);
        final int cardRadius = dp(12);
        cardOutline = new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cardRadius);
            }
        };

        root = new FrameLayout(this);
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

        // Barra inferior (so no modo de edicao): Adicionar apps | Concluido
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(5), dp(5), dp(5), dp(5));
        bottomBar.setClipChildren(false);
        bottomBar.setClipToPadding(false);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setCornerRadius(dp(40));
        barBg.setColor(0x99000000);
        bottomBar.setBackground(barBg);
        addBtn = makePill(getString(R.string.edit_add_apps), false, new Runnable() {
            @Override
            public void run() {
                openAdd();
            }
        });
        doneBtn = makePill(getString(R.string.edit_done), false, new Runnable() {
            @Override
            public void run() {
                exitEditMode();
            }
        });
        bottomBar.addView(addBtn);
        bottomBar.addView(doneBtn);
        addBtn.setNextFocusLeftId(addBtn.getId());
        addBtn.setNextFocusRightId(doneBtn.getId());
        addBtn.setNextFocusDownId(addBtn.getId());
        doneBtn.setNextFocusLeftId(addBtn.getId());
        doneBtn.setNextFocusRightId(doneBtn.getId());
        doneBtn.setNextFocusDownId(doneBtn.getId());
        bottomBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = dp(24);
        root.addView(bottomBar, blp);

        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(pkgReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String keep = null;
        if (editing) {
            View f = getCurrentFocus();
            if (menu != null) keep = menuKey;
            else if (f != null && f.getTag() instanceof String) keep = (String) f.getTag();
        }
        closeMenu(true);
        boolean reloaded = appsStale;
        if (reloaded) {
            loadApps();
            appsStale = false;
            if (movingKey != null && !all.containsKey(movingKey)) movingKey = null;
        }
        if (HomeOrder.join(order).equals(lastSig)) {
            // Nada mudou na Home (caso comum ao voltar de um app): nao recria nenhuma view.
            if (editing) startWobble();
            else focusFirstTile();
        } else {
            renderGrid(keep);
        }
        if (reloaded && addOverlay != null) fillAddList(0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean wasMoving = movingKey != null;
        movingKey = null;
        saveIfDirty();
        closeMenu(false);
        if (addOverlay != null) closeAdd(false);
        if (editing) exitEditMode();
        // Recria a grade para desfazer o "levantado" do app que estava sendo movido.
        if (wasMoving) renderGrid(null);
        else focusFirstTile();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopWobble(); // onResume religa a tremulacao
        saveIfDirty();
    }

    @Override
    public void onBackPressed() {
        if (movingKey != null) {
            dropMoving();
        } else if (menu != null) {
            closeMenu(true);
        } else if (addOverlay != null) {
            closeAdd(true);
        } else if (editing) {
            exitEditMode();
        }
        // Tela inicial: fora da edicao, o botao Voltar nao faz nada.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (movingKey == null) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);
        // Levantando um app: as setas o trocam de lugar, OK solta. O resto e consumido.
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int dir = -1;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) dir = HomeOrder.LEFT;
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dir = HomeOrder.RIGHT;
            else if (code == KeyEvent.KEYCODE_DPAD_UP) dir = HomeOrder.UP;
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) dir = HomeOrder.DOWN;
            if (dir >= 0) {
                moveBy(dir);
            } else if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER) && event.getRepeatCount() == 0) {
                dropMoving();
            }
        }
        return true;
    }

    private void focusFirstTile() {
        gridScroll.smoothScrollTo(0, 0);
        if (grid.getChildCount() > 0) {
            View firstRow = grid.getChildAt(0);
            if (firstRow instanceof LinearLayout && ((LinearLayout) firstRow).getChildCount() > 0) {
                ((LinearLayout) firstRow).getChildAt(0).requestFocus();
                return;
            }
        }
        if (editing) addBtn.requestFocus();
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

    // ------------------------------------------------------------------ dados

    private void sortByLabel(List<Entry> list) {
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.sortKey.compareTo(b.sortKey);
            }
        });
    }

    /** Le os apps de TV instalados e monta a ordem da Home (salva + padroes + novos no fim). */
    private void loadApps() {
        PackageManager pm = getPackageManager();
        // Reaproveita as entradas ja criadas (label, chave de ordenacao e imagem carregada).
        Map<String, Entry> old = cacheInvalid ? new HashMap<String, Entry>() : new HashMap<String, Entry>(all);
        cacheInvalid = false;
        all.clear();
        List<Entry> defaults = new ArrayList<Entry>();

        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.equals(PLAY_STORE) || pkg.equals(TV_SETTINGS)) continue;

            boolean known = false;
            for (String p : PREFERRED) {
                if (p.equals(pkg)) known = true;
            }
            boolean system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

            ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            String key = cn.flattenToString();
            Entry e = old.get(key);
            if (e == null) {
                e = new Entry();
                e.key = key;
                e.pkg = pkg;
                e.system = system;
                e.info = ri;
                e.label = ri.loadLabel(pm);
                e.sortKey = collator.getCollationKey(e.label.toString());
                e.intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setComponent(cn)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            }
            all.put(e.key, e);
            // Padrao: so streaming conhecidos + qualquer app instalado pelo usuario.
            if (known || !system) defaults.add(e);
        }
        sortByLabel(defaults);

        List<String> defaultKeys = new ArrayList<String>();
        for (Entry e : defaults) defaultKeys.add(e.key);
        hidden.clear();
        hidden.addAll(prefs.getStringSet(PREF_HIDDEN, new HashSet<String>()));
        hidden.retainAll(all.keySet()); // app desinstalado e reinstalado volta como novo
        // Ordem ainda nao gravada (durante o Mover) vale mais que a das preferencias.
        List<String> saved = dirty ? order : HomeOrder.split(prefs.getString(PREF_ORDER, ""));
        order = HomeOrder.merge(saved, defaultKeys, new HashSet<String>(all.keySet()), hidden);
        rebuildHome();
        if (addOverlay == null) releaseUnusedImages();
    }

    private void rebuildHome() {
        home.clear();
        for (String k : order) {
            Entry e = all.get(k);
            if (e != null) home.add(e);
        }
    }

    private void save() {
        dirty = false;
        prefs.edit()
                .putString(PREF_ORDER, HomeOrder.join(order))
                .putStringSet(PREF_HIDDEN, new HashSet<String>(hidden))
                .apply();
    }

    private void saveIfDirty() {
        if (dirty) save();
    }

    private void ensureImage(Entry e) {
        if (e.image != null) return;
        PackageManager pm = getPackageManager();
        Drawable d = e.info.activityInfo.loadBanner(pm);
        e.banner = d != null;
        e.image = d != null ? d : e.info.loadIcon(pm);
    }

    // ------------------------------------------------------------------ grade

    /** Reconstroi a grade da Home. {@code focusKey} (opcional) e o app que deve receber o foco. */
    private void renderGrid(String focusKey) {
        stopWobble();
        tileViews.clear();
        grid.removeAllViews();
        buildRows(grid, home, true);
        wireNavigation();
        gridScroll.setPadding(dp(24), dp(20), dp(24), editing ? dp(110) : dp(20));
        bottomBar.setVisibility(editing ? View.VISIBLE : View.GONE);

        View target = focusKey != null ? tileViews.get(focusKey) : null;
        if (target != null) {
            target.requestFocus();
            if (focusKey.equals(movingKey)) liftTile(target);
        } else if (!editing) {
            focusFirstTile();
        } else if (getCurrentFocus() == null || !isInside(getCurrentFocus(), root)) {
            focusFirstTile();
        }
        if (editing) startWobble();
        lastSig = HomeOrder.join(order);
    }

    /**
     * Reposiciona os tiles ja criados conforme {@code home}, sem recriar nenhuma view
     * (usado ao mover, onde cada tecla de seta passa por aqui).
     */
    private void relayout() {
        View focused = grid.findFocus();
        tmpTiles.clear();
        for (int i = 0; i < home.size(); i++) tmpTiles.add(tileViews.get(home.get(i).key));
        reflow(grid, tmpTiles, focused);
        wireNavigation();
        tmpTiles.clear();
    }

    /**
     * Redistribui {@code tiles} (ja criados) nas linhas de {@code container}, sem recriar views.
     * Remover a view focada faria o foco pular para a barra e animar tudo; por isso o foco fica
     * bloqueado e os listeners quietos durante a troca, e {@code focusAfter} o recebe no fim.
     */
    private void reflow(LinearLayout container, List<View> tiles, View focusAfter) {
        View before = root.findFocus();
        relayouting = true;
        int prev = root.getDescendantFocusability();
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        for (int r = 0; r < container.getChildCount(); r++) {
            ((LinearLayout) container.getChildAt(r)).removeAllViews();
        }
        int rows = (tiles.size() + COLUMNS - 1) / COLUMNS;
        while (container.getChildCount() > rows) container.removeViewAt(container.getChildCount() - 1);
        for (int i = 0; i < tiles.size(); i++) {
            ((LinearLayout) container.getChildAt(i / COLUMNS)).addView(tiles.get(i));
        }
        root.setDescendantFocusability(prev);
        // Mesmo tile de antes: o visual de foco nunca saiu, entao o listener continua quieto.
        relayouting = focusAfter != null && focusAfter == before;
        if (focusAfter != null) focusAfter.requestFocus();
        relayouting = false;
    }

    private boolean isInside(View v, View ancestor) {
        return v != null && (v == ancestor || (v.getParent() instanceof View && isInside((View) v.getParent(), ancestor)));
    }

    private void buildRows(LinearLayout target, List<Entry> list, final boolean forHome) {
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            final Entry e = list.get(i);
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
                rlp.setMargins(0, dp(6), 0, dp(6));
                target.addView(row, rlp);
            }
            View.OnClickListener click;
            if (forHome) {
                click = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (editing) showMenu(e, v);
                        else launch(e.intent);
                    }
                };
            } else {
                click = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addApp(e);
                    }
                };
            }
            View tile = makeTile(e, click);
            if (forHome) {
                tile.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (editing) return false;
                        enterEditMode();
                        return true;
                    }
                });
                tileViews.put(e.key, tile);
            }
            row.addView(tile);
        }
    }

    /**
     * Liga a primeira linha da grade as abas da barra (cima/baixo) e, na edicao, a ultima linha
     * a barra inferior, sem depender da geometria.
     */
    private void wireNavigation() {
        int rows = grid.getChildCount();
        if (rows == 0) {
            for (View t : tabs) t.setNextFocusDownId(editing ? addBtn.getId() : View.NO_ID);
            addBtn.setNextFocusUpId(View.NO_ID);
            doneBtn.setNextFocusUpId(View.NO_ID);
            return;
        }
        // Ids sao criados uma vez por tile; os vinculos sao zerados e refeitos a cada chamada.
        for (int r = 0; r < rows; r++) {
            LinearLayout lr = (LinearLayout) grid.getChildAt(r);
            for (int c = 0; c < lr.getChildCount(); c++) {
                View t = lr.getChildAt(c);
                if (t.getId() == View.NO_ID) t.setId(View.generateViewId());
                t.setNextFocusUpId(View.NO_ID);
                t.setNextFocusDownId(View.NO_ID);
            }
        }
        LinearLayout first = (LinearLayout) grid.getChildAt(0);
        LinearLayout last = (LinearLayout) grid.getChildAt(rows - 1);
        int n = first.getChildCount();
        if (!tabs.isEmpty()) {
            for (int c = 0; c < n; c++) {
                first.getChildAt(c).setNextFocusUpId(
                        tabs.get(Math.min(UP_TAB[Math.min(c, 3)], tabs.size() - 1)).getId());
            }
            for (int t = 0; t < tabs.size(); t++) {
                tabs.get(t).setNextFocusDownId(first.getChildAt(Math.min(t, n - 1)).getId());
            }
        }
        if (editing) {
            int ln = last.getChildCount();
            for (int c = 0; c < ln; c++) {
                last.getChildAt(c).setNextFocusDownId(c < (ln + 1) / 2 ? addBtn.getId() : doneBtn.getId());
            }
            addBtn.setNextFocusUpId(last.getChildAt(0).getId());
            doneBtn.setNextFocusUpId(last.getChildAt(ln - 1).getId());
        }
    }

    // ------------------------------------------------------------------ edicao

    private void enterEditMode() {
        if (editing) return;
        editing = true;
        gridScroll.setPadding(dp(24), dp(20), dp(24), dp(110));
        bottomBar.setAlpha(0f);
        bottomBar.setVisibility(View.VISIBLE);
        bottomBar.animate().alpha(1f).setDuration(160).start();
        wireNavigation();
        startWobble();
    }

    private void exitEditMode() {
        if (!editing) return;
        editing = false;
        movingKey = null;
        closeMenu(false);
        stopWobble();
        boolean barFocused = bottomBar.findFocus() != null;
        bottomBar.setVisibility(View.GONE);
        gridScroll.setPadding(dp(24), dp(20), dp(24), dp(20));
        wireNavigation(); // sem barra inferior, a ultima linha deixa de apontar para ela
        saveIfDirty();
        if (barFocused) focusFirstTile();
    }

    /** Faz os cards tremerem (menos o que esta sendo movido) com um unico animador. */
    private void startWobble() {
        stopWobble();
        for (Map.Entry<String, View> en : tileViews.entrySet()) {
            if (en.getKey().equals(movingKey)) continue;
            View card = ((ViewGroup) en.getValue()).getChildAt(0);
            // Camada de hardware: o card vira textura e girar custa quase nada por quadro.
            card.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            wobbleCards.add(card);
        }
        int n = wobbleCards.size();
        if (n == 0) return;
        if (wobblePhase.length < n) wobblePhase = new float[n];
        for (int i = 0; i < n; i++) wobblePhase[i] = random.nextFloat();
        wobbler = ValueAnimator.ofFloat(0f, 1f);
        wobbler.setDuration(260);
        wobbler.setInterpolator(new LinearInterpolator());
        wobbler.setRepeatCount(ValueAnimator.INFINITE);
        wobbler.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float f = a.getAnimatedFraction();
                for (int i = 0; i < wobbleCards.size(); i++) {
                    wobbleCards.get(i).setRotation(
                            1.4f * (float) Math.sin(2 * Math.PI * (f + wobblePhase[i])));
                }
            }
        });
        wobbler.start();
    }

    private void stopWobble() {
        if (wobbler != null) {
            wobbler.cancel();
            wobbler = null;
        }
        for (View card : wobbleCards) {
            card.setRotation(0f);
            card.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        wobbleCards.clear();
    }

    // ------------------------------------------------------------------ menu de acoes

    private void showMenu(final Entry e, View tile) {
        closeMenu(false);
        menuKey = e.key;

        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.HORIZONTAL);
        m.setGravity(Gravity.CENTER);
        m.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable mBg = new GradientDrawable();
        mBg.setCornerRadius(dp(40));
        mBg.setColor(0xE61C1C22);
        m.setBackground(mBg);

        final List<TextView> pills = new ArrayList<TextView>();
        pills.add(makePill(getString(R.string.action_move), false, new Runnable() {
            @Override
            public void run() {
                closeMenu(true);
                startMoving(e.key);
            }
        }));
        pills.add(makePill(getString(R.string.action_remove), false, new Runnable() {
            @Override
            public void run() {
                closeMenu(false);
                removeFromHome(e);
            }
        }));
        if (!e.system) {
            pills.add(makePill(getString(R.string.action_uninstall), false, new Runnable() {
                @Override
                public void run() {
                    closeMenu(true);
                    launch(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.pkg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
            }));
        }
        for (int i = 0; i < pills.size(); i++) {
            TextView p = pills.get(i);
            p.setNextFocusUpId(p.getId());
            p.setNextFocusDownId(p.getId());
            p.setNextFocusLeftId(i == 0 ? p.getId() : pills.get(i - 1).getId());
            p.setNextFocusRightId(i == pills.size() - 1 ? p.getId() : pills.get(i + 1).getId());
            m.addView(p);
        }

        m.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int mw = m.getMeasuredWidth();
        int mh = m.getMeasuredHeight();
        int[] loc = new int[2];
        tile.getLocationInWindow(loc);
        int x = loc[0] + tile.getWidth() / 2 - mw / 2;
        x = Math.max(dp(20), Math.min(x, root.getWidth() - mw - dp(20)));
        int y = loc[1] + tile.getHeight() + dp(6);
        if (y + mh > root.getHeight() - dp(110)) y = loc[1] - mh - dp(6);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = x;
        lp.topMargin = Math.max(dp(80), y);
        m.setAlpha(0f);
        root.addView(m, lp);
        m.animate().alpha(1f).setDuration(140).start();
        menu = m;
        final TextView firstPill = pills.get(0);
        m.post(new Runnable() {
            @Override
            public void run() {
                firstPill.requestFocus();
            }
        });
    }

    private void closeMenu(boolean refocus) {
        if (menu == null) return;
        root.removeView(menu);
        menu = null;
        if (refocus) {
            View t = tileViews.get(menuKey);
            if (t != null) t.requestFocus();
        }
    }

    private void removeFromHome(Entry e) {
        int idx = order.indexOf(e.key);
        order.remove(e.key);
        hidden.add(e.key);
        save();
        rebuildHome();
        releaseUnusedImages();
        String next = null;
        if (!home.isEmpty()) next = home.get(Math.min(Math.max(idx, 0), home.size() - 1)).key;
        renderGrid(next);
    }

    // ------------------------------------------------------------------ mover

    private void startMoving(String key) {
        movingKey = key;
        View tile = tileViews.get(key);
        if (tile != null) liftTile(tile);
        startWobble(); // recria a lista de cards, agora sem o que esta levantado
    }

    private void liftTile(View tile) {
        tile.animate().scaleX(1.25f).scaleY(1.25f).translationY(-dp(8)).alpha(1f)
                .setInterpolator(ease).setDuration(140).start();
    }

    /** Move o app levantado um passo; so reposiciona views e marca a ordem como alterada. */
    private void moveBy(int dir) {
        int idx = order.indexOf(movingKey);
        int to = HomeOrder.target(idx, dir, order.size(), COLUMNS);
        if (idx < 0 || to < 0) return;
        HomeOrder.move(order, idx, to);
        home.add(to, home.remove(idx)); // home espelha order; evita remontar a lista
        dirty = true; // grava so ao soltar / sair, nao a cada tecla
        relayout(); // o tile levantado mantem o visual: o foco nao sai dele
    }

    private void dropMoving() {
        View tile = tileViews.get(movingKey);
        movingKey = null;
        lastSig = HomeOrder.join(order);
        saveIfDirty();
        if (tile != null) {
            // Volta ao tamanho de "em foco".
            tile.animate().scaleX(1.15f).scaleY(1.15f).translationY(-dp(5))
                    .setInterpolator(ease).setDuration(140).start();
        }
        startWobble();
    }

    /** Libera as imagens dos apps que nao estao na Home (so a lista "Adicionar" as usa). */
    private void releaseUnusedImages() {
        Set<String> inHome = new HashSet<String>(order);
        for (Entry e : all.values()) {
            if (!inHome.contains(e.key)) e.image = null;
        }
    }

    // ------------------------------------------------------------------ adicionar apps

    private void openAdd() {
        if (addOverlay != null) return;
        closeMenu(false);
        // Bloqueia o foco por tras do overlay.
        savedFocus = new int[root.getChildCount()];
        for (int i = 0; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            if (c instanceof ViewGroup) {
                savedFocus[i] = ((ViewGroup) c).getDescendantFocusability();
                ((ViewGroup) c).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
        }

        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(0xE6000000);
        ov.setFocusable(true);

        TextView title = new TextView(this);
        title.setText(R.string.add_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setShadowLayer(dp(6), 0, dp(2), 0x80000000);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tlp.topMargin = dp(28);
        ov.addView(title, tlp);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setPadding(dp(24), dp(20), dp(24), dp(20));
        addGrid = new LinearLayout(this);
        addGrid.setOrientation(LinearLayout.VERTICAL);
        addGrid.setGravity(Gravity.CENTER);
        addGrid.setClipChildren(false);
        sv.addView(addGrid, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, -1);
        slp.topMargin = dp(80);
        ov.addView(sv, slp);

        addOverlay = ov;
        root.addView(ov, new FrameLayout.LayoutParams(-1, -1));
        fillAddList(0);
    }

    /** Lista os apps de TV que ainda nao estao na Home e foca o de indice {@code focusIndex}. */
    private void fillAddList(int focusIndex) {
        addGrid.removeAllViews();
        List<Entry> avail = new ArrayList<Entry>();
        Set<String> inHome = new HashSet<String>(order);
        for (Entry e : all.values()) {
            if (!inHome.contains(e.key)) avail.add(e);
        }
        sortByLabel(avail);
        if (avail.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.add_empty);
            empty.setTextColor(0xE6FFFFFF);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            empty.setGravity(Gravity.CENTER);
            addGrid.addView(empty, new LinearLayout.LayoutParams(-2, -2));
            // Sem tiles: o overlay segura o foco para as setas nao agirem nos botoes de tras.
            addOverlay.requestFocus();
            return;
        }
        buildRows(addGrid, avail, false);
        int idx = Math.max(0, Math.min(focusIndex, avail.size() - 1));
        ((LinearLayout) addGrid.getChildAt(idx / COLUMNS)).getChildAt(idx % COLUMNS).requestFocus();
    }

    private void addApp(Entry e) {
        hidden.remove(e.key);
        order.remove(e.key);
        order.add(e.key);
        save();
        rebuildHome();
        // Tira so o tile adicionado e reflui os outros, em vez de recriar a lista inteira.
        tmpTiles.clear();
        int idx = 0;
        for (int r = 0; r < addGrid.getChildCount(); r++) {
            View row = addGrid.getChildAt(r);
            if (!(row instanceof LinearLayout)) continue;
            LinearLayout lr = (LinearLayout) row;
            for (int c = 0; c < lr.getChildCount(); c++) {
                View t = lr.getChildAt(c);
                if (e.key.equals(t.getTag())) idx = tmpTiles.size();
                else tmpTiles.add(t);
            }
        }
        if (tmpTiles.isEmpty()) {
            fillAddList(0); // mostra "todos os apps ja estao na Home"
        } else {
            // Foco no app que ocupou o lugar do adicionado (ou no ultimo).
            reflow(addGrid, tmpTiles, tmpTiles.get(Math.min(idx, tmpTiles.size() - 1)));
        }
        tmpTiles.clear();
    }

    private void closeAdd(boolean refocus) {
        if (addOverlay == null) return;
        root.removeView(addOverlay);
        addOverlay = null;
        addGrid = null;
        if (savedFocus != null) {
            for (int i = 0; i < savedFocus.length && i < root.getChildCount(); i++) {
                View c = root.getChildAt(i);
                if (c instanceof ViewGroup) ((ViewGroup) c).setDescendantFocusability(savedFocus[i]);
            }
            savedFocus = null;
        }
        releaseUnusedImages();
        renderGrid(null);
        if (refocus && editing) addBtn.requestFocus();
    }

    // ------------------------------------------------------------------ componentes

    /** Pilula do estilo da barra superior: branca em foco. Nao entra na lista de abas. */
    private TextView makePill(String text, boolean selected, final Runnable action) {
        final TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(7), dp(18), dp(7));
        t.setFocusable(true);
        t.setClickable(true);
        t.setId(View.generateViewId());

        final GradientDrawable on = new GradientDrawable();
        on.setCornerRadius(dp(40));
        on.setColor(0xFFF2F2F2);
        final GradientDrawable sel = new GradientDrawable();
        sel.setCornerRadius(dp(40));
        sel.setColor(0x33FFFFFF);
        final Drawable idle = selected ? sel : null;
        t.setBackground(idle);
        t.setTextColor(Color.WHITE);

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

    private TextView makeTab(String text, boolean selected, final Runnable action) {
        TextView t = makePill(text, selected, action);
        tabs.add(t);
        return t;
    }

    private View makeTile(final Entry e, View.OnClickListener click) {
        final int radius = dp(12);
        final int lift = dp(5);
        final int cardW = dp(120);
        final int cardH = dp(68);
        ensureImage(e);

        final LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setFocusable(true);
        item.setClickable(true);
        item.setClipChildren(false);
        item.setAlpha(0.88f);
        item.setTag(e.key);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, -2);
        ilp.setMargins(dp(14), 0, dp(14), 0);
        item.setLayoutParams(ilp);

        final FrameLayout card = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(radius);
        cardBg.setColor(0x66202028);
        card.setBackground(cardBg);
        card.setOutlineProvider(cardOutline);
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

        item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (relayouting) return;
                float s = hasFocus ? 1.15f : 1f;
                v.animate().scaleX(s).scaleY(s).translationY(hasFocus ? -lift : 0)
                        .alpha(hasFocus ? 1f : 0.88f).setInterpolator(ease).setDuration(160).start();
                card.animate().translationZ(hasFocus ? dp(12) : 0).setDuration(160).start();
                label.animate().alpha(hasFocus ? 1f : 0f).setDuration(160).start();
            }
        });
        item.setOnClickListener(click);
        return item;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
