package app.vindica.diary;

import java.util.Calendar;
import java.util.TimeZone;

/* The sky of «Сегодня» as the page computes it, so the widget shows the same: the sun by the hour (the sundial,
   sunrise and sunset), the moon after Schlyter, and the colours of the sky by the sun's altitude.
   Plain Java on purpose: it is checked against the page's own numbers off the phone. */
final class HeroMath {
    static final double RD = Math.PI / 180;
    /* the dial in the page's units: a 400 × 344 face */
    static final double DX = 200, DY = 172, DRING = 118, DNUM = 139, DPL = 162, DGN = 64;
    static final String[] ROMH = {"XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI"};

    private HeroMath() {}

    static double cl(double x, double a, double b) { return Math.max(a, Math.min(b, x)); }

    static final class Sun {
        double tz, lat, lon, eq, de, tst, H;
    }

    /* the sun for this moment and place: Brașov when the clock is Romanian, else the middle of the time zone */
    static Sun sunPos(long now, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(now);
        Sun p = new Sun();
        p.tz = zone.getOffset(now) / 3600000.0;
        boolean ro = p.tz == 2 || p.tz == 3;
        p.lat = ro ? 45.65 : 45;
        p.lon = ro ? 25.6 : p.tz * 15;
        Calendar y0 = Calendar.getInstance(zone);
        y0.clear();
        y0.set(c.get(Calendar.YEAR), Calendar.JANUARY, 0, 0, 0, 0);
        double doy = Math.floor((now - y0.getTimeInMillis()) / 864e5), g = 2 * Math.PI / 365 * (doy - 1);
        p.eq = 229.18 * (.000075 + .001868 * Math.cos(g) - .032077 * Math.sin(g) - .014615 * Math.cos(2 * g) - .040849 * Math.sin(2 * g));
        p.de = .006918 - .399912 * Math.cos(g) + .070257 * Math.sin(g) - .006758 * Math.cos(2 * g) + .000907 * Math.sin(2 * g) - .002697 * Math.cos(3 * g) + .00148 * Math.sin(3 * g);
        double m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60.0;
        p.tst = ((m + p.eq + 4 * p.lon - 60 * p.tz) % 1440 + 1440) % 1440;
        p.H = p.tst / 4 - 180;
        return p;
    }

    /* sunrise and sunset, in hours of the local clock */
    static double[] sunTimes(long now, TimeZone zone) {
        Sun p = sunPos(now, zone);
        double ha = Math.acos(cl(Math.cos(90.833 * RD) / (Math.cos(p.lat * RD) * Math.cos(p.de)) - Math.tan(p.lat * RD) * Math.tan(p.de), -1, 1)) / RD;
        return new double[]{(720 - 4 * (p.lon + ha) - p.eq) / 60 + p.tz, (720 - 4 * (p.lon - ha) - p.eq) / 60 + p.tz};
    }

    static double sunAlt(Sun p) {
        return Math.asin(Math.sin(p.lat * RD) * Math.sin(p.de) + Math.cos(p.lat * RD) * Math.cos(p.de) * Math.cos(p.H * RD)) / RD;
    }

    /* day is from sunrise to sunset, by the local clock */
    static boolean isDay(long now, TimeZone zone) {
        double[] s = sunTimes(now, zone);
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(now);
        double t = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0;
        return t >= s[0] && t < s[1];
    }

    /* «12:07»: the sun's own time */
    static String fmtM(double m) {
        int h = (int) Math.floor(m / 60) % 24, mm = (int) Math.floor(m % 60);
        return (h < 10 ? "0" : "") + h + ":" + (mm < 10 ? "0" : "") + mm;
    }

    /* hour line of a horizontal dial: angle from the noon line, clockwise, for hour angle H at latitude lat */
    static double hla(double H, double lat) {
        return Math.atan2(Math.sin(lat * RD) * Math.sin(H * RD), Math.cos(H * RD)) / RD;
    }

    static double[] dpt(double a, double r) {
        return new double[]{DX + r * Math.sin(a * RD), DY - r * Math.cos(a * RD)};
    }

    /* the tip of the gnomon's shadow on the dial */
    static double[] shadowTip(double H, Sun p) {
        double la = p.lat * RD, d = p.de, h = H * RD;
        double sx = -Math.cos(d) * Math.sin(h), sy = Math.sin(d) * Math.cos(la) - Math.cos(d) * Math.cos(h) * Math.sin(la),
            sz = Math.sin(d) * Math.sin(la) + Math.cos(d) * Math.cos(h) * Math.cos(la);
        double k = Math.min(5000, DGN * Math.tan(la) / Math.max(sz, .02));
        return new double[]{DX - k * sx, DY - DGN + k * sy};
    }

    private static double n360(double x) { return ((x % 360) + 360) % 360; }
    private static double s(double q) { return Math.sin(q * RD); }
    private static double c(double q) { return Math.cos(q * RD); }

    /* the moon's real place: Schlyter's lunar theory with the main perturbations and parallax, good to about a degree */
    static double[] moonAltAz(long t, double lat, double lon) {
        double d = t / 864e5 + 2440587.5 - 2451543.5;
        double N = n360(125.1228 - .0529538083 * d), i = 5.1454, w = n360(318.0634 + .1643573223 * d), a = 60.2666, e = .0549, M = n360(115.3654 + 13.0649929509 * d);
        double Ms = n360(356.047 + .9856002585 * d), Ls = n360(Ms + 282.9404 + 4.70935e-5 * d);
        double E = M + (e / RD) * s(M) * (1 + e * c(M));
        for (int k = 0; k < 5; k++) E -= (E - (e / RD) * s(E) - M) / (1 - e * c(E));
        double x = a * (c(E) - e), y = a * Math.sqrt(1 - e * e) * s(E), v = Math.atan2(y, x) / RD, dist = Math.hypot(x, y);
        double xe = dist * (c(N) * c(v + w) - s(N) * s(v + w) * c(i)), ye = dist * (s(N) * c(v + w) + c(N) * s(v + w) * c(i)), ze = dist * s(v + w) * s(i);
        double lo = Math.atan2(ye, xe) / RD, la = Math.atan2(ze, Math.hypot(xe, ye)) / RD;
        double Lm = n360(N + w + M), D = n360(Lm - Ls), F = n360(Lm - N);
        lo += -1.274 * s(M - 2 * D) + .658 * s(2 * D) - .186 * s(Ms) - .059 * s(2 * M - 2 * D) - .057 * s(M - 2 * D + Ms) + .053 * s(M + 2 * D) + .046 * s(2 * D - Ms) + .041 * s(M - Ms) - .035 * s(D) - .031 * s(M + Ms) - .015 * s(2 * F - 2 * D) + .011 * s(M - 4 * D);
        la += -.173 * s(F - 2 * D) - .055 * s(M - F - 2 * D) - .046 * s(M + F - 2 * D) + .033 * s(F + 2 * D) + .017 * s(2 * M + F);
        dist += -.58 * c(M - 2 * D) - .46 * c(2 * D);
        double ecl = 23.4393 - 3.563e-7 * d, X = c(lo) * c(la), Y = s(lo) * c(la) * c(ecl) - s(la) * s(ecl), Z = s(lo) * c(la) * s(ecl) + s(la) * c(ecl);
        double RA = Math.atan2(Y, X) / RD, Dec = Math.atan2(Z, Math.hypot(X, Y)) / RD;
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(t);
        double UT = u.get(Calendar.HOUR_OF_DAY) + u.get(Calendar.MINUTE) / 60.0 + u.get(Calendar.SECOND) / 3600.0, HA = n360(Ls + 180 + UT * 15 + lon - RA);
        double hx = c(HA) * c(Dec), hy = s(HA) * c(Dec), hz = s(Dec), xh = hx * s(lat) - hz * c(lat), zh = hx * c(lat) + hz * s(lat);
        double alt = Math.asin(zh) / RD;
        alt -= Math.asin(1 / dist) / RD * Math.cos(alt * RD);
        return new double[]{alt, n360(Math.atan2(hy, xh) / RD + 180)};
    }

    static double moonPhase(long t) {
        return (((t - 947182440000L) / 864e5 / 29.530588853) % 1 + 1) % 1;
    }

    /* colours */
    static int hex(String h) { return 0xFF000000 | Integer.parseInt(h.substring(1), 16); }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int alpha(int c, double a) { return ((int) Math.round(cl(a, 0, 1) * 255) << 24) | (c & 0xFFFFFF); }

    private static int[] pal(String a, String b, String c) { return new int[]{hex(a), hex(b), hex(c)}; }
    static final int[] LD = pal("#CFE0EF", "#E0EAF2", "#EEF2F3"), LE = pal("#D9E0EE", "#EFE1D8", "#F2D7BE"), LM = pal("#D3E1EF", "#ECE5E3", "#F1D9D2"),
        DD = pal("#201C18", "#1C1815", "#171411"), DE = pal("#1A2241", "#2E2545", "#42221D"), DM = pal("#18213D", "#292645", "#3D2035"), DN = pal("#0F1626", "#1A2034", "#2C2421");

    private static int[] mixP(int[] a, int[] b, double t) { return new int[]{mix(a[0], b[0], t), mix(a[1], b[1], t), mix(a[2], b[2], t)}; }
    private static double ez(double x) { return x * x * (3 - 2 * x); }

    /* the sky: marble by day, golden before sunset and rosy after sunrise, dusk and dawn, then night;
       three colours from top to bottom, and how bright the stars are */
    static final class Sky {
        int[] c;
        double stars;
    }

    static Sky sky(long now, TimeZone zone, boolean dark) {
        Sun p = sunPos(now, zone);
        double h = sunAlt(p);
        boolean morn = p.H < 0;
        Sky k = new Sky();
        if (!dark) k.c = h < -.833 ? LD : mixP(LD, morn ? LM : LE, ez(cl((10 - h) / 10.83, 0, 1)));
        else if (h > -.833) k.c = mixP(DD, morn ? DM : DE, ez(cl((10 - h) / 10.83, 0, 1)));
        else {
            k.c = mixP(morn ? DM : DE, DN, ez(cl((-.833 - h) / 11.17, 0, 1)));
            k.stars = cl((-3 - h) / 7, 0, 1);
        }
        return k;
    }

    /* where the sun and the moon stand on the sky, in per cent of its width and height, and how strongly they show */
    static final class Body {
        double x, y, o, warm, phase;
    }

    static Body sunSky(long now, TimeZone zone, boolean dark) {
        Sun p = sunPos(now, zone);
        double la = p.lat * RD, h = p.H * RD, d = p.de;
        double e = -Math.cos(d) * Math.sin(h), n = Math.sin(d) * Math.cos(la) - Math.cos(d) * Math.cos(h) * Math.sin(la),
            u = Math.sin(d) * Math.sin(la) + Math.cos(d) * Math.cos(h) * Math.cos(la);
        double alt = Math.asin(u) / RD, az = ((Math.atan2(e, n) / RD) + 360) % 360;
        Body b = new Body();
        b.warm = cl((12 - alt) / 12, 0, 1);
        b.x = cl((az - 45) / 270 * 100, 3, 97);
        b.y = cl(85 - alt * .97, 8, 90);
        b.o = cl((alt + .8) / 2, 0, 1) * (dark ? .75 : 1);
        return b;
    }

    static Body moonSky(long now, TimeZone zone, boolean dark) {
        Sun p = sunPos(now, zone);
        double[] m = moonAltAz(now, p.lat, p.lon);
        Body b = new Body();
        b.x = cl((m[1] - 45) / 270 * 100, 3, 97);
        b.y = cl(85 - m[0] * .97, 8, 90);
        b.o = cl((m[0] + 1) / 3, 0, 1) * (dark ? 1 : .9);
        b.phase = moonPhase(now);
        return b;
    }

    /* the fixed stars of the night sky, more of them high up: x, y in a 400 × 900 sky, radius, opacity */
    static double[][] stars() {
        long x = 11;
        double[][] out = new double[84][];
        for (int i = 0; i < 84; i++) {
            double[] r = new double[4];
            for (int j = 0; j < 4; j++) {
                x = x * 16807 % 2147483647L;
                r[j] = x / 2147483647.0;
            }
            out[i] = new double[]{r[0] * 400, Math.pow(r[1], 1.5) * 880, .75 + r[2] * 1.05, .6 + r[3] * .4};
        }
        return out;
    }

    /* the page's palette, by day (light) and by night (dark) */
    static final class Ink {
        final int ink, mut, red, brz, glow, plate, shF, shE, gnoDk, line;

        Ink(boolean dark) {
            ink = hex(dark ? "#ECE5D9" : "#1E1915");
            mut = hex(dark ? "#A89E91" : "#685D51");
            red = hex(dark ? "#E0705E" : "#9A2A20");
            brz = hex(dark ? "#D0A867" : "#7A5726");
            glow = hex(dark ? "#F7D48E" : "#87500B");
            plate = dark ? alpha(hex("#ECE4D6"), .06) : alpha(hex("#FFFDF9"), .45);
            shF = dark ? alpha(0, .42) : alpha(hex("#2E2218"), .17);
            shE = dark ? alpha(0, .78) : alpha(hex("#1E1915"), .74);
            gnoDk = mix(brz, 0xFF000000, .42);
            line = ink;
        }
    }
}
