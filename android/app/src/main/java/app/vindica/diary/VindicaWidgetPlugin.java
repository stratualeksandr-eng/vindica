package app.vindica.diary;

import android.content.Context;
import android.content.SharedPreferences;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;

/* the page and the home-screen widget: the list goes to the widget, marks made on the widget come back */
@CapacitorPlugin(name = "VindicaWidget")
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
