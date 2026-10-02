package org.androshogi.ui.main;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withSpinnerText;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.androshogi.R;
import org.androshogi.settings.AppSettings;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** The analysis dialog remembers its own MultiPV without changing manual analysis. */
@RunWith(AndroidJUnit4.class)
public class MainActivityAnalysisParametersTest {
    @Test
    public void selectionIsRememberedSeparatelyAndCancelLeavesSettingsIntact() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String[] keys = {AppSettings.KEY_MULTI_PV, AppSettings.KEY_ANALYSIS_MULTI_PV,
                AppSettings.KEY_ANALYSIS_TIME_MS};
        Map<String, ?> original = preferences.getAll();
        preferences.edit().putString(AppSettings.KEY_MULTI_PV, "3")
                .remove(AppSettings.KEY_ANALYSIS_MULTI_PV)
                .putInt(AppSettings.KEY_ANALYSIS_TIME_MS, 1000).commit();
        AtomicReference<int[]> selected = new AtomicReference<>();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.showParameterDialog(activity,
                    (time, multiPv, start) -> selected.set(new int[] {time, multiPv})));
            onView(withId(R.id.consider_multipv_spinner)).check(matches(withSpinnerText("3")));
            onView(withId(R.id.consider_multipv_spinner)).perform(click());
            onData(is("2")).inRoot(isPlatformPopup()).perform(click());
            onView(withText("OK")).perform(click());
            assertEquals(2, selected.get()[1]);
            assertEquals(1000, selected.get()[0]);
            assertEquals(2, AppSettings.analysisMultiPv(context));
            assertEquals(3, AppSettings.multiPv(context));

            preferences.edit().putString(AppSettings.KEY_MULTI_PV, "4").commit();
            selected.set(null);
            scenario.onActivity(activity -> activity.showParameterDialog(activity,
                    (time, multiPv, start) -> selected.set(new int[] {time, multiPv})));
            onView(withId(R.id.consider_multipv_spinner)).check(matches(withSpinnerText("2")));
            onView(withId(R.id.consider_multipv_spinner)).perform(click());
            onData(is("5")).inRoot(isPlatformPopup()).perform(click());
            onView(withText("Cancel")).perform(click());
            assertNull(selected.get());
            assertEquals(2, AppSettings.analysisMultiPv(context));
            assertEquals(4, AppSettings.multiPv(context));
        } finally {
            SharedPreferences.Editor editor = preferences.edit();
            for (String key : keys) {
                Object value = original.get(key);
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else {
                    editor.remove(key);
                }
            }
            editor.commit();
        }
    }
}
