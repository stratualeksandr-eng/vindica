package app.vindica.diary;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/* The live wallpaper's picture: the sky of «Сегодня» full screen with everything the first page counts. The date and
   day of life, the greeting, Memento mori with TEMPVS NOSTRVM and PROTINVS VIVE, the hour and the weather, the sundial
   (its back counts down to lights out), the card of the place (the town, its landmarks by name, the weather with its
   sign, the landmark drawn in a band), what is left of a life to eighty, Seneca and the meander. The still parts
   are drawn once a minute into one picture; the counters, and the back of the dial when it is turned, every second. */
final class SkyScene {
    /* the sundial shows its back, the countdown to lights out */
    boolean flipped;
    /* the life total in years (0), weeks (1) or hours (2) */
    int unit;
    /* a tap on the weather asked for it anew */
    boolean wantWeather;
    final RectF rDial = new RectF(), rHour = new RectF(), rLife = new RectF(), rCard = new RectF();

    private Bitmap base;
    private String baseKey = "";
    private Typeface forum, lit, litI, litB;
    private float u, colR, tnY, slY, wkY, dialL, dialT, dialS;
    private boolean dark, day;
    private HeroMath.Ink ink;
    /* the sky as last drawn: its three colours (the card's picture takes them), the lowest one and the night's depth */
    private int[] skyCol = {0xFF1A2034, 0xFF1A2034, 0xFF2C2421};
    private int landC2;
    private double landNight;

    private static final int[] ON = {HeroMath.hex("#191C24"), HeroMath.hex("#1F2229"), HeroMath.hex("#292625")},
        LO = {HeroMath.hex("#D9DDE0"), HeroMath.hex("#E2E5E7"), HeroMath.hex("#EBECED")};
    private static final String[] R5 = {"LX", "V", "X", "XV", "XX", "XXV", "XXX", "XXXV", "XL", "XLV", "L", "LV"};

    void invalidate() {
        baseKey = "";
    }

    /* a tap: on the dial it turns over, on the life total it changes years for weeks and hours, on the hour it asks
       for the weather; true when the picture changes */
    boolean tap(float x, float y) {
        if (rDial.contains(x, y)) {
            flipped = !flipped;
            baseKey = "";
            return true;
        }
        if (rLife.contains(x, y)) {
            unit = (unit + 1) % 3;
            baseKey = "";
            return true;
        }
        if (rHour.contains(x, y) || rCard.contains(x, y)) {
            wantWeather = true;
            return true;
        }
        return false;
    }

    private void fonts(Context ctx) {
        if (forum != null) return;
        forum = HeroDraw.forum(ctx);
        lit = litI = litB = Typeface.SERIF;
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                lit = ctx.getResources().getFont(R.font.literata);
                litI = ctx.getResources().getFont(R.font.literata_italic);
                litB = ctx.getResources().getFont(R.font.literata_semibold);
            } catch (Exception e) {
                lit = litI = litB = Typeface.SERIF;
            }
        }
    }

    private TextPaint tp(Typeface tf, float size, int color, float ls) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        if (ls != 0) p.setLetterSpacing(ls);
        if (!day) p.setShadowLayer(5 * u, 0, 0, dark ? 0xCC060A16 : 0xD9FFFDF8);
        return p;
    }

    private static StaticLayout lay(CharSequence s, TextPaint p, int w, Layout.Alignment al, float mult, int maxLines) {
        StaticLayout.Builder b = StaticLayout.Builder.obtain(s, 0, s.length(), p, Math.max(1, w)).setAlignment(al).setLineSpacing(0, mult).setIncludePad(false);
        if (maxLines > 0) b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END);
        return b.build();
    }

    /* right-aligned, each digit in a cell of its own so the ticking numbers do not shiver; returns the left edge */
    private static float cells(Canvas c, String s, float right, float base, Paint p) {
        float cw = p.getTextSize() * .56f, x = right;
        Paint.Align al = p.getTextAlign();
        for (int i = s.length() - 1; i >= 0; i--) {
            String ch = s.substring(i, i + 1);
            boolean dg = Character.isDigit(s.charAt(i));
            float w = dg ? cw : p.measureText(ch);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(ch, x - w / 2, base, p);
            x -= w;
        }
        p.setTextAlign(al);
        return x;
    }

    private static String hm(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    /* «6:59:49», as PROTINVS VIVE counts on the page */
    private static String clock(long ms) {
        long t = Math.max(0, ms / 1000);
        return (t / 3600) + ":" + (t % 3600 / 60 < 10 ? "0" : "") + (t % 3600 / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60);
    }

    void draw(Context ctx, Canvas c, int w, int h, long now) {
        fonts(ctx);
        HeroPlace.load(ctx);
        HeroData d = HeroData.load(ctx);
        TimeZone z = TimeZone.getDefault();
        day = HeroMath.isDay(now, z);
        String look = d.str("look", "sun");
        boolean sys = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dark = "dark".equals(look) || ("auto".equals(look) ? sys : !"day".equals(look) && !day);
        ink = new HeroMath.Ink(dark);
        String lang = d.str("lang", Locale.getDefault().getLanguage());
        String wx = HeroWeather.text(ctx, lang, now);
        double cover = HeroWeather.cover(ctx, now);
        String key = w + "x" + h + "|" + (now / 60000) + "|" + dark + "|" + day + "|" + flipped + "|" + unit + "|" + System.identityHashCode(d) + "|" + wx + "|" + cover + "|" + HeroPlace.key(ctx);
        if (base == null || base.getWidth() != w || base.getHeight() != h) {
            base = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
            baseKey = "";
        }
        if (!key.equals(baseKey)) {
            base.eraseColor(0);
            paintBase(ctx, new Canvas(base), w, h, now, z, d, lang, wx, cover);
            baseKey = key;
        }
        c.drawBitmap(base, 0, 0, null);
        paintLive(c, now, d);
    }

    /* the sky: its colours by the sun's altitude, greyer under cloud, the stars by night, the sun's glow, the moon */
    private void sky(Context ctx, Canvas c, int w, int h, long now, TimeZone z, double cover) {
        HeroMath.Sky k = HeroMath.sky(now, z, dark);
        int[] col = k.c.clone();
        double st = k.stars, cv = HeroMath.cl(cover, 0, 1);
        if (cv > 0) {
            double t = Math.pow(cv, 1.3) * (dark ? .6 : .85);
            int[] o = dark ? ON : LO;
            for (int i = 0; i < 3; i++) col[i] = HeroMath.mix(col[i], o[i], t);
            st *= 1 - .45 * cv;
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, col, new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        if (st > .01) {
            float sc = Math.max(w / 400f, h / 900f), ox = (w - 400 * sc) / 2, oy = (h - 900 * sc) / 2;
            int star = HeroMath.hex("#FFF3DA");
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (double[] s : HeroMath.stars()) {
                float x = (float) (ox + s[0] * sc), y = (float) (oy + s[1] * sc), r = (float) (s[2] * sc) * .8f;
                /* a soft glow fading out, then the point of light */
                g.setShader(new RadialGradient(x, y, r * 3.2f, new int[]{HeroMath.alpha(star, s[3] * st * .38), HeroMath.alpha(star, 0)}, null, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, r * 3.2f, g);
                p.setColor(HeroMath.alpha(star, s[3] * st));
                c.drawCircle(x, y, r, p);
            }
        }
        HeroMath.Body sun = HeroMath.sunSky(now, z, dark);
        double so = sun.o * (1 - .85 * cv);
        if (so > .01) {
            float cx = (float) (sun.x / 100 * w), cy = (float) (sun.y / 100 * h), r = 85 * u;
            int core = HeroMath.mix(HeroMath.hex("#FFF3C9"), HeroMath.hex("#FFC98E"), sun.warm),
                glow = HeroMath.mix(HeroMath.hex("#FFD777"), HeroMath.hex("#FF8F55"), sun.warm),
                hot = HeroMath.mix(HeroMath.hex("#FFFEF6"), HeroMath.hex("#FFF0D6"), sun.warm);
            int[] cols = {hot, hot, HeroMath.alpha(core, .96), HeroMath.alpha(glow, .62), HeroMath.alpha(glow, .3), HeroMath.alpha(glow, .1), HeroMath.alpha(glow, 0)};
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            g.setShader(new RadialGradient(cx, cy, r, cols, new float[]{0f, .075f, .11f, .17f, .3f, .55f, 1f}, Shader.TileMode.CLAMP));
            g.setAlpha((int) Math.round(HeroMath.cl(so, 0, 1) * 255));
            c.drawCircle(cx, cy, r, g);
        }
        HeroMath.Body moon = HeroMath.moonSky(now, z, dark);
        double mo = moon.o * (1 - .85 * cv);
        if (mo > .25) {
            int px = Math.max(8, Math.round(38 * u));
            Bitmap m = HeroDraw.moon(px, moon.phase, dark);
            Paint q = new Paint(Paint.FILTER_BITMAP_FLAG);
            q.setAlpha((int) Math.round(HeroMath.cl(mo, 0, 1) * 255));
            c.drawBitmap(m, (float) (moon.x / 100 * w - px / 2.0), (float) (moon.y / 100 * h - px / 2.0), q);
        }
        skyCol = col.clone();
        landC2 = col[2];
        landNight = HeroMath.cl((-3 - HeroMath.sunAlt(HeroMath.sunPos(now, z))) / 5, 0, 1);
    }

    private float mzTopOf(int h) {
        return h - Math.max(20 * u, h * .022f) - 12 * u;
    }

    private int tone(String t) {
        int c2 = landC2, K = 0xFF000000;
        double nk = landNight;
        if (dark) {
            switch (t) {
                case "far": return HeroMath.mix(c2, K, .32);
                case "mid": return HeroMath.mix(c2, K, .52);
                case "near": return HeroMath.mix(c2, K, .7);
                case "det": return HeroMath.mix(c2, K, .55);
                case "hero": return HeroMath.mix(HeroMath.mix(c2, K, .62), HeroMath.hex("#8C97A8"), nk);
                case "lit": return HeroMath.alpha(HeroMath.hex("#F6ECD6"), .7 + .25 * nk);
                case "win": return HeroMath.alpha(HeroMath.hex("#F2C46B"), .85 * nk);
                default: return 0;
            }
        }
        switch (t) {
            case "far": return HeroMath.mix(c2, HeroMath.hex("#5E7186"), .24);
            case "mid": return HeroMath.mix(c2, HeroMath.hex("#46566A"), .4);
            case "near": return HeroMath.mix(c2, HeroMath.hex("#353F4C"), .52);
            case "det": return HeroMath.mix(c2, HeroMath.hex("#46566A"), .44);
            case "hero": return HeroMath.mix(c2, HeroMath.hex("#2A323C"), .7);
            case "lit": return HeroMath.alpha(0xFFFFFFFF, .96);
            default: return 0;
        }
    }

    private static String low1(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }

    private static String cur(HeroData d, String key, long now, int i) {
        JSONArray e = d.cur(key, now);
        return e == null ? "" : e.optString(i, "");
    }

    private void paintBase(Context ctx, Canvas c, int w, int h, long now, TimeZone z, HeroData d, String lang, String wx, double cover) {
        u = Math.min(w / 390f, h / 830f);
        float cw = 350 * u, x0 = (w - 390 * u) / 2 + 20 * u, x1 = x0 + cw;
        boolean ro = "ro".equals(lang);
        HeroMath.Ink k = ink;
        sky(ctx, c, w, h, now, z, cover);

        /* the date and the day of life */
        float y = Math.max(44 * u, h * .045f);
        String date = d.has() ? cur(d, "date", now, 1) : HeroWidget.fallbackDate(now, ro);
        StaticLayout dl = lay(date, tp(lit, 15 * u, k.mut, 0), (int) cw, Layout.Alignment.ALIGN_NORMAL, 1.1f, 2);
        c.save();
        c.translate(x0, y);
        dl.draw(c);
        c.restore();
        y += dl.getHeight() + 6 * u;

        /* the greeting, and beside it Memento mori with its counters */
        JSONArray mm = d.cur("mm", now);
        float colW = mm != null ? 130 * u : 0, gw = cw - (mm != null ? colW + 10 * u : 0);
        String l1, l2;
        if (d.has()) {
            l1 = cur(d, "hello", now, 1);
            l2 = cur(d, "hello", now, 2);
        } else {
            l1 = HeroWidget.fallbackHello(ctx, now);
            l2 = "";
        }
        float F = Math.min(Math.max(36, 39), (350 - (mm != null ? 140 : 0)) / (ro ? 6.5f : 6.12f)) * u;
        if (mm == null) F = 39 * u;
        TextPaint gp = tp(forum, F, k.ink, 0);
        float tw = Math.max(gp.measureText(l1), l2.isEmpty() ? 0 : gp.measureText(l2));
        if (tw > gw) {
            F *= gw / tw * .98f;
            gp.setTextSize(F);
        }
        Paint.FontMetrics fm = gp.getFontMetrics();
        float L = F * 1.08f, base1 = y + (L - (fm.descent - fm.ascent)) / 2 - fm.ascent;
        c.drawText(l1, x0, base1, gp);
        if (!l2.isEmpty()) c.drawText(l2, x0, base1 + L, gp);
        float gBottom = y + L * (l2.isEmpty() ? 1 : 2);

        float cy = y + 4 * u;
        colR = x1;
        if (mm != null) {
            JSONObject lab = d.o.optJSONObject("lab");
            TextPaint lp = tp(forum, 9.5f * u, k.brz, .36f);
            lp.setTextAlign(Paint.Align.RIGHT);
            float lsTrail = 9.5f * u * .36f / 2;
            cy += 9 * u;
            c.drawText(lab != null ? lab.optString("mm", "MEMENTO MORI") : "MEMENTO MORI", x1 + lsTrail, cy, lp);
            String n = mm.optString(1, ""), un = mm.optString(2, "");
            TextPaint np = tp(forum, 24 * u, k.ink, 0), up = tp(lit, 12 * u, k.mut, 0);
            np.setTextAlign(Paint.Align.RIGHT);
            up.setTextAlign(Paint.Align.RIGHT);
            cy += 25 * u;
            float ux = x1;
            if (!un.isEmpty()) {
                String uu = TextUtils.ellipsize(un, up, n.isEmpty() ? colW : colW - np.measureText(n) - 6 * u, TextUtils.TruncateAt.END).toString();
                c.drawText(uu, ux, cy, up);
                ux -= up.measureText(uu) + 6 * u;
            }
            if (!n.isEmpty()) c.drawText(n, ux, cy, np);
            double fr = mm.optDouble(3, -1);
            cy += 7 * u;
            if (fr >= 0) {
                Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
                b.setColor(HeroMath.alpha(k.ink, .16));
                c.drawRect(x1 - colW, cy, x1, cy + 2 * u, b);
                b.setColor(k.brz);
                c.drawRect(x1 - colW, cy, (float) (x1 - colW + colW * HeroMath.cl(fr, 0, 1)), cy + 2 * u, b);
            }
            cy += 2 * u;
            JSONObject tn = d.o.optJSONObject("tn");
            if (tn != null && tn.optDouble("v", 0) > 0) {
                tnY = cy + 21 * u;
                cy = tnY + 13 * u;
                c.drawText(lab != null ? lab.optString("tn", "TEMPVS NOSTRVM") : "TEMPVS NOSTRVM", x1 + lsTrail, cy, lp);
            } else tnY = 0;
            slY = d.cur("sl", now) != null ? (cy += 22 * u) : 0;
            JSONArray wk = d.cur("wk", now);
            wkY = wk != null && !wk.optString(1, "").isEmpty() ? (cy += 20 * u) : 0;
            if (slY > 0) {
                cy += 14 * u;
                c.drawText(lab != null ? lab.optString("pv", "PROTINVS VIVE") : "PROTINVS VIVE", x1 + lsTrail, cy, lp);
            }
            cy += 4 * u;
        } else {
            tnY = slY = wkY = 0;
        }
        y = Math.max(gBottom, cy) + 10 * u;

        /* the hour: the night watch or the sun's own time, and the weather after it */
        String hour = cur(d, "hour", now, 1).replace("{S}", HeroMath.fmtM(HeroMath.sunPos(now, z).tst));
        String town = HeroPlace.name(ctx, lang);
        boolean place = !town.isEmpty() || HeroWeather.now(ctx, now) != null;
        rHour.setEmpty();
        if (!hour.isEmpty()) {
            StaticLayout hl = lay(hour, tp(lit, 15 * u, k.mut, 0), (int) cw, Layout.Alignment.ALIGN_NORMAL, 1.15f, 3);
            c.save();
            c.translate(x0, y);
            hl.draw(c);
            c.restore();
            rHour.set(x0, y - 8 * u, x1, y + hl.getHeight() + 8 * u);
            y += hl.getHeight() + 10 * u;
        }

        /* below: the life left, Seneca, the meander; the dial takes the room in between */
        float mzTop = mzTopOf(h);
        JSONObject life = d.has() ? d.o.optJSONObject("life") : null;
        JSONObject lab = d.has() ? d.o.optJSONObject("lab") : null;
        JSONArray motto = d.has() ? d.o.optJSONArray("motto") : null;
        String la1 = motto != null ? motto.optString(0) : "OMNIA ALIENA SVNT", la2 = motto != null ? motto.optString(1) : "TEMPVS TANTVM NOSTRVM EST",
            tr = motto != null ? motto.optString(2) : ctx.getString(R.string.hero_motto);
        TextPaint mp = tp(forum, 13 * u, k.ink, .14f), ip = tp(litI, 13.5f * u, k.mut, 0);
        mp.setTextAlign(Paint.Align.CENTER);
        StaticLayout tl = lay(tr, ip, (int) cw, Layout.Alignment.ALIGN_CENTER, 1.1f, 2);
        float mottoH = 13 * u * 1.35f * 2 + 4 * u + tl.getHeight();

        Life lv = life == null ? null : new Life(life, lab, cw, k);
        float gap = 14 * u, minDial = 190 * u;
        Card card = place ? new Card(ctx, lang, cw, true, k, now, z) : null;
        boolean mottoOn = true;
        float room = mzTop - gap - y - (card != null ? card.h + gap : 0) - (lv != null ? lv.h + gap : 0) - mottoH - gap;
        if (room < minDial * .86f) {
            mottoOn = false;
            room += mottoH + gap;
        }
        if (room < minDial * .86f && lv != null && lv.capOn) {
            float was = lv.h;
            lv = new Life(life, lab, cw, k, false);
            room += was - lv.h;
        }
        if (room < minDial * .86f && card != null && card.pic) {
            float was = card.h;
            card = new Card(ctx, lang, cw, false, k, now, z);
            room += was - card.h;
        }
        if (room < minDial * .86f && lv != null) {
            room += lv.h + gap;
            lv = null;
        }
        float dw = Math.max(120 * u, Math.min(Math.min(340 * u, cw), room / .86f)), dh = dw * .86f;
        dialL = (w - dw) / 2;
        dialT = y;
        dialS = dw / 400f;
        rDial.set(dialL, dialT, dialL + dw, dialT + dh);
        if (!flipped) {
            Bitmap db = HeroDraw.dialCached(ctx, Math.max(4, Math.round(dw)), now, z, dark, day);
            c.drawBitmap(db, dialL, dialT, new Paint(Paint.FILTER_BITMAP_FLAG));
        }
        y += dh + gap;

        /* the place: the town, its landmarks, the weather; tapping it asks the weather again */
        rCard.setEmpty();
        if (card != null) {
            card.draw(c, x0, y, k);
            rCard.set(x0, y, x0 + cw, y + card.h);
            y += card.h + gap;
        }

        /* what is left of a life: the total (years, weeks or hours by turns), what takes the rest, the bar */
        rLife.setEmpty();
        if (lv != null) {
            float y0 = y;
            lv.draw(c, w / 2f, x0, y, unit);
            y += lv.h + gap;
            rLife.set(x0, y0 - 6 * u, x1, y - gap + 6 * u);
        }

        /* Seneca */
        if (mottoOn) {
            float b = y + 13 * u;
            c.drawText(la1, w / 2f, b, mp);
            c.drawText(la2, w / 2f, b + 13 * u * 1.35f, mp);
            c.save();
            c.translate(x0, b + 13 * u * 1.35f + 4 * u + 13.5f * u * .35f);
            tl.draw(c);
            c.restore();
        }

        /* the bronze meander along the bottom */
        Paint mz = new Paint(Paint.ANTI_ALIAS_FLAG);
        mz.setStyle(Paint.Style.STROKE);
        mz.setStrokeWidth(1.3f * u);
        mz.setColor(k.brz);
        Path p = new Path();
        float tw20 = 20 * u;
        for (float x = 0; x < w + tw20; x += tw20) {
            p.moveTo(x, mzTop + 10.5f * u);
            p.lineTo(x + tw20, mzTop + 10.5f * u);
            p.moveTo(x + 2 * u, mzTop + 10.5f * u);
            p.lineTo(x + 2 * u, mzTop + 1.5f * u);
            p.lineTo(x + 17 * u, mzTop + 1.5f * u);
            p.lineTo(x + 17 * u, mzTop + 7.5f * u);
            p.lineTo(x + 6 * u, mzTop + 7.5f * u);
            p.lineTo(x + 6 * u, mzTop + 4.5f * u);
            p.lineTo(x + 13 * u, mzTop + 4.5f * u);
        }
        c.drawPath(p, mz);
    }

    /* the card of the place: the town and its landmarks by name, the weather's sign, the temperature and the weather in
       words; on the right, in a picture of the sky's own colours, the landmark itself. Without room (or without a
       drawing of the town) the card keeps only its words, the weather then on the right */
    private final class Card {
        final float w, h, pw;
        final boolean pic;
        final StaticLayout cityL, sightsL, phL;
        final String temp;
        final int icon;
        final TextPaint tpT;
        final HeroPlace.Art art;
        final float[] box;

        Card(Context ctx, String lang, float w, boolean withPic, HeroMath.Ink k, long now, TimeZone z) {
            this.w = w;
            boolean ro = "ro".equals(lang), night = HeroMath.sunAlt(HeroMath.sunPos(now, z)) < -.833;
            HeroWeather.Now n = HeroWeather.now(ctx, now);
            String ph = "";
            if (n != null) {
                long t = Math.round(n.t);
                temp = (t > 0 ? "+" : t < 0 ? "−" : "") + Math.abs(t) + "°";
                ph = low1(HeroWeather.phrase(n.code, ro));
                icon = sign(n.code, night);
            } else {
                temp = "";
                icon = -1;
            }
            HeroPlace.City city = HeroPlace.city(ctx);
            pic = withPic && city != null;
            art = HeroPlace.art(ctx);
            box = HeroPlace.box(ctx);
            pw = pic ? Math.round(w * .46f) : 0;
            String town = HeroPlace.name(ctx, lang), sights = HeroPlace.sights(ctx, lang);
            float tw = (pic ? w - pw - 28 * u : w - 32 * u);
            tpT = quiet(tp(forum, (pic ? 28 : 34) * u, k.ink, 0));
            float tempW = temp.isEmpty() ? 0 : tpT.measureText(temp), iconS = (pic ? 26 : 32) * u;
            if (pic) {
                /* words on the left: the town, the landmarks, then a row with the weather */
                float rest = tw - (icon >= 0 ? iconS + 8 * u : 0) - (tempW > 0 ? tempW + 8 * u : 0);
                phL = ph.isEmpty() ? null : lay(ph, quiet(tp(lit, 12.5f * u, k.mut, 0)), (int) Math.max(40 * u, rest), Layout.Alignment.ALIGN_NORMAL, 1.15f, 2);
                cityL = lay(town, quiet(tp(forum, 27 * u, k.ink, 0)), (int) tw, Layout.Alignment.ALIGN_NORMAL, 1.05f, 1);
                sightsL = sights.isEmpty() ? null : lay(sights.toUpperCase(Locale.forLanguageTag(ro ? "ro" : "ru")), quiet(tp(forum, 10 * u, k.brz, .14f)), (int) tw, Layout.Alignment.ALIGN_NORMAL, 1.25f, 2);
                float th = cityL.getHeight() + (sightsL != null ? 5 * u + sightsL.getHeight() : 0) + (rowH(iconS) > 0 ? 10 * u + rowH(iconS) : 0);
                h = Math.max(th + 26 * u, 104 * u);
            } else {
                /* words on the left, the weather on the right */
                phL = ph.isEmpty() ? null : lay(ph, quiet(tp(lit, 13 * u, k.mut, 0)), (int) (128 * u), Layout.Alignment.ALIGN_OPPOSITE, 1.2f, 2);
                float pwid = 0;
                if (phL != null) for (int i = 0; i < phL.getLineCount(); i++) pwid = Math.max(pwid, phL.getLineWidth(i));
                float rightW = icon < 0 && temp.isEmpty() ? 0 : iconS + 8 * u + Math.max(tempW, pwid);
                float lw = tw - rightW - (rightW > 0 ? 10 * u : 0);
                cityL = lay(town, quiet(tp(forum, 30 * u, k.ink, 0)), (int) lw, Layout.Alignment.ALIGN_NORMAL, 1.05f, 1);
                sightsL = sights.isEmpty() ? null : lay(sights.toUpperCase(Locale.forLanguageTag(ro ? "ro" : "ru")), quiet(tp(forum, 11 * u, k.brz, .15f)), (int) lw, Layout.Alignment.ALIGN_NORMAL, 1.25f, 2);
                float lh = cityL.getHeight() + (sightsL != null ? 6 * u + sightsL.getHeight() : 0);
                float rh = Math.max(icon >= 0 ? iconS : 0, tempH() + (phL != null ? 3 * u + phL.getHeight() : 0));
                h = 14 * u + Math.max(lh, rh) + 12 * u;
            }
        }

        private float tempH() {
            if (temp.isEmpty()) return 0;
            Paint.FontMetrics fm = tpT.getFontMetrics();
            return -fm.ascent + fm.descent * .3f;
        }

        private float rowH(float iconS) {
            float r = Math.max(icon >= 0 ? iconS : 0, tempH());
            return Math.max(r, phL != null ? phL.getHeight() : 0);
        }

        void draw(Canvas c, float x, float y, HeroMath.Ink k) {
            float r = 18 * u, lw = Math.max(1, .6f * u);
            int gline = dark ? 0x26ECE4D6 : 0x262E2216;
            RectF card = new RectF(x, y, x + w, y + h);
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setColor(dark ? 0x57181820 : 0x73FCFAF6);
            c.drawRoundRect(card, r, r, g);
            if (pic && art != null) {
                /* the picture: the sky's colours, the landmark fitted whole and standing on the picture's bottom */
                float px = x + w - pw;
                c.save();
                Path clip = new Path();
                clip.addRoundRect(card, r, r, Path.Direction.CW);
                c.clipPath(clip);
                c.clipRect(px, y, x + w, y + h);
                Paint sp = new Paint(Paint.DITHER_FLAG);
                sp.setShader(new LinearGradient(0, y, 0, y + h, skyCol, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(px, y, x + w, y + h, sp);
                float bw = box[1] - box[0], bh = box[3] - box[2], pad = 8 * u;
                float s = Math.min(pw / bw, (h - pad) / bh), cx = (box[0] + box[1]) / 2;
                Matrix m = new Matrix();
                m.setTranslate(-cx, -box[3]);
                m.postScale(s, s);
                m.postTranslate(px + pw / 2, y + h);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                Path q = new Path();
                for (int i = 0; i < art.paths.size(); i++) {
                    int col = cardTone(art.tones.get(i));
                    if (Color.alpha(col) == 0) continue;
                    p.setColor(col);
                    art.paths.get(i).transform(m, q);
                    c.drawPath(q, p);
                }
                c.restore();
                Paint ln = new Paint(Paint.ANTI_ALIAS_FLAG);
                ln.setColor(gline);
                ln.setStrokeWidth(lw);
                c.drawLine(px, y, px, y + h, ln);
            }
            Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
            b.setStyle(Paint.Style.STROKE);
            b.setStrokeWidth(lw);
            b.setColor(gline);
            c.drawRoundRect(card, r, r, b);
            float lx = x + 16 * u;
            if (pic) {
                float iconS = 26 * u, rh = rowH(iconS);
                float th = cityL.getHeight() + (sightsL != null ? 5 * u + sightsL.getHeight() : 0) + (rh > 0 ? 10 * u + rh : 0);
                float ty = y + (h - th) / 2;
                c.save();
                c.translate(lx, ty);
                cityL.draw(c);
                c.restore();
                ty += cityL.getHeight();
                if (sightsL != null) {
                    c.save();
                    c.translate(lx, ty + 5 * u);
                    sightsL.draw(c);
                    c.restore();
                    ty += 5 * u + sightsL.getHeight();
                }
                if (rh > 0) {
                    ty += 10 * u;
                    float rx = lx;
                    if (icon >= 0) {
                        sign(c, icon, rx, ty + (rh - iconS) / 2, iconS, k.ink);
                        rx += iconS + 8 * u;
                    }
                    if (!temp.isEmpty()) {
                        tpT.setTextAlign(Paint.Align.LEFT);
                        c.drawText(temp, rx, ty + (rh - tempH()) / 2 - tpT.getFontMetrics().ascent, tpT);
                        rx += tpT.measureText(temp) + 8 * u;
                    }
                    if (phL != null) {
                        c.save();
                        c.translate(rx, ty + (rh - phL.getHeight()) / 2);
                        phL.draw(c);
                        c.restore();
                    }
                }
                return;
            }
            float top = y + 14 * u, inner = h - 26 * u, iconS = 32 * u;
            float lh = cityL.getHeight() + (sightsL != null ? 6 * u + sightsL.getHeight() : 0), ly = top + (inner - lh) / 2;
            c.save();
            c.translate(lx, ly);
            cityL.draw(c);
            c.restore();
            if (sightsL != null) {
                c.save();
                c.translate(lx, ly + cityL.getHeight() + 6 * u);
                sightsL.draw(c);
                c.restore();
            }
            float rx = x + w - 16 * u, rh = tempH() + (phL != null ? 3 * u + phL.getHeight() : 0), cy = top + (inner - rh) / 2;
            float colW = Math.max(temp.isEmpty() ? 0 : tpT.measureText(temp), phL != null ? phL.getWidth() : 0);
            if (phL != null) {
                float pwid = 0;
                for (int i = 0; i < phL.getLineCount(); i++) pwid = Math.max(pwid, phL.getLineWidth(i));
                colW = Math.max(temp.isEmpty() ? 0 : tpT.measureText(temp), pwid);
            }
            if (icon >= 0) sign(c, icon, rx - colW - 8 * u - iconS, top + (inner - iconS) / 2, iconS, k.ink);
            if (!temp.isEmpty()) {
                tpT.setTextAlign(Paint.Align.RIGHT);
                c.drawText(temp, rx, cy - tpT.getFontMetrics().ascent, tpT);
                cy += tempH();
            }
            if (phL != null) {
                c.save();
                c.translate(rx - phL.getWidth(), cy + 3 * u);
                phL.draw(c);
                c.restore();
            }
        }
    }

    /* text on the card's glass needs no halo */
    private static TextPaint quiet(TextPaint p) {
        p.setShadowLayer(0, 0, 0, 0);
        return p;
    }

    /* the picture's tones are a step darker than the sky's horizon would have them, so the landmark stands out */
    private int cardTone(String t) {
        switch (t) {
            case "far": return tone("mid");
            case "mid": return tone("near");
            case "near": return HeroMath.mix(tone("near"), 0xFF000000, .26);
            case "det": return tone("mid");
            default: return tone(t);
        }
    }

    /* the weather's sign for a WMO code: 0 cloud, 1 fog, 2 rain, 3 snow, 4 sun, 5 moon */
    private static int sign(int code, boolean night) {
        if (code <= 1) return night ? 5 : 4;
        if (code == 45 || code == 48) return 1;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return 3;
        if (code >= 51) return 2;
        return 0;
    }

    /* the signs, drawn as the app draws its icons: thin bronze-ink strokes on a 24-unit square */
    private static void sign(Canvas c, int icon, float x, float y, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.35f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        c.save();
        c.translate(x, y);
        c.scale(size / 24f, size / 24f);
        if (icon == 4) {
            c.drawCircle(12, 12, 4, p);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine((float) (12 + 6.5 * Math.cos(a)), (float) (12 + 6.5 * Math.sin(a)), (float) (12 + 8.6 * Math.cos(a)), (float) (12 + 8.6 * Math.sin(a)), p);
            }
        } else if (icon == 5) {
            Path a = new Path(), b = new Path();
            a.addCircle(11, 12.5f, 7.2f, Path.Direction.CW);
            b.addCircle(15.6f, 9.2f, 6.2f, Path.Direction.CW);
            a.op(b, Path.Op.DIFFERENCE);
            c.drawPath(a, p);
        } else {
            float dy = icon == 0 ? .5f : icon == 1 ? -4f : -3f;
            Path cl = new Path(), t = new Path();
            cl.addCircle(8.6f, 13.6f + dy, 4.2f, Path.Direction.CW);
            t.addCircle(13f, 10.6f + dy, 5.4f, Path.Direction.CW);
            cl.op(t, Path.Op.UNION);
            t.reset();
            t.addCircle(17.4f, 14.4f + dy, 3.6f, Path.Direction.CW);
            cl.op(t, Path.Op.UNION);
            t.reset();
            t.addRect(8.6f, 13.6f + dy, 17.4f, 18f + dy, Path.Direction.CW);
            cl.op(t, Path.Op.UNION);
            c.drawPath(cl, p);
            if (icon == 1) {
                c.drawLine(4, 17, 20, 17, p);
                c.drawLine(6, 20.5f, 18, 20.5f, p);
            } else if (icon == 2) {
                c.drawLine(8, 17.5f, 7, 20.5f, p);
                c.drawLine(12, 17.5f, 11, 20.5f, p);
                c.drawLine(16, 17.5f, 15, 20.5f, p);
            } else if (icon == 3) {
                Paint d = new Paint(p);
                d.setStyle(Paint.Style.FILL);
                c.drawCircle(8, 18.8f, .9f, d);
                c.drawCircle(12, 20.4f, .9f, d);
                c.drawCircle(16, 18.8f, .9f, d);
            }
        }
        c.restore();
    }

    /* the life panel, measured once and drawn centred */
    private final class Life {
        final float h, cw;
        final JSONObject o;
        final StaticLayout cap;
        final boolean capOn;
        final String head;
        final HeroMath.Ink k;
        final ArrayList<float[]> rows = new ArrayList<>();

        Life(JSONObject o, JSONObject lab, float cw, HeroMath.Ink k) {
            this(o, lab, cw, k, true);
        }

        Life(JSONObject o, JSONObject lab, float cw, HeroMath.Ink k, boolean capOn) {
            this.capOn = capOn;
            this.o = o;
            this.cw = cw;
            this.k = k;
            head = lab != null ? lab.optString("vita", "VITA SI VTI SCIAS LONGA EST") : "VITA SI VTI SCIAS LONGA EST";
            cap = lay(o.optString("cap", ""), tp(lit, 13 * u, k.mut, 0), (int) cw, Layout.Alignment.ALIGN_CENTER, 1.12f, 3);
            JSONArray cats = o.optJSONArray("cats");
            int lines = legend(null, 0, 0, cats);
            h = 14 * u + 52 * u + (capOn ? cap.getHeight() + 10 * u : 0) + 10 * u + 10 * u + lines * 19 * u;
        }

        int color(String key) {
            switch (key) {
                case "s":
                    return HeroMath.hex(dark ? "#99AFE0" : "#2A4677");
                case "w":
                    return k.red;
                case "c":
                    return HeroMath.mix(k.red, HeroMath.hex(dark ? "#3E3730" : "#CEC4B5"), .58);
                case "b":
                    return HeroMath.mix(k.mut, HeroMath.hex(dark ? "#3E3730" : "#CEC4B5"), .38);
                default:
                    return k.brz;
            }
        }

        /* the legend flows in centred lines; with c null it only counts them */
        int legend(Canvas c, float cx, float y, JSONArray cats) {
            if (cats == null) return 0;
            TextPaint p = tp(lit, 12 * u, k.mut, 0), pf = tp(litB, 12 * u, k.brz, 0);
            float sq = 8 * u, sp = 5 * u, between = 14 * u;
            ArrayList<ArrayList<Integer>> lines = new ArrayList<>();
            ArrayList<Float> widths = new ArrayList<>();
            ArrayList<Integer> line = new ArrayList<>();
            float lw = 0;
            for (int i = 0; i < cats.length(); i++) {
                JSONArray e = cats.optJSONArray(i);
                if (e == null) continue;
                String t = e.optString(1) + " " + e.optString(2);
                float iw = sq + sp + ("f".equals(e.optString(0)) ? pf : p).measureText(t);
                if (!line.isEmpty() && lw + between + iw > cw) {
                    lines.add(line);
                    widths.add(lw);
                    line = new ArrayList<>();
                    lw = 0;
                }
                lw += (line.isEmpty() ? 0 : between) + iw;
                line.add(i);
            }
            if (!line.isEmpty()) {
                lines.add(line);
                widths.add(lw);
            }
            if (c != null) {
                Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
                for (int li = 0; li < lines.size(); li++) {
                    float x = cx - widths.get(li) / 2, b = y + li * 19 * u + 12 * u;
                    for (int i : lines.get(li)) {
                        JSONArray e = cats.optJSONArray(i);
                        boolean free = "f".equals(e.optString(0));
                        q.setColor(color(e.optString(0)));
                        c.drawRoundRect(x, b - sq - .5f * u, x + sq, b - .5f * u, 2.5f * u, 2.5f * u, q);
                        String t = e.optString(1) + " " + e.optString(2);
                        TextPaint tpp = free ? pf : p;
                        c.drawText(t, x + sq + sp, b, tpp);
                        x += sq + sp + tpp.measureText(t) + between;
                    }
                }
            }
            return lines.size();
        }

        void draw(Canvas c, float cx, float x0, float y, int unit) {
            TextPaint hp = tp(forum, 9.5f * u, k.brz, .26f);
            hp.setTextAlign(Paint.Align.CENTER);
            c.drawText(head, cx, y + 9 * u, hp);
            y += 14 * u;
            JSONArray big = o.optJSONArray("big");
            JSONArray b = big == null ? null : big.optJSONArray(Math.min(unit, big.length() - 1));
            String n = b == null ? "" : b.optString(0), un = b == null ? "" : b.optString(1);
            TextPaint np = tp(forum, 46 * u, k.brz, 0), up = tp(forum, 20 * u, k.mut, 0);
            float nw = np.measureText(n), uw = up.measureText(un), sp = 8 * u, x = cx - (nw + sp + uw) / 2, base = y + 42 * u;
            c.drawText(n, x, base, np);
            c.drawText(un, x + nw + sp, base, up);
            y += 52 * u;
            if (capOn) {
                c.save();
                c.translate(x0, y);
                cap.draw(c);
                c.restore();
                y += cap.getHeight() + 10 * u;
            }
            /* the bar: every part of the life left in its colour, the free part in bronze */
            JSONArray cats = o.optJSONArray("cats");
            if (cats != null) {
                Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
                float bh = 10 * u, gapx = 2 * u, total = cw - gapx * Math.max(0, cats.length() - 1), bx = x0;
                c.save();
                Path clip = new Path();
                clip.addRoundRect(new RectF(x0, y, x0 + cw, y + bh), bh / 2, bh / 2, Path.Direction.CW);
                c.clipPath(clip);
                for (int i = 0; i < cats.length(); i++) {
                    JSONArray e = cats.optJSONArray(i);
                    if (e == null) continue;
                    float bw = (float) Math.max(2 * u, total * e.optDouble(3, 0));
                    q.setColor(color(e.optString(0)));
                    c.drawRect(bx, y, Math.min(x0 + cw, bx + bw), y + bh, q);
                    bx += bw + gapx;
                }
                c.restore();
                y += bh + 10 * u;
                legend(c, cx, y, cats);
            }
        }
    }

    /* the counters, every second: TEMPVS NOSTRVM at its own pace, until lights out or rising, until the working day
       ends; and the back of the dial when it is turned */
    private void paintLive(Canvas c, long now, HeroData d) {
        HeroMath.Ink k = ink;
        if (tnY > 0) {
            JSONObject tn = d.o.optJSONObject("tn");
            if (tn != null) {
                double v = tn.optDouble("v", 0) - tn.optDouble("r", 0) * (now - tn.optLong("at")) / 1000.0;
                cells(c, HeroTnService.fmt(v, tn.optString("sep", " ")), colR, tnY, tp(forum, 17 * u, k.ink, 0));
            }
        }
        JSONArray sl = d.cur("sl", now);
        if (slY > 0 && sl != null) {
            float left = cells(c, clock(sl.optLong(2) - now), colR, slY, tp(forum, 16 * u, k.ink, 0));
            TextPaint lp = tp(lit, 12 * u, k.mut, 0);
            lp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(sl.optString(1, ""), left - 6 * u, slY, lp);
        }
        JSONArray wk = d.cur("wk", now);
        if (wkY > 0 && wk != null) {
            long to = wk.optLong(2);
            float left;
            if (to > now) left = cells(c, clock(to - now), colR, wkY, tp(forum, 16 * u, k.ink, 0));
            else {
                TextPaint tpp = tp(lit, 12.5f * u, k.ink, 0);
                tpp.setTextAlign(Paint.Align.RIGHT);
                String t = wk.optString(3, "");
                c.drawText(t, colR, wkY, tpp);
                left = colR - tpp.measureText(t);
            }
            TextPaint lp = tp(lit, 12 * u, k.mut, 0);
            lp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(wk.optString(1, ""), left - 6 * u, wkY, lp);
        }
        if (flipped) chrono(c, now, d);
    }

    private void ray(Canvas c, Paint st, double cx, double cy, double a, double r1, double r2, double op, float wid) {
        double s = Math.sin(a * HeroMath.RD), co = Math.cos(a * HeroMath.RD);
        st.setColor(HeroMath.alpha(ink.ink, op));
        st.setStrokeWidth(wid);
        c.drawLine((float) (cx + r1 * s), (float) (cy - r1 * co), (float) (cx + r2 * s), (float) (cy - r2 * co), st);
    }

    private void needle(Canvas c, Paint f, float cx, float cy, float tail, float wid, float wy, float tip) {
        Path a = new Path();
        a.moveTo(cx, cy + tail);
        a.lineTo(cx - wid, cy - wy);
        a.lineTo(cx, cy - tip);
        a.close();
        Path b = new Path();
        b.moveTo(cx, cy + tail);
        b.lineTo(cx + wid, cy - wy);
        b.lineTo(cx, cy - tip);
        b.close();
        f.setColor(ink.brz);
        c.drawPath(a, f);
        f.setColor(ink.gnoDk);
        c.drawPath(b, f);
    }

    /* a register of the reverse: its own track, six numerals, a needle; the numeral the needle stands at is lit */
    private void reg(Canvas c, Paint st, Paint f, float cx, float cy, int n, int maj, String[] nums, double ang, boolean on) {
        final float CDRG = 34, CDRN = 19;
        st.setColor(HeroMath.alpha(ink.ink, .5));
        st.setStrokeWidth(1.2f);
        c.drawCircle(cx, cy, CDRG, st);
        for (int i = 0; i < n; i++) {
            boolean major = i % maj == 0;
            ray(c, st, cx, cy, i * 360.0 / n, major ? CDRG - 7 : CDRG - 4, CDRG, major ? .5 : (day ? .42 : .32), major ? 1.2f : 1f);
        }
        int lit = on ? (int) (Math.round(ang / 60) % 6) : -1;
        Paint np = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        np.setTypeface(forum);
        np.setTextAlign(Paint.Align.CENTER);
        np.setLetterSpacing(.03f);
        for (int kk = 0; kk < nums.length; kk++) {
            double a = kk * 360.0 / nums.length;
            float x = (float) (cx + CDRN * Math.sin(a * HeroMath.RD)), y = (float) (cy - CDRN * Math.cos(a * HeroMath.RD) + 2.9);
            boolean l = kk == lit;
            np.setTextSize(l ? 9.6f : 8f);
            np.setColor(l ? ink.glow : ink.mut);
            if (l && !day) np.setShadowLayer(3.2f, 0, 0, 0x80F7D48E);
            else np.clearShadowLayer();
            c.drawText(nums[kk], x, l ? y + .4f : y, np);
        }
        c.save();
        c.rotate((float) ang, cx, cy);
        Path a = new Path();
        a.moveTo(cx, cy + 5);
        a.lineTo(cx - 1.9f, cy - 7);
        a.lineTo(cx, cy - CDRG + 6);
        a.close();
        Path b = new Path();
        b.moveTo(cx, cy + 5);
        b.lineTo(cx + 1.9f, cy - 7);
        b.lineTo(cx, cy - CDRG + 6);
        b.close();
        f.setColor(ink.brz);
        c.drawPath(a, f);
        f.setColor(ink.gnoDk);
        c.drawPath(b, f);
        c.restore();
        f.setColor(ink.brz);
        c.drawCircle(cx, cy, 2.4f, f);
    }

    /* the reverse of the sundial, as in the app: the needle counts seconds back on the sixty-second track, the left
       register hours until lights out (0–24), the right one minutes, the lower one the hours left of the working day */
    private void chrono(Canvas c, long now, HeroData d) {
        final float DX = 200, DY = 172, CDRX = 62, CDRY = 46;
        JSONArray sl = d.cur("sl", now), wk = d.cur("wk", now);
        JSONObject cd = d.has() ? d.o.optJSONObject("cd") : null;
        boolean asleep = sl != null && "w".equals(sl.optString(3, ""));
        long to = sl == null ? 0 : sl.optLong(2);
        double R = asleep || sl == null ? 0 : Math.floor(Math.max(0, to - now) / 1000.0);
        double W = wk != null && wk.optLong(2) > now ? Math.floor((wk.optLong(2) - now) / 1000.0) : 0;
        double aH = R / 3600 % 24 * 15, aM = R / 60 % 60 * 6, aW = W / 3600 % 12 * 30, aS = R % 60 * 6;
        String lab = asleep ? "VIXI" : cd != null ? cd.optString("lab", "ДО ОТБОЯ") : "ДО ОТБОЯ";
        String sub = sl == null ? "" : asleep ? (cd != null ? cd.optString("up", "подъём в {T}") : "подъём в {T}").replace("{T}", hm(to))
            : (cd != null ? cd.optString("at", "в {T}") : "в {T}").replace("{T}", hm(to));

        c.save();
        c.translate(dialL, dialT);
        c.scale(dialS, dialS);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        f.setColor(ink.plate);
        c.drawCircle(DX, DY, 162, f);
        st.setColor(HeroMath.alpha(ink.ink, .5));
        st.setStrokeWidth(1.2f);
        c.drawCircle(DX, DY, 162, st);
        st.setColor(HeroMath.alpha(ink.ink, day ? .3 : .24));
        st.setStrokeWidth(1f);
        c.drawCircle(DX, DY, 156, st);
        st.setColor(HeroMath.alpha(ink.ink, .75));
        st.setStrokeWidth(1.6f);
        c.drawCircle(DX, DY, 118, st);
        for (int i = 0; i < 60; i++) {
            boolean major = i % 5 == 0;
            ray(c, st, DX, DY, i * 6, major ? 104 : 111, 118, major ? .5 : (day ? .42 : .32), major ? 1.2f : 1f);
        }
        Paint np = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        np.setTypeface(forum);
        np.setTextSize(15);
        np.setTextAlign(Paint.Align.CENTER);
        np.setColor(ink.mut);
        for (int kk = 0; kk < 12; kk++) {
            double a = kk * 30;
            c.drawText(R5[kk], (float) (DX + 139 * Math.sin(a * HeroMath.RD)), (float) (DY - 139 * Math.cos(a * HeroMath.RD) + 5), np);
        }
        boolean on = !asleep && sl != null;
        reg(c, st, f, DX - CDRX, DY, 24, 2, new String[]{"XXIV", "IV", "VIII", "XII", "XVI", "XX"}, on ? aH : 0, on);
        reg(c, st, f, DX + CDRX, DY, 60, 5, new String[]{"LX", "X", "XX", "XXX", "XL", "L"}, on ? aM : 0, on);
        reg(c, st, f, DX, DY + CDRY, 48, 4, new String[]{"XII", "II", "IV", "VI", "VIII", "X"}, on ? aW : 0, on && W > 0);
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        lp.setTypeface(forum);
        lp.setTextAlign(Paint.Align.CENTER);
        lp.setTextSize(10);
        lp.setLetterSpacing(.34f);
        lp.setColor(ink.brz);
        c.drawText(lab, DX, DY - 66, lp);
        lp.setTextSize(17);
        lp.setLetterSpacing(0);
        lp.setColor(ink.ink);
        c.drawText(sub, DX, DY - 43, lp);
        /* PROTINVS VIVE along the lower arc */
        Path arc = new Path();
        arc.addArc(new RectF(DX - 100, DY - 100, DX + 100, DY + 100), 140, -100);
        Paint ins = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        ins.setTypeface(forum);
        ins.setTextSize(day ? 11 : 13);
        ins.setLetterSpacing(day ? .32f : .3f);
        ins.setColor(day ? ink.mut : ink.glow);
        String t = "PROTINVS VIVE";
        float off = (new PathMeasure(arc, false).getLength() - ins.measureText(t)) / 2;
        if (!day) ins.setShadowLayer(4f, 0, 0, 0xB3F7D48E);
        c.drawTextOnPath(t, arc, off, 0, ins);
        /* the seconds needle */
        c.save();
        c.rotate((float) (on ? aS : 0), DX, DY);
        needle(c, f, DX, DY, 18, 3.2f, 22, 108);
        c.restore();
        f.setColor(ink.brz);
        c.drawCircle(DX, DY, 3.4f, f);
        c.restore();
    }
}
