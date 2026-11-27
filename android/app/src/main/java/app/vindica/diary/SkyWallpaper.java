package app.vindica.diary;

import android.annotation.TargetApi;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.ViewConfiguration;

/* The live wallpaper: the sky of «Сегодня» behind the home screen, with the sundial, every counter and the weather.
   It draws once a second while it is in view and not at all otherwise. A tap on the dial turns it over, on the life
   total it changes years for weeks and hours, on the weather it asks for it anew. Its colours, bronze on the night
   blue, are what the phone builds its palette from. */
public class SkyWallpaper extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new SkyEngine();
    }

    final class SkyEngine extends Engine {
        private final Handler h = new Handler(Looper.getMainLooper());
        private final SkyScene scene = new SkyScene();
        private final Runnable tick = this::frame;
        private boolean visible;
        private float downX, downY;
        private long downAt, lastTap;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(true);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) {
                weather(false);
                frame();
            } else {
                h.removeCallbacks(tick);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            scene.invalidate();
            frame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            h.removeCallbacks(tick);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            h.removeCallbacks(tick);
            super.onDestroy();
        }

        /* the home screen tells the wallpaper where the user tapped on an empty spot */
        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (WallpaperManager.COMMAND_TAP.equals(action)) tapAt(x, y);
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        /* and where the home screen does not, a short touch without movement counts as a tap */
        @Override
        public void onTouchEvent(MotionEvent e) {
            int slop = ViewConfiguration.get(getApplicationContext()).getScaledTouchSlop();
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                downAt = SystemClock.uptimeMillis();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP
                && SystemClock.uptimeMillis() - downAt < 350
                && Math.abs(e.getX() - downX) < slop && Math.abs(e.getY() - downY) < slop) {
                tapAt(e.getX(), e.getY());
            }
            super.onTouchEvent(e);
        }

        private void tapAt(float x, float y) {
            long now = SystemClock.uptimeMillis();
            if (now - lastTap < 400) return;
            if (scene.tap(x, y)) {
                lastTap = now;
                if (scene.wantWeather) {
                    scene.wantWeather = false;
                    weather(true);
                }
                frame();
            }
        }

        /* where the phone is (when allowed, at most every quarter of an hour), then the weather there */
        private void weather(boolean force) {
            final Context app = getApplicationContext();
            HeroPlace.refresh(app, false, HeroData.load(app).str("lang", ""), changed -> h.post(() -> {
                if (changed) {
                    scene.invalidate();
                    frame();
                }
                HeroWeather.refresh(app, force || changed, () -> h.post(() -> {
                    scene.invalidate();
                    frame();
                }));
            }));
        }

        @TargetApi(27)
        @Override
        public WallpaperColors onComputeColors() {
            return new WallpaperColors(Color.valueOf(0xFFD0A867), Color.valueOf(0xFF1A2034), Color.valueOf(0xFF2C2421));
        }

        private void frame() {
            h.removeCallbacks(tick);
            if (!visible) return;
            SurfaceHolder sh = getSurfaceHolder();
            Canvas c = null;
            long now = System.currentTimeMillis();
            try {
                c = sh.lockCanvas();
                if (c != null) scene.draw(getApplicationContext(), c, c.getWidth(), c.getHeight(), now);
            } catch (RuntimeException e) {
                /* a frame lost; the next second draws again */
            } finally {
                if (c != null) {
                    try {
                        sh.unlockCanvasAndPost(c);
                    } catch (RuntimeException e) {
                        /* the surface went away meanwhile */
                    }
                }
            }
            if (now % 60000 < 1100) weather(false);
            h.postDelayed(tick, 1000 - now % 1000 + 20);
        }
    }
}
