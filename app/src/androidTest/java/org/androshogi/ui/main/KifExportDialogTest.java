package org.androshogi.ui.main;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.not;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.androshogi.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class KifExportDialogTest {
    @Test public void optionsAreExplicitAndCancelDoesNotStartSaving() {
        AtomicReference<int[]> choice = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> KifExportDialog.show(activity, true,
                    (format, include) -> choice.set(new int[] {format, include ? 1 : 0})));
            onView(withId(R.id.kif_include_analysis)).inRoot(isDialog()).check(matches(not(isChecked())));
            onView(withId(R.id.kif_export_utf8)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.kif_include_analysis)).inRoot(isDialog()).perform(click());
            onView(withText(R.string.kifu_export_save)).inRoot(isDialog()).perform(click());
            assertArrayEquals(new int[] {1, 1}, choice.get());
            choice.set(null);
            scenario.onActivity(activity -> KifExportDialog.show(activity, true,
                    (format, include) -> choice.set(new int[] {format, include ? 1 : 0})));
            onView(withId(R.id.kif_include_analysis)).inRoot(isDialog()).check(matches(not(isChecked())));
            onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click());
            assertNull(choice.get());
        }
    }

    @Test public void noStoredResultsDisablesOnlyTheAnalysisOption() {
        AtomicReference<int[]> choice = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> KifExportDialog.show(activity, false,
                    (format, include) -> choice.set(new int[] {format, include ? 1 : 0})));
            onView(withId(R.id.kif_include_analysis)).inRoot(isDialog()).check(matches(not(isEnabled())));
            onView(withId(R.id.kif_no_analysis)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.kifu_export_save)).inRoot(isDialog()).perform(click());
            assertArrayEquals(new int[] {0, 0}, choice.get());
        }
    }
}
