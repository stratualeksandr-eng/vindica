package app.vindica.diary;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/* The weather for the live wallpaper: Open-Meteo for where the phone is (HeroPlace; Brașov on a Romanian clock
   until it is known), asked again every half hour while the wallpaper is in view and whenever the place changes,
   kept in vindica_widget. Shown as the page shows its weather, «Брашов: пасмурно, +3°», after the hour; cloud cover
   also greys the sky and hides the stars. */
final class HeroWeather {
    static final String KEY = "hero.wx";
    private static final long FRESH = 30 * 60000L, OLD = 3 * 3600000L;
    private static volatile boolean busy;
    private static volatile long tried;
    private static String lastS;
    private static Now lastN;

    private HeroWeather() {}

    static final class Now {
        long at;
        double t, cover, wind, lat = Double.NaN, lon = Double.NaN;
        int code;
    }

    static synchronized Now get(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(ShopWidget.PREFS, Context.MODE_PRIVATE);
        String s = sp.getString(KEY, null);
        if (s == null) return null;
        if (s.equals(lastS)) return lastN;
        lastS = s;
        lastN = null;
        try {
            JSONObject o = new JSONObject(s);
            Now n = new Now();
            n.at = o.optLong("at");
            n.t = o.optDouble("t", 0);
            n.code = o.optInt("code", 0);
            n.cover = o.optDouble("cover", 0);
            n.wind = o.optDouble("wind", 0);
            n.lat = o.optDouble("lat", Double.NaN);
            n.lon = o.optDouble("lon", Double.NaN);
            lastN = n;
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    /* is the last answer for this place? (the weather of a town left behind is not shown) */
    private static boolean here(Context ctx, Now n) {
        double[] w = HeroPlace.where(ctx);
        if (w == null) return false;
        return Double.isNaN(n.lat) ? !HeroPlace.has : HeroPlace.km(n.lat, n.lon, w[0], w[1]) < 8;
    }

    static boolean stale(Context ctx, long now) {
        Now n = get(ctx);
        return n == null || now - n.at > FRESH || !here(ctx, n);
    }

    /* asks again when the last answer is older than half an hour (after a failure not sooner than in five minutes),
       or at once when asked to; done runs afterwards */
    static void refresh(Context ctx, boolean force, Runnable done) {
        final Context app = ctx.getApplicationContext();
        long now = System.currentTimeMillis();
        if (busy || (!force && (!stale(app, now) || now - tried < 5 * 60000L))) return;
        busy = true;
        tried = now;
        new Thread(() -> {
            try {
                fetch(app);
            } catch (Exception e) {
                /* no network: the last answer stays for three hours, then the weather simply is not shown */
            } finally {
                busy = false;
                if (done != null) done.run();
            }
        }, "vindica-weather").start();
    }

    private static void fetch(Context ctx) throws Exception {
        long now = System.currentTimeMillis();
        double[] w = HeroPlace.where(ctx);
        if (w == null) return;
        String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
            + "&current=temperature_2m,weather_code,cloud_cover,wind_speed_10m&wind_speed_unit=ms&timezone=auto", w[0], w[1]);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Vindica (app.vindica.diary)");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int k;
            while ((k = in.read(buf)) > 0) b.write(buf, 0, k);
            JSONObject cur = new JSONObject(b.toString("UTF-8")).getJSONObject("current");
            JSONObject o = new JSONObject();
            o.put("at", now);
            o.put("t", cur.optDouble("temperature_2m", 0));
            o.put("code", cur.optInt("weather_code", 0));
            o.put("cover", Math.max(0, Math.min(1, cur.optDouble("cloud_cover", 0) / 100)));
            o.put("wind", cur.optDouble("wind_speed_10m", 0));
            o.put("lat", w[0]);
            o.put("lon", w[1]);
            ctx.getSharedPreferences(ShopWidget.PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply();
        } finally {
            c.disconnect();
        }
    }

    /* «Пасмурно, +3°», or nothing when the last answer is more than three hours old or for another place */
    static String text(Context ctx, String lang, long now) {
        Now n = get(ctx);
        if (n == null || now - n.at > OLD || !here(ctx, n)) return "";
        long t = Math.round(n.t);
        return phrase(n.code, "ro".equals(lang)) + ", " + (t > 0 ? "+" : t < 0 ? "−" : "") + Math.abs(t) + "°";
    }

    static double cover(Context ctx, long now) {
        Now n = get(ctx);
        return n == null || now - n.at > OLD || !here(ctx, n) ? 0 : n.cover;
    }

    /* the WMO weather codes, in words */
    static String phrase(int c, boolean ro) {
        if (c == 0) return ro ? "Senin" : "Ясно";
        if (c == 1) return ro ? "Predominant senin" : "Преимущественно ясно";
        if (c == 2) return ro ? "Parțial noros" : "Переменная облачность";
        if (c == 3) return ro ? "Înnorat" : "Пасмурно";
        if (c == 45 || c == 48) return ro ? "Ceață" : "Туман";
        if (c == 51 || c == 53 || c == 55) return ro ? "Burniță" : "Морось";
        if (c == 56 || c == 57) return ro ? "Burniță înghețată" : "Ледяная морось";
        if (c == 61) return ro ? "Ploaie slabă" : "Небольшой дождь";
        if (c == 63) return ro ? "Ploaie" : "Дождь";
        if (c == 65) return ro ? "Ploaie puternică" : "Сильный дождь";
        if (c == 66 || c == 67) return ro ? "Ploaie înghețată" : "Ледяной дождь";
        if (c == 71) return ro ? "Ninsoare slabă" : "Небольшой снег";
        if (c == 73) return ro ? "Ninsoare" : "Снег";
        if (c == 75) return ro ? "Ninsoare puternică" : "Сильный снег";
        if (c == 77) return ro ? "Grăunțe de zăpadă" : "Снежная крупа";
        if (c == 80 || c == 81) return ro ? "Averse" : "Ливень";
        if (c == 82) return ro ? "Averse puternice" : "Сильный ливень";
        if (c == 85 || c == 86) return ro ? "Averse de ninsoare" : "Снегопад";
        if (c == 95) return ro ? "Furtună" : "Гроза";
        if (c == 96 || c == 99) return ro ? "Furtună cu grindină" : "Гроза с градом";
        return ro ? "Vreme" : "Погода";
    }
}
