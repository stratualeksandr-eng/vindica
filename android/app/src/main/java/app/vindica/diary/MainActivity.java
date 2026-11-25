package app.vindica.diary;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private boolean restoring;
    private boolean ready;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        /* the system brings back the old intent when it recreates the screen: the widget's request was already handled then */
        restoring = savedInstanceState != null;
        registerPlugin(VindicaWidgetPlugin.class);
        super.onCreate(savedInstanceState);
        restoring = false;
        ready = true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (restoring || !keepOpen(intent)) return;
        /* the app was already running: tell the page right away */
        if (ready && getBridge() != null) getBridge().triggerWindowJSEvent("vindicawidget");
    }

    /* the widget asked to open the shopping list («shop») or to add to it («add»): the page picks it up when it is ready */
    private boolean keepOpen(Intent intent) {
        String what = intent != null ? intent.getStringExtra(ShopWidget.EXTRA_OPEN) : null;
        if (what == null) return false;
        intent.removeExtra(ShopWidget.EXTRA_OPEN);
        getSharedPreferences(ShopWidget.PREFS, MODE_PRIVATE).edit().putString("open", what).apply();
        return true;
    }
}
