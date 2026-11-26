package app.vindica.diary;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/* What the page handed the widget («hero» in vindica_widget): for each line a list of [from, …] entries,
   three weeks ahead, so the widget knows what to show at any moment without the app. */
final class HeroData {
    final JSONObject o;

    private static String lastJson;
    private static HeroData last;

    private HeroData(JSONObject o) {
        this.o = o;
    }

    static synchronized HeroData load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(ShopWidget.PREFS, Context.MODE_PRIVATE);
        String s = sp.getString(HeroWidget.KEY, null);
        if (s != null && s.equals(lastJson) && last != null) return last;
        JSONObject o = null;
        if (s != null) {
            try {
                o = new JSONObject(s);
            } catch (Exception e) {
                o = null;
            }
        }
        lastJson = s;
        last = new HeroData(o);
        return last;
    }

    boolean has() {
        return o != null;
    }

    String str(String key, String def) {
        return o == null ? def : o.optString(key, def);
    }

    /* the entry in force at this moment: the last one that has begun, else the first */
    JSONArray cur(String key, long now) {
        JSONArray a = o == null ? null : o.optJSONArray(key);
        if (a == null || a.length() == 0) return null;
        JSONArray pick = a.optJSONArray(0);
        for (int i = 1; i < a.length(); i++) {
            JSONArray e = a.optJSONArray(i);
            if (e == null) continue;
            if (e.optLong(0) <= now) pick = e;
            else break;
        }
        return pick;
    }

    /* the next moment one of these lines changes */
    long next(long now, String... keys) {
        long n = Long.MAX_VALUE;
        if (o == null) return n;
        for (String k : keys) {
            JSONArray a = o.optJSONArray(k);
            if (a == null) continue;
            for (int i = 0; i < a.length(); i++) {
                JSONArray e = a.optJSONArray(i);
                if (e == null) continue;
                long t = e.optLong(0);
                if (t > now) {
                    if (t < n) n = t;
                    break;
                }
            }
        }
        return n;
    }

    static String at(JSONArray e, int i) {
        return e == null ? "" : e.optString(i, "");
    }
}
