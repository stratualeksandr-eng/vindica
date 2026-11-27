package app.vindica.diary;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/* Debug builds only: the live wallpaper's picture full screen, drawn by the same SkyScene, for emulator shots.
   Extras: fixture (the page's data, a file in the app's files), flip (the dial turned over), unit (0 years, 1 weeks,
   2 hours), wx (a weather answer to use instead of asking Open-Meteo: "code,temperature,cover"), place (where the
   phone is: "lat,lon,name"), noplace (forget the place: back to Brașov on a Romanian clock), locate (look where the
   phone is, as the app does). */
public class SkyTestActivity extends Activity {
    private final SkyScene scene = new SkyScene();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        if (in.getBooleanExtra("noplace", false)) getSharedPreferences(ShopWidget.PREFS, MODE_PRIVATE).edit().remove(HeroPlace.KEY).commit();
        String place = in.getStringExtra("place");
        if (place != null) {
            try {
                String[] q = place.split(",", -1);
                HeroPlace.put(this, Double.parseDouble(q[0]), Double.parseDouble(q[1]), q.length > 2 ? q[2] : "", "ru");
            } catch (Exception e) {
                /* then wherever it was */
            }
        }
        HeroPlace.load(this);
        double[] here = HeroPlace.where(this);
        String wx = in.getStringExtra("wx");
        if (wx != null) {
            try {
                String[] p = wx.split(",");
                JSONObject o = new JSONObject();
                o.put("at", System.currentTimeMillis());
                o.put("code", Integer.parseInt(p[0]));
                o.put("t", Double.parseDouble(p[1]));
                o.put("cover", Double.parseDouble(p[2]));
                o.put("wind", 2);
                if (here != null) {
                    o.put("lat", here[0]);
                    o.put("lon", here[1]);
                }
                getSharedPreferences(ShopWidget.PREFS, MODE_PRIVATE).edit().putString(HeroWeather.KEY, o.toString()).commit();
            } catch (Exception e) {
                /* then the real weather */
            }
        } else {
            HeroWeather.refresh(this, true, null);
        }
        /* locate: look where the phone is now, as the app does in the foreground */
        if (in.getBooleanExtra("locate", false)) HeroPlace.refresh(this, true, "ru", changed -> runOnUiThread(() -> {
            scene.invalidate();
            HeroWeather.refresh(this, true, null);
        }));
        scene.flipped = in.getBooleanExtra("flip", false);
        scene.unit = in.getIntExtra("unit", 0);
        setContentView(new Sky(this));
    }

    private final class Sky extends View {
        Sky(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = System.currentTimeMillis();
            scene.draw(getContext(), c, getWidth(), getHeight(), now);
            postInvalidateDelayed(1000 - now % 1000 + 20);
        }
    }
}
