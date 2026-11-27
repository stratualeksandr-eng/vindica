package app.vindica.diary;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/* Where the phone is: a coarse fix from the phone's own location (with its permission), rounded to about a kilometre,
   the town's name from the skylines' catalog (res/raw/skyline.json, the same file the page draws) or else from the
   phone's geocoder, kept in vindica_widget as "hero.place". The sun, the moon, the weather and the skyline under the
   sky follow it; until it is known the sky stands over Brașov on a Romanian clock, as it always did. */
final class HeroPlace {
    static final String KEY = "hero.place";
    static volatile boolean has;
    static volatile double lat, lon;
    private static String lastS;
    private static Place last;
    private static volatile boolean busy;
    private static volatile long tried;
    private static List<City> cities;
    private static Art generic;

    private HeroPlace() {}

    static final class Place {
        double lat, lon;
        String name = "", lang = "";
        long at;
    }

    /* a town of the catalog: its names, where it is, how far it reaches, the line below which words may lie over it,
       the top of its landmark, and its silhouette */
    static final class City {
        String id, ru, ro;
        double lat, lon, r;
        int clear, top;
        Art art;
    }

    /* the silhouette: polylines in units 1000 wide, y=420 the base; one path per tone */
    static final class Art {
        final List<String> tones = new ArrayList<>();
        final List<Path> paths = new ArrayList<>();
        boolean hasNear;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(ShopWidget.PREFS, Context.MODE_PRIVATE);
    }

    static synchronized Place load(Context ctx) {
        String s = prefs(ctx).getString(KEY, null);
        if (s == null) {
            has = false;
            lastS = null;
            last = null;
            return null;
        }
        if (s.equals(lastS)) return last;
        lastS = s;
        last = null;
        try {
            JSONObject o = new JSONObject(s);
            Place p = new Place();
            p.lat = o.getDouble("lat");
            p.lon = o.getDouble("lon");
            p.name = o.optString("name", "");
            p.lang = o.optString("lang", "");
            p.at = o.optLong("at");
            lat = p.lat;
            lon = p.lon;
            has = true;
            last = p;
            return p;
        } catch (Exception e) {
            has = false;
            return null;
        }
    }

    static boolean permitted(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean roClock() {
        long now = System.currentTimeMillis();
        double tz = TimeZone.getDefault().getOffset(now) / 3600000.0;
        return tz == 2 || tz == 3;
    }

    /* the place the sky stands over: the phone's own, else Brașov on a Romanian clock, else none */
    static double[] where(Context ctx) {
        Place p = load(ctx);
        if (p != null) return new double[]{p.lat, p.lon};
        return roClock() ? new double[]{45.65, 25.6} : null;
    }

    static double km(double a, double b, double c, double d) {
        double r = Math.PI / 180, x = Math.sin((c - a) * r / 2), y = Math.sin((d - b) * r / 2);
        return 12742 * Math.asin(Math.min(1, Math.sqrt(x * x + Math.cos(a * r) * Math.cos(c * r) * y * y)));
    }

    private static synchronized void catalog(Context ctx) {
        if (cities != null) return;
        List<City> out = new ArrayList<>();
        try (InputStream in = ctx.getResources().openRawResource(R.raw.skyline)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int k;
            while ((k = in.read(buf)) > 0) b.write(buf, 0, k);
            JSONObject o = new JSONObject(b.toString("UTF-8"));
            JSONArray cs = o.getJSONArray("cities");
            for (int i = 0; i < cs.length(); i++) {
                JSONObject c = cs.getJSONObject(i);
                City x = new City();
                x.id = c.getString("id");
                x.ru = c.optString("ru");
                x.ro = c.optString("ro");
                x.lat = c.getDouble("lat");
                x.lon = c.getDouble("lon");
                x.r = c.optDouble("r", 10);
                x.clear = c.optInt("clear", 0);
                x.top = c.optInt("top", x.clear);
                x.art = art(c.getJSONArray("art"));
                out.add(x);
            }
            generic = art(o.getJSONArray("generic"));
        } catch (Exception e) {
            generic = new Art();
        }
        cities = out;
    }

    private static Art art(JSONArray a) throws Exception {
        Art t = new Art();
        for (int i = 0; i < a.length(); i++) {
            JSONArray l = a.getJSONArray(i);
            String tone = l.getString(0);
            t.tones.add(tone);
            t.paths.add(parse(l.getString(1)));
            if ("near".equals(tone)) t.hasNear = true;
        }
        return t;
    }

    /* M x y L x y x y … Z, as skyline.py writes them */
    static Path parse(String d) {
        Path p = new Path();
        int n = d.length(), i = 0;
        char cmd = 'M';
        boolean first = true;
        float[] xy = new float[2];
        int k = 0;
        while (i < n) {
            char ch = d.charAt(i);
            if (ch == 'M' || ch == 'L') {
                cmd = ch;
                first = true;
                k = 0;
                i++;
            } else if (ch == 'Z' || ch == 'z') {
                p.close();
                i++;
            } else if (ch == '-' || ch == '.' || (ch >= '0' && ch <= '9')) {
                int j = i + 1;
                while (j < n) {
                    char c = d.charAt(j);
                    if ((c >= '0' && c <= '9') || c == '.') j++;
                    else break;
                }
                xy[k++] = Float.parseFloat(d.substring(i, j));
                i = j;
                if (k == 2) {
                    if (cmd == 'M' && first) p.moveTo(xy[0], xy[1]);
                    else p.lineTo(xy[0], xy[1]);
                    first = false;
                    k = 0;
                }
            } else {
                i++;
            }
        }
        return p;
    }

    /* the town of the catalog the phone is in, or null */
    static City city(Context ctx) {
        catalog(ctx);
        double[] w = where(ctx);
        if (w == null) return null;
        City best = null;
        double bd = 1e9;
        for (City c : cities) {
            double k = km(w[0], w[1], c.lat, c.lon);
            if (k <= c.r && k < bd) {
                best = c;
                bd = k;
            }
        }
        return best;
    }

    static Art art(Context ctx) {
        City c = city(ctx);
        return c != null ? c.art : generic;
    }

    /* the town's name in the app's language, «Брашов» or «Buzău»; the geocoder's name for other places */
    static String name(Context ctx, String lang) {
        City c = city(ctx);
        if (c != null) return "ro".equals(lang) ? c.ro : c.ru;
        Place p = load(ctx);
        return p != null && p.name != null ? p.name : "";
    }

    /* what tells the drawings apart: the town, or the place and its name */
    static String key(Context ctx) {
        City c = city(ctx);
        Place p = load(ctx);
        return (c != null ? c.id : "-") + "/" + (p != null ? p.lat + "," + p.lon + "," + p.name : "");
    }

    /* looks again when the last fix is older than a quarter of an hour (or at once when asked to), only with the
       phone's permission; done gets true when the place or its name changed. Runs on a thread of its own. */
    static void refresh(Context ctx, boolean force, String lang, final Done done) {
        final Context app = ctx.getApplicationContext();
        long now = System.currentTimeMillis();
        Place p = load(app);
        if (!permitted(app) || busy) {
            if (done != null) done.run(false);
            return;
        }
        if (!force && ((p != null && now - p.at < 15 * 60000L) || now - tried < 2 * 60000L)) {
            if (done != null) done.run(false);
            return;
        }
        busy = true;
        tried = now;
        final String lg = lang == null || lang.isEmpty() ? (p != null && !p.lang.isEmpty() ? p.lang : "ru") : lang;
        new Thread(() -> {
            boolean changed = false;
            try {
                Location l = fix(app);
                if (l != null) changed = store(app, l, lg);
            } catch (Exception e) {
                /* no fix: the last place stays */
            } finally {
                busy = false;
                if (done != null) done.run(changed);
            }
        }, "vindica-place").start();
    }

    interface Done {
        void run(boolean changed);
    }

    private static Location fix(Context ctx) throws InterruptedException {
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        List<String> prs;
        try {
            prs = lm.getProviders(true);
        } catch (Exception e) {
            return null;
        }
        Location best = null;
        for (String pr : prs) {
            try {
                Location l = lm.getLastKnownLocation(pr);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException | IllegalArgumentException e) {
                /* not this one */
            }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 10 * 60000L) return best;
        /* a fresh fix: the phone's own fused one, else the network's, then satellites as a last resort */
        String first = prs.contains("fused") ? "fused" : prs.contains(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null;
        Location got = first == null ? null : current(ctx, lm, first, 25);
        if (got == null && prs.contains(LocationManager.GPS_PROVIDER)) got = current(ctx, lm, LocationManager.GPS_PROVIDER, 20);
        return got != null ? got : best;
    }

    @SuppressWarnings("deprecation")
    private static Location current(Context ctx, LocationManager lm, String pr, int sec) throws InterruptedException {
        final Location[] got = {null};
        final CountDownLatch cd = new CountDownLatch(1);
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                CancellationSignal cs = new CancellationSignal();
                lm.getCurrentLocation(pr, cs, ctx.getMainExecutor(), l -> {
                    got[0] = l;
                    cd.countDown();
                });
                if (!cd.await(sec, TimeUnit.SECONDS)) cs.cancel();
            } else {
                LocationListener li = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location l) {
                        got[0] = l;
                        cd.countDown();
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(String provider) {}

                    @Override
                    public void onProviderDisabled(String provider) {}
                };
                lm.requestSingleUpdate(pr, li, Looper.getMainLooper());
                if (!cd.await(sec, TimeUnit.SECONDS)) lm.removeUpdates(li);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            /* the phone said no */
        }
        return got[0];
    }

    private static boolean store(Context ctx, Location l, String lang) throws Exception {
        Place old = load(ctx);
        double la = Math.round(l.getLatitude() * 100) / 100.0, lo = Math.round(l.getLongitude() * 100) / 100.0;
        boolean moved = old == null || km(old.lat, old.lon, la, lo) > 1.5;
        String name = !moved && lang.equals(old.lang) && !old.name.isEmpty() ? old.name : geocode(ctx, l.getLatitude(), l.getLongitude(), lang);
        if (name == null) name = !moved ? old.name : "";
        boolean changed = moved || !name.equals(old.name);
        JSONObject o = new JSONObject();
        o.put("lat", la);
        o.put("lon", lo);
        o.put("name", name);
        o.put("lang", lang);
        o.put("at", System.currentTimeMillis());
        prefs(ctx).edit().putString(KEY, o.toString()).commit();
        load(ctx);
        return changed;
    }

    /* the town's name from the phone's geocoder, in the app's language; null when it cannot say */
    private static String geocode(Context ctx, double la, double lo, String lang) {
        if (!Geocoder.isPresent()) return null;
        Geocoder g = new Geocoder(ctx, Locale.forLanguageTag(lang));
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                final String[] r = {null};
                final CountDownLatch cd = new CountDownLatch(1);
                g.getFromLocation(la, lo, 1, new Geocoder.GeocodeListener() {
                    @Override
                    public void onGeocode(List<Address> a) {
                        r[0] = pick(a);
                        cd.countDown();
                    }

                    @Override
                    public void onError(String m) {
                        cd.countDown();
                    }
                });
                cd.await(15, TimeUnit.SECONDS);
                return r[0];
            }
            @SuppressWarnings("deprecation") List<Address> a = g.getFromLocation(la, lo, 1);
            return pick(a);
        } catch (Exception e) {
            return null;
        }
    }

    private static String pick(List<Address> a) {
        if (a == null || a.isEmpty()) return null;
        Address x = a.get(0);
        String n = x.getLocality();
        if (n == null || n.isEmpty()) n = x.getSubAdminArea();
        if (n == null || n.isEmpty()) n = x.getAdminArea();
        return n;
    }

    /* for the place the page sends (tests, or a fix it got itself) */
    static boolean put(Context ctx, double la, double lo, String name, String lang) {
        try {
            Place old = load(ctx);
            la = Math.round(la * 100) / 100.0;
            lo = Math.round(lo * 100) / 100.0;
            boolean changed = old == null || old.lat != la || old.lon != lo || !old.name.equals(name == null ? "" : name);
            JSONObject o = new JSONObject();
            o.put("lat", la);
            o.put("lon", lo);
            o.put("name", name == null ? "" : name);
            o.put("lang", lang == null ? "" : lang);
            o.put("at", System.currentTimeMillis());
            prefs(ctx).edit().putString(KEY, o.toString()).commit();
            load(ctx);
            return changed;
        } catch (Exception e) {
            return false;
        }
    }

    static JSONObject json(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            Place p = load(ctx);
            if (p != null) {
                o.put("lat", p.lat);
                o.put("lon", p.lon);
                o.put("name", p.name);
                o.put("at", p.at);
            }
            o.put("perm", permitted(ctx) ? "granted" : "prompt");
        } catch (Exception e) {
            /* nothing to say */
        }
        return o;
    }
}
