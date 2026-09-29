package org.androshogi.ui.main;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.action.ViewActions.click;

import static org.junit.Assume.assumeFalse;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.androshogi.R;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** The board remains usable without evaluation data; only engine requests show an error. */
@RunWith(AndroidJUnit4.class)
public class MainActivityMissingEvaluationTest {
    @Test
    public void opensBoardAndShowsDialogWhenHintIsRequested() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeFalse(new File(new File(context.getFilesDir(), "eval"), "nn.bin").isFile());

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.board_view)).check(matches(isDisplayed()));
            onView(withText(R.string.engine_eval_missing_title)).check(doesNotExist());

            onView(withId(R.id.hint_button)).perform(click());
            onView(withText(R.string.engine_eval_missing_title)).check(matches(isDisplayed()));
            onView(withText(R.string.engine_eval_missing_message)).check(matches(isDisplayed()));
        }
    }
}
