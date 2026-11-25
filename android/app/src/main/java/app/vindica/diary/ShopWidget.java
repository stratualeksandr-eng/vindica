package app.vindica.diary;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* «Покупки» on the home screen: what is left to buy; a tap on a line marks it bought and it goes away.
   The page keeps the list in SharedPreferences («shop»); marks made here wait in «checked» until the app takes them. */
public class ShopWidget extends AppWidgetProvider {
    static final String PREFS = "vindica_widget";
    static final String ACTION_CHECK = "app.vindica.diary.SHOP_CHECK";
    static final String EXTRA_ID = "vindica_item";
    static final String EXTRA_OPEN = "vindica_open";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_CHECK.equals(intent.getAction())) {
            String id = intent.getStringExtra(EXTRA_ID);
            if (id != null) check(ctx, id);
        }
    }

    @SuppressWarnings("deprecation")
    static void update(Context ctx, AppWidgetManager mgr, int wid) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_shop);
        int n = left(sp).length();
        v.setTextViewText(R.id.shop_title, sp.getString("title", ctx.getString(R.string.widget_shop_name)));
        v.setTextViewText(R.id.shop_count, n > 0 ? String.valueOf(n) : "");
        v.setTextViewText(R.id.shop_empty, sp.getString("empty", ctx.getString(R.string.widget_shop_empty)));
        Intent svc = new Intent(ctx, ShopWidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.shop_list, svc);
        v.setEmptyView(R.id.shop_list, R.id.shop_empty);
        int mutable = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        Intent check = new Intent(ctx, ShopWidget.class).setAction(ACTION_CHECK);
        v.setPendingIntentTemplate(R.id.shop_list, PendingIntent.getBroadcast(ctx, 0, check, mutable));
        v.setOnClickPendingIntent(R.id.shop_head, open(ctx, "shop", 1));
        v.setOnClickPendingIntent(R.id.shop_add, open(ctx, "add", 2));
        mgr.updateAppWidget(wid, v);
        mgr.notifyAppWidgetViewDataChanged(wid, R.id.shop_list);
    }

    static PendingIntent open(Context ctx, String what, int code) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setAction("app.vindica.diary.OPEN_" + what);
        i.putExtra(EXTRA_OPEN, what);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /* what is left to buy: the list from the app without what was just marked here */
    static JSONArray left(SharedPreferences sp) {
        JSONArray out = new JSONArray();
        try {
            JSONArray a = new JSONArray(sp.getString("shop", "[]")), c = new JSONArray(sp.getString("checked", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id");
                boolean gone = false;
                for (int j = 0; j < c.length(); j++) {
                    JSONObject k = c.optJSONObject(j);
                    if (k != null && id.equals(k.optString("id"))) { gone = true; break; }
                }
                if (!gone) out.put(o);
            }
        } catch (JSONException e) {
            return new JSONArray();
        }
        return out;
    }

    static void check(Context ctx, String id) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray c = new JSONArray(sp.getString("checked", "[]"));
            c.put(new JSONObject().put("id", id).put("at", System.currentTimeMillis()));
            sp.edit().putString("checked", c.toString()).commit();
        } catch (JSONException e) {
            return;
        }
        refreshAll(ctx);
    }

    static void refreshAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, ShopWidget.class));
        for (int id : ids) update(ctx, mgr, id);
    }
}
