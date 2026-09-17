package jp.bunkaich.sukashimotion;

import android.app.Application;
import android.app.LocaleManager;
import android.os.LocaleList;

/** A Chinese-first fork; explicit later language choices remain respected. */
public final class FolduoApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        var preferences = getSharedPreferences("appearance", MODE_PRIVATE);
        if (!preferences.getBoolean("locale_initialized", false)) {
            var manager = getSystemService(LocaleManager.class);
            if (manager.getApplicationLocales().isEmpty()) {
                manager.setApplicationLocales(LocaleList.forLanguageTags("zh-Hans"));
            }
            preferences.edit().putBoolean("locale_initialized", true).apply();
        }
    }
}
