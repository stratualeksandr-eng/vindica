package app.vindica.diary;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/* Debug builds only: hosts «Сегодня» and «Часы» the way a home screen does, so an emulator can show them.
   Extras: fixture — a JSON file in the app's files to use as the page's data; which — hero, small or both;
   w, h, sw, sh — the sizes in dp. Needs «adb shell appwidget grantbind --package app.vindica.diary». */
public class WidgetHostActivity extends Activity {
    private AppWidgetHost host;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        String fixture = in.getStringExtra("fixture");
        if (fixture != null) {
            try (InputStream s = new FileInputStream(new File(getFilesDir(), fixture))) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = s.read(buf)) > 0) o.write(buf, 0, n);
                getSharedPreferences(ShopWidget.PREFS, MODE_PRIVATE).edit().putString(HeroWidget.KEY, o.toString("UTF-8")).commit();
            } catch (Exception e) {
                /* the shot shows whatever is there */
            }
        }
        String which = in.getStringExtra("which");
        if (which == null) which = "both";
        float dn = getResources().getDisplayMetrics().density;
        host = new AppWidgetHost(this, 1024);
        host.deleteHost();
        host.startListening();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF34404C);
        root.setPadding(Math.round(10 * dn), Math.round(30 * dn), 0, 0);
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        if (!"small".equals(which)) add(root, m, HeroWidget.class, in.getIntExtra("w", 360), in.getIntExtra("h", 620), dn);
        if (!"hero".equals(which)) add(root, m, HeroWidgetSmall.class, in.getIntExtra("sw", 360), in.getIntExtra("sh", 150), dn);
        setContentView(root);
    }

    private void add(LinearLayout root, AppWidgetManager m, Class<?> cls, int wDp, int hDp, float dn) {
        int id = host.allocateAppWidgetId();
        ComponentName cn = new ComponentName(this, cls);
        Bundle opts = new Bundle();
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp);
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp);
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp);
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp);
        if (!m.bindAppWidgetIdIfAllowed(id, cn, opts)) {
            TextView t = new TextView(this);
            t.setText("bind failed: " + cls.getSimpleName());
            t.setTextColor(0xFFFFFFFF);
            root.addView(t);
            return;
        }
        AppWidgetProviderInfo info = m.getAppWidgetInfo(id);
        AppWidgetHostView v = host.createView(this, id, info);
        v.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(wDp * dn), Math.round(hDp * dn));
        lp.bottomMargin = Math.round(10 * dn);
        root.addView(v, lp);
        m.updateAppWidgetOptions(id, opts);
        Intent u = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setComponent(cn);
        u.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{id});
        sendBroadcast(u);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (host != null) host.stopListening();
    }
}
