package app.vindica.diary;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/* the lines of the «Покупки» widget */
public class ShopWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Lines(getApplicationContext());
    }

    static class Lines implements RemoteViewsFactory {
        private final Context ctx;
        private final List<String[]> items = new ArrayList<>();

        Lines(Context ctx) { this.ctx = ctx; }

        @Override public void onCreate() { }

        @Override
        public void onDataSetChanged() {
            items.clear();
            JSONArray a = ShopWidget.left(ctx.getSharedPreferences(ShopWidget.PREFS, Context.MODE_PRIVATE));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) items.add(new String[]{o.optString("id"), o.optString("t")});
            }
        }

        @Override public void onDestroy() { items.clear(); }

        @Override public int getCount() { return items.size(); }

        @Override
        public RemoteViews getViewAt(int pos) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_shop_item);
            if (pos < 0 || pos >= items.size()) return v;
            v.setTextViewText(R.id.shop_item_text, items.get(pos)[1]);
            Intent fill = new Intent();
            fill.putExtra(ShopWidget.EXTRA_ID, items.get(pos)[0]);
            v.setOnClickFillInIntent(R.id.shop_item, fill);
            return v;
        }

        @Override public RemoteViews getLoadingView() { return null; }

        @Override public int getViewTypeCount() { return 1; }

        @Override public long getItemId(int pos) { return pos < items.size() ? items.get(pos)[0].hashCode() : pos; }

        @Override public boolean hasStableIds() { return true; }
    }
}
