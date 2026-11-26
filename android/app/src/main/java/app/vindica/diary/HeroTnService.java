package app.vindica.diary;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONObject;

/* TEMPVS NOSTRVM on the home screen: the free hours left fall slower than the clock (only the hours that are truly
   one's own count), so the widget cannot use a plain countdown. Its line is a list turned over once a second by
   the home screen itself: entry p is the value one second after entry p−1. From Android 12 the widget hands the
   list over itself, a few minutes of it at a time; before that this service serves it from the page's anchor on. */
public class HeroTnService extends RemoteViewsService {
    static final int COUNT = 2500000;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        Uri u = intent.getData();
        String path = u != null ? u.getPath() : null;
        return new Factory(getApplicationContext(), path != null && path.endsWith("/1"));
    }

    private static String two(long x) {
        return x < 10 ? "0" + x : String.valueOf(x);
    }

    /* 135 670:57:19, the thousands parted as on the page */
    static String fmt(double c, String sep) {
        long t = Math.max(0, (long) Math.floor(c)), h = t / 3600;
        String hs = String.valueOf(h);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < hs.length(); i++) {
            if (i > 0 && (hs.length() - i) % 3 == 0) b.append(sep);
            b.append(hs.charAt(i));
        }
        return b + ":" + two(t % 3600 / 60) + ":" + two(t % 60);
    }

    static RemoteViews item(Context ctx, String text, int color) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_hero_tn);
        rv.setTextViewText(R.id.hero_tn_t, text);
        rv.setTextColor(R.id.hero_tn_t, color);
        rv.setOnClickFillInIntent(R.id.hero_tn_t, new Intent());
        return rv;
    }

    static final class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private final boolean dark;
        private double v, r;
        private String sep = " ";
        private int color;

        Factory(Context ctx, boolean dark) {
            this.ctx = ctx;
            this.dark = dark;
            color = new HeroMath.Ink(dark).ink;
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDataSetChanged() {
            HeroData d = HeroData.load(ctx);
            JSONObject tn = d.has() ? d.o.optJSONObject("tn") : null;
            v = tn == null ? 0 : tn.optDouble("v", 0);
            r = tn == null ? 0 : tn.optDouble("r", 0);
            sep = tn == null ? " " : tn.optString("sep", " ");
            color = new HeroMath.Ink(dark).ink;
        }

        @Override
        public void onDestroy() {
        }

        @Override
        public int getCount() {
            return v > 0 ? COUNT : 0;
        }

        @Override
        public RemoteViews getViewAt(int p) {
            return item(ctx, fmt(v - r * p, sep), color);
        }

        @Override
        public RemoteViews getLoadingView() {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_hero_tn);
            rv.setTextViewText(R.id.hero_tn_t, "");
            return rv;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int p) {
            return p;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
