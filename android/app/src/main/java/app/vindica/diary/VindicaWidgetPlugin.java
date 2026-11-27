package app.vindica.diary;

import android.content.Context;
import android.content.SharedPreferences;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONException;

/* the page and the home-screen widgets: the shopping list goes to «Покупки», marks made there come back;
   the days ahead go to «Сегодня» and «Часы»; where the phone is goes to the sky, the weather and the skyline */
@CapacitorPlugin(name = "VindicaWidget", permissions = {
    @Permission(alias = "location", strings = {android.Manifest.permission.ACCESS_COARSE_LOCATION})
})
public class VindicaWidgetPlugin extends Plugin {
    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(ShopWidget.PREFS, Context.MODE_PRIVATE);
    }

    @PluginMethod
    public void setShop(PluginCall call) {
        JSArray items = call.getArray("items", new JSArray());
        prefs().edit()
            .putString("shop", items.toString())
            .putString("title", call.getString("title", getContext().getString(R.string.widget_shop_name)))
            .putString("empty", call.getString("empty", getContext().getString(R.string.widget_shop_empty)))
            .commit();
        ShopWidget.refreshAll(getContext());
        call.resolve();
    }

    /* «Сегодня» on the home screen: the texts of the days ahead, as the page wrote them */
    @PluginMethod
    public void setHero(PluginCall call) {
        String json = call.getString("json", null);
        if (json == null || json.isEmpty()) {
            call.reject("json");
            return;
        }
        prefs().edit().putString(HeroWidget.KEY, json).commit();
        try {
            HeroWidget.renderAll(getContext());
        } catch (RuntimeException e) {
            /* the widget looks again on its own within a minute */
        }
        call.resolve();
    }

    /* the live wallpaper: the phone's own preview of it, with its button to set it; else the list of live wallpapers */
    @PluginMethod
    public void openWallpaper(PluginCall call) {
        android.app.Activity a = getActivity();
        if (a == null) {
            JSObject r = new JSObject();
            r.put("ok", false);
            call.resolve(r);
            return;
        }
        a.runOnUiThread(() -> {
            boolean ok = true;
            try {
                android.content.Intent i = new android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                i.putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new android.content.ComponentName(a, SkyWallpaper.class));
                a.startActivity(i);
            } catch (Exception e) {
                try {
                    a.startActivity(new android.content.Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
                } catch (Exception e2) {
                    ok = false;
                }
            }
            JSObject r = new JSObject();
            r.put("ok", ok);
            call.resolve(r);
        });
    }

    @PluginMethod
    public void takeChecked(PluginCall call) {
        SharedPreferences sp = prefs();
        String c = sp.getString("checked", "[]");
        sp.edit().putString("checked", "[]").commit();
        JSArray items;
        try {
            items = new JSArray(c);
        } catch (JSONException e) {
            items = new JSArray();
        }
        JSObject r = new JSObject();
        r.put("items", items);
        call.resolve(r);
    }

    /* where the phone is, as last known: {lat, lon, name, at, perm}; lat is missing until the first fix */
    @PluginMethod
    public void getPlace(PluginCall call) {
        call.resolve(place());
    }

    private JSObject place() {
        JSObject r = new JSObject();
        HeroPlace.Place p = HeroPlace.load(getContext());
        if (p != null) {
            r.put("lat", p.lat);
            r.put("lon", p.lon);
            r.put("name", p.name);
            r.put("at", p.at);
        }
        PermissionState s = getPermissionState("location");
        r.put("perm", s == PermissionState.GRANTED ? "granted" : s == PermissionState.DENIED ? "denied" : "prompt");
        return r;
    }

    /* looks where the phone is: asks for the permission first when ask is set (the first time, or from the settings),
       otherwise only looks when allowed; answers once the look is done, and tells the page when the place changed */
    @PluginMethod
    public void locate(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            if (Boolean.TRUE.equals(call.getBoolean("ask", false))) {
                requestPermissionForAlias("location", call, "locatePerm");
                return;
            }
            call.resolve(place());
            return;
        }
        look(call);
    }

    @PermissionCallback
    private void locatePerm(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) look(call);
        else call.resolve(place());
    }

    private void look(final PluginCall call) {
        final Context ctx = getContext();
        HeroPlace.refresh(ctx, Boolean.TRUE.equals(call.getBoolean("force", false)), call.getString("lang", ""), changed -> {
            JSObject r = place();
            if (changed) {
                notifyListeners("place", r);
                try {
                    HeroWidget.renderAll(ctx);
                } catch (RuntimeException e) {
                    /* the widget looks again on its own within a minute */
                }
            }
            call.resolve(r);
        });
    }

    @PluginMethod
    public void takeOpen(PluginCall call) {
        SharedPreferences sp = prefs();
        String o = sp.getString("open", "");
        sp.edit().remove("open").commit();
        JSObject r = new JSObject();
        r.put("open", o);
        call.resolve(r);
    }
}
