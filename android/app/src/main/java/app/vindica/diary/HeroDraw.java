package app.vindica.diary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;

import java.util.TimeZone;

/* The pictures of «Сегодня» that the home screen cannot draw by itself: the sky, the moon, the greeting in the app's
   own lettering (Forum) and the sundial with its shadow, the same lines, numerals and inscription as in the app. */
final class HeroDraw {
    private static Typeface forum;

    private HeroDraw() {}

    static synchronized Typeface forum(Context ctx) {
        if (forum == null) {
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    forum = ctx.getResources().getFont(R.font.forum);
                } catch (Exception e) {
                    forum = null;
                }
            }
            if (forum == null) forum = Typeface.SERIF;
        }
        return forum;
    }

    /* the sky behind everything: its three colours from top to bottom and the sun's glow; drawn small and stretched */
    static Bitmap sky(int wPx, int hPx, HeroMath.Sky k, HeroMath.Body sun, float density) {
        int w = Math.max(8, wPx / 6), h = Math.max(8, hPx / 6);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, k.c, new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        if (sun.o > .01) {
            float cx = (float) (sun.x / 100 * w), cy = (float) (sun.y / 100 * h), r = 85 * density / 6f;
            int core = HeroMath.mix(HeroMath.hex("#FFF3C9"), HeroMath.hex("#FFC98E"), sun.warm),
                glow = HeroMath.mix(HeroMath.hex("#FFD777"), HeroMath.hex("#FF8F55"), sun.warm),
                hot = HeroMath.mix(HeroMath.hex("#FFFEF6"), HeroMath.hex("#FFF0D6"), sun.warm);
            int[] cols = {hot, hot, HeroMath.alpha(core, .96), HeroMath.alpha(glow, .62), HeroMath.alpha(glow, .3), HeroMath.alpha(glow, .1), HeroMath.alpha(glow, 0)};
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            g.setShader(new RadialGradient(cx, cy, r, cols, new float[]{0f, .075f, .11f, .17f, .3f, .55f, 1f}, Shader.TileMode.CLAMP));
            g.setAlpha((int) Math.round(HeroMath.cl(sun.o, 0, 1) * 255));
            c.drawCircle(cx, cy, r, g);
        }
        return b;
    }

    /* the moon in its phase: lit on the right while it waxes; a soft halo at night */
    static Bitmap moon(int px, double phase, boolean dark) {
        Bitmap b = Bitmap.createBitmap(Math.max(4, px), Math.max(4, px), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float s = px / 38f;
        c.scale(s, s);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (dark) {
            int mh = HeroMath.alpha(HeroMath.hex("#FFF6E4"), .38);
            p.setShader(new RadialGradient(19, 19, 19, new int[]{mh, mh, mh & 0x00FFFFFF}, new float[]{0f, .45f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(19, 19, 19, p);
            p.setShader(null);
        }
        p.setColor(dark ? HeroMath.hex("#2A3147") : HeroMath.alpha(HeroMath.hex("#6E7A91"), .13));
        c.drawCircle(19, 19, 11, p);
        Path lit = moonPath(19, 19, 11, phase);
        p.setColor(dark ? HeroMath.hex("#FFFAEE") : HeroMath.alpha(0xFFFFFFFF, .96));
        c.drawPath(lit, p);
        if (!dark) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(.6f);
            p.setColor(HeroMath.alpha(HeroMath.hex("#505C73"), .3));
            c.drawPath(lit, p);
        }
        return b;
    }

    static Path moonPath(float cx, float cy, float r, double ph) {
        boolean w = ph < .5;
        float rx = (float) Math.abs(Math.cos(2 * Math.PI * ph)) * r;
        boolean s2 = w ? ph >= .25 : ph > .75;
        Path path = new Path();
        path.moveTo(cx, cy - r);
        path.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), 270, w ? 180 : -180, false);
        path.arcTo(new RectF(cx - rx, cy - r, cx + rx, cy + r), 90, s2 ? 180 : -180, false);
        path.close();
        return path;
    }

    /* the greeting, two lines in Forum as on the page; it narrows itself to the room it has */
    static Bitmap hello(Context ctx, String l1, String l2, int wPx, float sizePx, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(forum(ctx));
        p.setColor(color);
        p.setTextSize(sizePx);
        float tw = Math.max(p.measureText(l1), l2.isEmpty() ? 0 : p.measureText(l2));
        if (tw > wPx) {
            sizePx = sizePx * wPx / tw * .98f;
            p.setTextSize(sizePx);
        }
        Paint.FontMetrics fm = p.getFontMetrics();
        float L = sizePx * 1.08f, A = -fm.ascent, D = fm.descent, base = (L - (A + D)) / 2 + A;
        int lines = l2.isEmpty() ? 1 : 2;
        int h = (int) Math.ceil(L * (lines - 1) + base + D + 2);
        Bitmap b = Bitmap.createBitmap(Math.max(4, wPx), Math.max(4, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawText(l1, 0, base, p);
        if (lines > 1) c.drawText(l2, 0, base + L, p);
        return b;
    }

    /* the thin bar of eighty years under the weeks lived */
    static Bitmap bar(int wPx, int hPx, double frac, int ink, int brz) {
        Bitmap b = Bitmap.createBitmap(Math.max(2, wPx), Math.max(1, hPx), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(HeroMath.alpha(ink, .16));
        c.drawRect(0, 0, b.getWidth(), b.getHeight(), p);
        p.setColor(brz);
        c.drawRect(0, 0, (float) (b.getWidth() * HeroMath.cl(frac, 0, 1)), b.getHeight(), p);
        return b;
    }

    private static void ring(Canvas c, Paint st, int ink, double op, float w, double r) {
        st.setColor(HeroMath.alpha(ink, op));
        st.setStrokeWidth(w);
        c.drawCircle((float) HeroMath.DX, (float) HeroMath.DY, (float) r, st);
    }

    private static void ray(Canvas c, Paint st, double a, double r1, double r2, int ink, double op, float w) {
        double[] p = HeroMath.dpt(a, r1), q = HeroMath.dpt(a, r2);
        st.setColor(HeroMath.alpha(ink, op));
        st.setStrokeWidth(w);
        c.drawLine((float) p[0], (float) p[1], (float) q[0], (float) q[1], st);
    }

    /* the same picture as last minute is not drawn again: at night nothing on the dial moves */
    private static final java.util.HashMap<String, Bitmap> CACHE = new java.util.HashMap<>();

    private static Bitmap kept(String key) {
        return CACHE.get(key);
    }

    private static Bitmap keep(String kind, String key, Bitmap b) {
        java.util.Iterator<String> it = CACHE.keySet().iterator();
        while (it.hasNext()) if (it.next().startsWith(kind + "|")) it.remove();
        CACHE.put(key, b);
        return b;
    }

    static synchronized Bitmap dialCached(Context ctx, int wPx, long now, TimeZone z, boolean dark, boolean day) {
        HeroMath.Sun p = HeroMath.sunPos(now, z);
        String kind = "dial" + wPx, key = kind + "|" + dark + "|" + day + "|" + p.lat + "|" + (p.H < 0) + "|" + (day ? Math.round(p.H * 4) + "|" + (int) Math.floor(p.tst / 60) : "n");
        Bitmap b = kept(key);
        return b != null ? b : keep(kind, key, dial(ctx, wPx, now, z, dark, day));
    }

    static synchronized Bitmap helloCached(Context ctx, String l1, String l2, int wPx, float sizePx, int color) {
        String kind = "hello" + wPx, key = kind + "|" + l1 + "|" + l2 + "|" + sizePx + "|" + color;
        Bitmap b = kept(key);
        return b != null ? b : keep(kind, key, hello(ctx, l1, l2, wPx, sizePx, color));
    }

    /* the sundial of «Сегодня»: the plate, the rings, the hour lines of a horizontal dial at this latitude with their
       Roman numerals, SINE SOLE SILEO along the lower arc (glowing at night), and by day the gnomon's shadow */
    static Bitmap dial(Context ctx, int wPx, long now, TimeZone z, boolean dark, boolean day) {
        float s = wPx / 400f;
        int hPx = Math.max(4, Math.round(344 * s));
        Bitmap b = Bitmap.createBitmap(Math.max(4, wPx), hPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(s, s);
        HeroMath.Ink k = new HeroMath.Ink(dark);
        HeroMath.Sun p = HeroMath.sunPos(now, z);
        float DX = (float) HeroMath.DX, DY = (float) HeroMath.DY, DRING = (float) HeroMath.DRING, DGN = (float) HeroMath.DGN;
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(k.plate);
        c.drawCircle(DX, DY, (float) HeroMath.DPL, f);
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        ring(c, st, k.ink, .5, 1.2f, HeroMath.DPL);
        ring(c, st, k.ink, day ? .3 : .24, 1f, HeroMath.DPL - 6);
        ring(c, st, k.ink, .75, 1.6f, HeroMath.DRING);
        Paint num = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        num.setTypeface(forum(ctx));
        num.setTextSize(15);
        num.setTextAlign(Paint.Align.CENTER);
        int hr = (int) Math.floor(p.tst / 60);
        for (int h = 4; h <= 20; h++) {
            double a = HeroMath.hla((h - 12) * 15, p.lat);
            ray(c, st, a, 22, HeroMath.DRING, k.ink, day ? .3 : .24, 1f);
            double[] q = HeroMath.dpt(a, HeroMath.DNUM);
            num.setColor(day && h == hr ? k.red : k.mut);
            c.drawText(HeroMath.ROMH[h % 12], (float) q[0], (float) q[1] + 5, num);
        }
        for (double h = 4.5; h < 20; h++) ray(c, st, HeroMath.hla((h - 12) * 15, p.lat), HeroMath.DRING - 10, HeroMath.DRING, k.ink, day ? .42 : .32, 1f);
        /* SINE SOLE SILEO: «without the sun I am silent» */
        float R = (float) HeroMath.DNUM + 4;
        Path arc = new Path();
        arc.addArc(new RectF(DX - R, DY - R, DX + R, DY + R), 130, -80);
        Paint ins = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        ins.setTypeface(forum(ctx));
        ins.setTextSize(day ? 11 : 13);
        ins.setLetterSpacing(day ? .32f : .3f);
        ins.setColor(day ? k.mut : k.glow);
        String text = "SINE SOLE SILEO";
        float off = (new PathMeasure(arc, false).getLength() - ins.measureText(text)) / 2;
        if (!day) {
            Paint g = new Paint(ins);
            g.setMaskFilter(new BlurMaskFilter(8.66f, BlurMaskFilter.Blur.NORMAL));
            c.drawTextOnPath(text, arc, off, 0, g);
            c.drawTextOnPath(text, arc, off, 0, g);
            g.setMaskFilter(new BlurMaskFilter(2.25f, BlurMaskFilter.Blur.NORMAL));
            c.drawTextOnPath(text, arc, off, 0, g);
        }
        c.drawTextOnPath(text, arc, off, 0, ins);
        /* by day the shadow of the gnomon's edge falls on the hour */
        if (day) {
            double[] t = HeroMath.shadowTip(p.H, p);
            c.save();
            Path clip = new Path();
            clip.addCircle(DX, DY, DRING, Path.Direction.CW);
            c.clipPath(clip);
            Path sh = new Path();
            sh.moveTo(DX, DY);
            sh.lineTo(DX, DY - DGN);
            sh.lineTo((float) t[0], (float) t[1]);
            sh.close();
            f.setColor(k.shF);
            c.drawPath(sh, f);
            Paint e = new Paint(Paint.ANTI_ALIAS_FLAG);
            e.setStyle(Paint.Style.STROKE);
            e.setStrokeWidth(2.6f);
            e.setStrokeCap(Paint.Cap.ROUND);
            e.setColor(k.shE);
            c.drawLine(DX, DY, (float) t[0], (float) t[1], e);
            c.restore();
        }
        /* the gnomon: two bronze halves, the one away from the sun darker */
        Path gw = new Path();
        gw.moveTo(DX, DY);
        gw.lineTo(DX - 4.2f, DY - DGN);
        gw.lineTo(DX, DY - DGN);
        gw.close();
        Path ge = new Path();
        ge.moveTo(DX, DY);
        ge.lineTo(DX + 4.2f, DY - DGN);
        ge.lineTo(DX, DY - DGN);
        ge.close();
        f.setColor(p.H < 0 ? k.gnoDk : k.brz);
        c.drawPath(gw, f);
        f.setColor(p.H >= 0 ? k.gnoDk : k.brz);
        c.drawPath(ge, f);
        f.setColor(k.brz);
        c.drawCircle(DX, DY, 3.4f, f);
        return b;
    }
}
