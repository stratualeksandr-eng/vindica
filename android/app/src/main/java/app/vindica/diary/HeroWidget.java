package app.vindica.diary;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/* «Сегодня» on the home screen: the first page of the app down to the bronze meander, and «Часы», the sundial
   with the counters. The texts of three weeks ahead come from the page («hero» in vindica_widget); the sundial,
   the sky and the moon are worked out here every minute, the countdowns run by themselves (Chronometer) and
   TEMPVS NOSTRVM turns its slow seconds over by itself (a list flipped once a second). */
public class HeroWidget extends AppWidgetProvider {
    static final String KEY = "hero";
    static final String ACTION_TICK = "app.vindica.diary.HERO_TICK";
    private static final long MIN = 60000L, HOUR = 3600000L;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        renderAll(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle opts) {
        renderAll(ctx);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(ShopWidget.PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) e.remove("hero.tn." + id);
        e.apply();
    }

    @Override
    public void onDisabled(Context ctx) {
        renderAll(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (ACTION_TICK.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
            || Intent.ACTION_LOCALE_CHANGED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) renderAll(ctx);
    }

    /* everything this minute needs, worked out once for all the widgets */
    static final class Frame {
        final long now;
        final TimeZone z;
        final HeroData d;
        final boolean day, dark;
        final HeroMath.Sky sky;
        final HeroMath.Body sun, moon;
        final HeroMath.Sun sp;
        final HeroMath.Ink ink;

        Frame(Context ctx, long now) {
            this.now = now;
            z = TimeZone.getDefault();
            d = HeroData.load(ctx);
            day = HeroMath.isDay(now, z);
            String look = d.str("look", "sun");
            boolean sys = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            dark = "dark".equals(look) || ("auto".equals(look) ? sys : !"day".equals(look) && !day);
            sky = HeroMath.sky(now, z, dark);
            sun = HeroMath.sunSky(now, z, dark);
            moon = HeroMath.moonSky(now, z, dark);
            sp = HeroMath.sunPos(now, z);
            ink = new HeroMath.Ink(dark);
        }

        /* the next look: the next minute (the shadow moves, TEMPVS NOSTRVM needs its next seconds), or sooner when
           a line changes, a countdown ends or drops under an hour */
        long next(boolean quick) {
            long n = (now / MIN + 1) * MIN;
            n = Math.min(n, d.next(now, "date", "hello", "hour", "mm", "sl", "wk", "astro"));
            for (String k : new String[]{"sl", "wk"}) {
                JSONArray e = d.cur(k, now);
                long to = e == null ? 0 : e.optLong(2);
                if (to > now) n = Math.min(n, to);
                if (to - HOUR > now) n = Math.min(n, to - HOUR);
            }
            if (quick) n = Math.min(n, now + 6000);
            return Math.max(n, now + 5000);
        }
    }

    static synchronized void renderAll(Context ctx) {
        try {
            HeroPlace.load(ctx);
            draw(ctx);
        } catch (RuntimeException e) {
            /* never take the app down with it; look again in a minute */
            schedule(ctx.getApplicationContext(), (System.currentTimeMillis() / MIN + 1) * MIN);
        }
    }

    private static void draw(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] big = mgr.getAppWidgetIds(new ComponentName(app, HeroWidget.class));
        int[] small = mgr.getAppWidgetIds(new ComponentName(app, HeroWidgetSmall.class));
        if (big.length + small.length == 0) {
            cancel(app);
            return;
        }
        Frame f = new Frame(app, System.currentTimeMillis());
        boolean quick = false;
        for (int id : big) {
            try {
                quick |= render(app, mgr, id, false, f);
            } catch (RuntimeException e) {
                /* the next look tries again */
            }
        }
        for (int id : small) {
            try {
                quick |= render(app, mgr, id, true, f);
            } catch (RuntimeException e) {
                /* the next look tries again */
            }
        }
        schedule(app, f.next(quick));
    }

    private static int dp(float v, float dn) {
        return Math.max(1, Math.round(v * dn));
    }

    /* one widget; returns true when the TEMPVS NOSTRVM list was set anew and wants its place found again shortly */
    @SuppressWarnings("deprecation")
    static boolean render(Context ctx, AppWidgetManager mgr, int id, boolean small, Frame f) {
        Resources res = ctx.getResources();
        float dn = res.getDisplayMetrics().density;
        Bundle opt = mgr.getAppWidgetOptions(id);
        int wDp = opt == null ? 0 : opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int hDp = opt == null ? 0 : opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (wDp <= 0) wDp = small ? 320 : 340;
        if (hDp <= 0) hDp = small ? 150 : 600;
        int wPx = dp(wDp, dn), hPx = dp(hDp, dn);
        HeroData d = f.d;
        HeroMath.Ink k = f.ink;
        RemoteViews v = new RemoteViews(ctx.getPackageName(), small ? R.layout.widget_hero_small : R.layout.widget_hero);

        /* the sky, the stars by night, the moon where it stands */
        v.setImageViewBitmap(R.id.hero_sky, HeroDraw.sky(wPx, hPx, f.sky, f.sun, dn));
        v.setViewVisibility(R.id.hero_stars, f.sky.stars > .01 ? View.VISIBLE : View.GONE);
        v.setInt(R.id.hero_stars, "setImageAlpha", (int) Math.round(HeroMath.cl(f.sky.stars, 0, 1) * 255));
        int mPx = dp(small ? 30 : 38, dn);
        if (f.moon.o > .02) {
            v.setViewVisibility(R.id.hero_moon, View.VISIBLE);
            v.setImageViewBitmap(R.id.hero_moon, HeroDraw.moon(mPx, f.moon.phase, f.dark));
            v.setInt(R.id.hero_moon, "setImageAlpha", (int) Math.round(HeroMath.cl(f.moon.o, 0, 1) * 255));
            int x = (int) Math.round(f.moon.x / 100 * wPx - mPx / 2.0), y = (int) Math.round(f.moon.y / 100 * hPx - mPx / 2.0);
            v.setViewPadding(R.id.hero_moon_box, Math.max(0, x), Math.max(0, y), 0, 0);
        } else {
            v.setViewVisibility(R.id.hero_moon, View.GONE);
        }

        /* the sundial */
        float dialDp = small ? Math.min((hDp - 16) / .86f, wDp - 16 - 138) : Math.min(wDp - 32, 340);
        v.setImageViewBitmap(R.id.hero_dial, HeroDraw.dialCached(ctx, dp(Math.max(80, dialDp), dn), f.now, f.z, f.dark, f.day));

        /* Memento mori, TEMPVS NOSTRVM, PROTINVS VIVE; on a low «Часы» the Latin captions step aside */
        boolean quick = counters(ctx, v, id, f, dn);
        if (small) {
            int cap = hDp < 150 ? View.GONE : View.VISIBLE;
            v.setViewVisibility(R.id.hero_mm_l, cap);
            v.setViewVisibility(R.id.hero_pv, cap);
            if (hDp < 150) v.setViewVisibility(R.id.hero_tn_l, View.GONE);
            if (hDp < 125) v.setViewVisibility(R.id.hero_mm_bar, View.GONE);
        }

        if (!small) page(ctx, v, f, wDp, hDp, dn);

        PendingIntent open = ShopWidget.open(ctx, "today", 3);
        v.setOnClickPendingIntent(android.R.id.background, open);
        v.setPendingIntentTemplate(R.id.hero_tn, open);
        mgr.updateAppWidget(id, v);
        return quick;
    }

    private static void countdown(RemoteViews v, int id, long to, long now) {
        long left = Math.max(0, to - now);
        v.setChronometerCountDown(id, true);
        v.setChronometer(id, SystemClock.elapsedRealtime() + left, left < HOUR ? "0:%s" : null, true);
    }

    @SuppressWarnings("deprecation")
    private static boolean counters(Context ctx, RemoteViews v, int id, Frame f, float dn) {
        HeroData d = f.d;
        HeroMath.Ink k = f.ink;
        JSONArray mm = d.cur("mm", f.now);
        if (mm == null) {
            v.setViewVisibility(R.id.hero_mm, View.GONE);
            return false;
        }
        v.setViewVisibility(R.id.hero_mm, View.VISIBLE);
        v.setTextColor(R.id.hero_mm_l, k.brz);
        v.setTextColor(R.id.hero_tn_l, k.brz);
        v.setTextColor(R.id.hero_pv, k.brz);
        JSONObject lab = d.o.optJSONObject("lab");
        if (lab != null) {
            v.setTextViewText(R.id.hero_mm_l, lab.optString("mm", "MEMENTO MORI"));
            v.setTextViewText(R.id.hero_tn_l, lab.optString("tn", "TEMPVS NOSTRVM"));
            v.setTextViewText(R.id.hero_pv, lab.optString("pv", "PROTINVS VIVE"));
        }
        v.setTextViewText(R.id.hero_mm_n, HeroData.at(mm, 1));
        v.setTextColor(R.id.hero_mm_n, k.ink);
        v.setTextViewText(R.id.hero_mm_u, HeroData.at(mm, 2));
        v.setTextColor(R.id.hero_mm_u, k.mut);
        v.setViewVisibility(R.id.hero_mm_n, HeroData.at(mm, 1).isEmpty() ? View.GONE : View.VISIBLE);
        double fr = mm.optDouble(3, -1);
        if (fr >= 0) {
            v.setViewVisibility(R.id.hero_mm_bar, View.VISIBLE);
            v.setImageViewBitmap(R.id.hero_mm_bar, HeroDraw.bar(dp(124, dn), dp(2, dn), fr, k.ink, k.brz));
        } else {
            v.setViewVisibility(R.id.hero_mm_bar, View.INVISIBLE);
        }

        /* TEMPVS NOSTRVM: the list of its seconds starts at the page's anchor; here it is told where it stands now */
        boolean quick = false;
        JSONObject tn = d.o.optJSONObject("tn");
        if (tn != null && tn.optDouble("v", 0) > 0 && Build.VERSION.SDK_INT >= 31) {
            /* the next two and a half minutes, second by second; the next look hands over the next ones */
            long at = tn.optLong("at"), base = f.now / 1000 * 1000;
            double tv = tn.optDouble("v", 0), tr = tn.optDouble("r", 0);
            String sep = tn.optString("sep", "\u00a0");
            RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1);
            for (int i = 0; i < 150; i++) {
                long t = base + i * 1000L;
                items.addItem(t / 1000, HeroTnService.item(ctx, HeroTnService.fmt(tv - tr * (t - at) / 1000.0, sep), k.ink));
            }
            v.setRemoteAdapter(R.id.hero_tn, items.build());
            v.setDisplayedChild(R.id.hero_tn, 0);
            v.setViewVisibility(R.id.hero_tn, View.VISIBLE);
            v.setViewVisibility(R.id.hero_tn_l, View.VISIBLE);
        } else if (tn != null && tn.optDouble("v", 0) > 0) {
            long at = tn.optLong("at");
            String uri = "vindica://hero-tn/" + id + "/" + at + "/" + (f.dark ? 1 : 0);
            Intent svc = new Intent(ctx, HeroTnService.class);
            svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            svc.setData(Uri.parse(uri));
            v.setRemoteAdapter(R.id.hero_tn, svc);
            long idx = Math.max(0, Math.min(HeroTnService.COUNT - 1, (f.now - at) / 1000));
            v.setDisplayedChild(R.id.hero_tn, (int) idx);
            v.setViewVisibility(R.id.hero_tn, View.VISIBLE);
            v.setViewVisibility(R.id.hero_tn_l, View.VISIBLE);
            SharedPreferences sp = ctx.getSharedPreferences(ShopWidget.PREFS, Context.MODE_PRIVATE);
            if (!uri.equals(sp.getString("hero.tn." + id, ""))) {
                sp.edit().putString("hero.tn." + id, uri).apply();
                quick = true;
            }
        } else {
            v.setViewVisibility(R.id.hero_tn, View.GONE);
            v.setViewVisibility(R.id.hero_tn_l, View.GONE);
        }

        /* until lights out, or until rising */
        JSONArray sl = d.cur("sl", f.now);
        if (sl != null) {
            v.setViewVisibility(R.id.hero_sl_row, View.VISIBLE);
            v.setTextViewText(R.id.hero_sl_l, HeroData.at(sl, 1));
            v.setTextColor(R.id.hero_sl_l, k.mut);
            v.setTextColor(R.id.hero_sl_c, k.ink);
            countdown(v, R.id.hero_sl_c, sl.optLong(2), f.now);
        } else {
            v.setViewVisibility(R.id.hero_sl_row, View.GONE);
        }
        /* until the working day ends; «окончена», «выходной», or nothing once work is over for good */
        JSONArray wk = d.cur("wk", f.now);
        if (wk != null && !HeroData.at(wk, 1).isEmpty()) {
            v.setViewVisibility(R.id.hero_wk_row, View.VISIBLE);
            v.setTextViewText(R.id.hero_wk_l, HeroData.at(wk, 1));
            v.setTextColor(R.id.hero_wk_l, k.mut);
            long to = wk.optLong(2);
            if (to > f.now) {
                v.setViewVisibility(R.id.hero_wk_c, View.VISIBLE);
                v.setViewVisibility(R.id.hero_wk_t, View.GONE);
                v.setTextColor(R.id.hero_wk_c, k.ink);
                countdown(v, R.id.hero_wk_c, to, f.now);
            } else {
                v.setViewVisibility(R.id.hero_wk_c, View.GONE);
                v.setViewVisibility(R.id.hero_wk_t, View.VISIBLE);
                v.setTextViewText(R.id.hero_wk_t, HeroData.at(wk, 3));
                v.setTextColor(R.id.hero_wk_t, k.ink);
            }
        } else {
            v.setViewVisibility(R.id.hero_wk_row, View.GONE);
        }
        return quick;
    }

    /* the rest of the first page: the brand, the date, the greeting, the hour, Seneca, the stars of the day */
    private static void page(Context ctx, RemoteViews v, Frame f, int wDp, int hDp, float dn) {
        HeroData d = f.d;
        HeroMath.Ink k = f.ink;
        String lg = d.str("lang", Locale.getDefault().getLanguage());
        /* the Romanian and English greetings are the longer ones: «Bună dimineața,», «Good afternoon,» */
        boolean wide = "ro".equals(lg) || "en".equals(lg);
        v.setImageViewResource(R.id.hero_logo, f.dark ? R.drawable.hero_logo_dark : R.drawable.hero_logo_light);
        v.setImageViewResource(R.id.hero_mz, f.dark ? R.drawable.hero_mz_dark : R.drawable.hero_mz_light);
        v.setTextColor(R.id.hero_brand_t, k.ink);
        v.setTextColor(R.id.hero_brand_l, k.brz);
        JSONArray brand = d.has() ? d.o.optJSONArray("brand") : null;
        if (brand != null) v.setTextViewText(R.id.hero_brand_t, brand.optString(0, ctx.getString(R.string.hero_brand)));

        /* the date and the day of life */
        JSONArray date = d.cur("date", f.now);
        v.setTextColor(R.id.hero_date, k.mut);
        v.setTextViewText(R.id.hero_date, date != null ? HeroData.at(date, 1) : fallbackDate(f.now, lg));

        /* the greeting beside Memento mori, in Forum, as wide as the room it has */
        String l1, l2;
        JSONArray hello = d.cur("hello", f.now);
        if (hello != null) {
            l1 = HeroData.at(hello, 1);
            l2 = HeroData.at(hello, 2);
        } else {
            l1 = fallbackHello(ctx, f.now);
            l2 = "";
        }
        boolean mm = d.cur("mm", f.now) != null;
        float roomDp = wDp - 32 - (mm ? 136 : 0);
        float sizeDp = Math.min(Math.max(36, Math.min(46, wDp * .1f)), (wDp - 32 - 136) / (wide ? 6.5f : 6.12f));
        if (!mm) sizeDp = Math.max(36, Math.min(46, wDp * .1f));
        v.setImageViewBitmap(R.id.hero_hello, HeroDraw.helloCached(ctx, l1, l2, dp(roomDp, dn), sizeDp * dn, k.ink));
        v.setContentDescription(R.id.hero_hello, (l1 + " " + l2).trim());

        /* the hour: the night watch, or the sun's own time by day */
        JSONArray hour = d.cur("hour", f.now);
        if (hour != null) {
            v.setViewVisibility(R.id.hero_hour, View.VISIBLE);
            v.setTextViewText(R.id.hero_hour, HeroData.at(hour, 1).replace("{S}", HeroMath.fmtM(f.sp.tst)));
            v.setTextColor(R.id.hero_hour, k.mut);
        } else {
            v.setViewVisibility(R.id.hero_hour, View.GONE);
        }

        /* Seneca under the dial */
        JSONArray motto = d.has() ? d.o.optJSONArray("motto") : null;
        if (motto != null && motto.length() >= 3) {
            v.setTextViewText(R.id.hero_la, motto.optString(0) + "\n" + motto.optString(1));
            v.setTextViewText(R.id.hero_tr, motto.optString(2));
        }
        v.setTextColor(R.id.hero_la, k.ink);
        v.setTextColor(R.id.hero_tr, k.mut);

        /* the stars of the day: as many lines as the height leaves after a dial worth looking at */
        JSONArray astro = d.cur("astro", f.now);
        int room = hDp - 350;
        boolean brandOn = room >= 260;
        if (!brandOn) room += 36;
        boolean mottoOn = room >= 120;
        if (!mottoOn) room += 54;
        int lines = astro == null ? 0 : Math.max(0, Math.min(6, (room - 190 - 24) / 17));
        if (lines < 2) lines = 0;
        v.setViewVisibility(R.id.hero_brand, brandOn ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.hero_la, mottoOn ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.hero_tr, mottoOn ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.hero_ah, lines > 0 ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.hero_astro, lines > 0 ? View.VISIBLE : View.GONE);
        if (lines > 0) {
            JSONObject lab = d.o.optJSONObject("lab");
            if (lab != null) v.setTextViewText(R.id.hero_ah, lab.optString("ah", "ASTRA INCLINANT, NON NECESSITANT"));
            v.setTextColor(R.id.hero_ah, k.brz);
            v.setTextViewText(R.id.hero_astro, HeroData.at(astro, 1));
            v.setTextColor(R.id.hero_astro, k.ink);
            v.setInt(R.id.hero_astro, "setMaxLines", lines);
        }
    }

    /* the date when the page has not said it yet: «Суббота, 28 ноября», «Sâmbătă, 28 noiembrie», «Saturday, 28 November» */
    static String fallbackDate(long now, String lang) {
        Locale l = "ro".equals(lang) ? new Locale("ro") : "en".equals(lang) ? Locale.UK : new Locale("ru");
        String s = new SimpleDateFormat("EEEE, d MMMM", l).format(new Date(now));
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(l) + s.substring(1);
    }

    static String fallbackHello(Context ctx, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int h = c.get(Calendar.HOUR_OF_DAY);
        String[] a = ctx.getResources().getStringArray(R.array.hero_hello);
        return a[h < 4 ? 0 : h < 12 ? 1 : h < 17 ? 2 : h < 23 ? 3 : 0];
    }

    static PendingIntent tick(Context ctx) {
        Intent i = new Intent(ctx, HeroWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(ctx, 41, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /* the next look, on time when the phone allows exact alarms (it does with USE_EXACT_ALARM); never waking it */
    static void schedule(Context ctx, long at) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = tick(ctx);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExact(AlarmManager.RTC, at, pi);
            else am.set(AlarmManager.RTC, at, pi);
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC, at, pi);
        }
    }

    static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tick(ctx));
    }
}
