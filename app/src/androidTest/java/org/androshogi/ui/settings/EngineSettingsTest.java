package org.androshogi.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.androshogi.engine.EngineKind;
import org.androshogi.settings.AppSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.io.File;

/** Exercise dependent preferences, upgrade compatibility and recreation on Android. */
@RunWith(AndroidJUnit4.class)
public class EngineSettingsTest {
    private Context context;
    private SharedPreferences preferences;
    private final Map<String, String> original = new HashMap<>();

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : keys()) {
            original.put(key, preferences.getString(key, null));
            editor.remove(key);
        }
        editor.putString(AppSettings.KEY_FV_SCALE, "20").commit();
    }

    @After
    public void restoreSettings() {
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : keys()) {
            if (original.get(key) == null) {
                editor.remove(key);
            } else {
                editor.putString(key, original.get(key));
            }
        }
        editor.commit();
    }

    private static String[] keys() {
        return new String[] {AppSettings.KEY_ENGINE_KIND, AppSettings.KEY_FV_SCALE,
                AppSettings.KEY_FV_SCALE + "_" + EngineKind.HALFKP_512X2_8_64.id(),
                AppSettings.KEY_FV_SCALE + "_" + EngineKind.HALFKP_768X2_16_64.id()};
    }

    @Test
    public void allSelectableExecutablesArePackagedForTheDeviceAbi() {
        for (EngineKind kind : EngineKind.values()) {
            File executable = new File(context.getApplicationInfo().nativeLibraryDir,
                    "lib" + kind.executableName(Build.SUPPORTED_ABIS[0]) + ".so");
            assertTrue(executable.getName(), executable.isFile());
        }
    }

    @Test
    public void switchingEnginesRestoresIndependentScaleAndSurvivesRecreation() {
        assertEquals(EngineKind.DEFAULT, AppSettings.engineKind(context));
        assertEquals(20, AppSettings.fvScale(context));
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                SettingsFragment fragment = fragment(activity);
                ListPreference scale = fragment.findPreference(AppSettings.KEY_FV_SCALE);
                assertFalse(scale.isPersistent());
                assertEquals("20", scale.getValue());
                select(fragment, AppSettings.KEY_ENGINE_KIND, EngineKind.HALFKP_512X2_8_64.id());
                assertEquals("40", scale.getValue());
                select(fragment, AppSettings.KEY_FV_SCALE, "24");
                select(fragment, AppSettings.KEY_ENGINE_KIND, EngineKind.HALFKP_768X2_16_64.id());
                assertEquals("40", scale.getValue());
                select(fragment, AppSettings.KEY_ENGINE_KIND, EngineKind.DEFAULT.id());
                assertEquals("20", scale.getValue());
                select(fragment, AppSettings.KEY_ENGINE_KIND, EngineKind.HALFKP_512X2_8_64.id());
                assertEquals("24", scale.getValue());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                SettingsFragment fragment = fragment(activity);
                ListPreference kind = fragment.findPreference(AppSettings.KEY_ENGINE_KIND);
                ListPreference scale = fragment.findPreference(AppSettings.KEY_FV_SCALE);
                assertEquals(EngineKind.HALFKP_512X2_8_64.id(), kind.getValue());
                assertEquals("24", scale.getValue());
                assertEquals(24, AppSettings.fvScale(context));
                assertEquals("20", preferences.getString(AppSettings.KEY_FV_SCALE, null));
            });
        }
    }

    private static SettingsFragment fragment(SettingsActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        return (SettingsFragment) activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
    }

    private static void select(SettingsFragment fragment, String key, String value) {
        ListPreference preference = fragment.findPreference(key);
        assertTrue(preference.callChangeListener(value));
        preference.setValue(value);
    }
}
