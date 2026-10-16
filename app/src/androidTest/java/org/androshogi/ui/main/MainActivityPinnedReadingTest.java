package org.androshogi.ui.main;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.androshogi.R;
import org.androshogi.engine.EngineInfo;
import org.androshogi.engine.EngineSession;
import org.androshogi.engine.EvaluationFileStore;
import org.androshogi.game.GameSession;
import org.androshogi.game.PositionAnalysis;
import org.androshogi.shogi.Board;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/** Pinned readings are snapshots, independent of navigation and subsequent searches. */
@RunWith(AndroidJUnit4.class)
public class MainActivityPinnedReadingTest {
    private static final String KIF = "手合割：平手\n先手：B\n後手：W\n"
            + "手数----指手---------消費時間--\n1 ７六歩(77)\n2 ３四歩(33)\n";

    @Test
    public void snapshotSurvivesNavigationAndUpdatesAndCanBeReplaced() {
        AtomicReference<String> pinned = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.parseShogiData(KIF);
                assertFalse(activity.findViewById(R.id.pin_reading_button).isEnabled());
                showCandidates(activity, 120);
                assertTrue(activity.findViewById(R.id.pin_reading_button).isEnabled());
                pinned.set(activity.getString(R.string.pinned_reading_title, 2) + "\n\n"
                        + ((TextView) activity.findViewById(R.id.engine_view)).getText());
            });
            onView(withId(R.id.pin_reading_button)).perform(click());
            onView(withId(R.id.pinned_reading_view)).check(matches(withText(pinned.get())));
            scenario.onActivity(activity -> {
                assertEquals(R.id.tab_pinned, tabs(activity).getCheckedButtonId());
                // Even a fresh search update for the same position must not change the snapshot or tab.
                EngineView reading = activity.findViewById(R.id.engine_view);
                BoardView board = activity.findViewById(R.id.board_view);
                Board position = new Board(board.getSFEN());
                try {
                    reading.onEngineUpdated(position, candidates(480));
                } finally {
                    position.cleanup();
                }
                assertEquals(R.id.tab_pinned, tabs(activity).getCheckedButtonId());
            });
            onView(withId(R.id.backward_button)).perform(click());
            onView(withId(R.id.pinned_reading_view)).check(matches(withText(pinned.get())));
            scenario.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.pin_reading_button).isEnabled());
                ((BoardView) activity.findViewById(R.id.board_view)).seekTo(2);
                showCandidates(activity, 480);
                tabs(activity).check(R.id.tab_reading);
                pinned.set(activity.getString(R.string.pinned_reading_title, 2) + "\n\n"
                        + ((TextView) activity.findViewById(R.id.engine_view)).getText());
            });
            onView(withId(R.id.pin_reading_button)).perform(click());
            onView(withId(R.id.pinned_reading_view)).check(matches(withText(pinned.get())));
            scenario.onActivity(activity -> activity.parseShogiData(KIF));
            onView(withId(R.id.pinned_reading_view)).check(matches(withText(R.string.pinned_reading_empty)));
        }
    }

    @Test
    public void bothReadingsScrollAndAnUpdatePreservesTheReadingScrollPosition() {
        AtomicInteger previousScroll = new AtomicInteger();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.parseShogiData(KIF);
                showCandidates(activity, 120);
                // Force a small viewport so this also exercises scrolling on large emulators.
                ScrollView scroll = activity.findViewById(R.id.reading_scroll);
                scroll.getLayoutParams().height = 60;
                ((android.widget.LinearLayout.LayoutParams) scroll.getLayoutParams()).weight = 0;
                scroll.requestLayout();
            });
            onView(withId(R.id.reading_scroll)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                ScrollView scroll = activity.findViewById(R.id.reading_scroll);
                assertTrue(scroll.canScrollVertically(1));
                scroll.scrollTo(0, 30);
                assertTrue(scroll.getScrollY() > 0);
                int before = scroll.getScrollY();
                previousScroll.set(before);
                Board position = new Board(((BoardView) activity.findViewById(R.id.board_view)).getSFEN());
                try {
                    ((EngineView) activity.findViewById(R.id.engine_view))
                            .onEngineUpdated(position, candidates(240));
                } finally {
                    position.cleanup();
                }
                assertEquals(before, scroll.getScrollY());
            });
            onView(withId(R.id.reading_scroll)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> assertEquals(previousScroll.get(),
                    activity.findViewById(R.id.reading_scroll).getScrollY()));
            onView(withId(R.id.pin_reading_button)).perform(click());
            scenario.onActivity(activity -> {
                ScrollView scroll = activity.findViewById(R.id.pinned_panel);
                scroll.getLayoutParams().height = 60;
                scroll.requestLayout();
            });
            onView(withId(R.id.pinned_panel)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                ScrollView scroll = activity.findViewById(R.id.pinned_panel);
                assertTrue(scroll.canScrollVertically(1));
                scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
                assertTrue(scroll.getScrollY() > 0);
                assertFalse(scroll.canScrollVertically(1));
                ((BoardView) activity.findViewById(R.id.board_view)).backwardBoard();
                assertEquals(0, activity.findViewById(R.id.reading_scroll).getScrollY());
            });
        }
    }

    @Test
    public void onlyAnAcceptedSearchSwitchesToReadingAndKeepsThePin() {
        AtomicReference<File> temporaryModel = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.parseShogiData(KIF);
                showCandidates(activity, 120);
                activity.findViewById(R.id.pin_reading_button).performClick();
                String pinned = ((TextView) activity.findViewById(R.id.pinned_reading_view)).getText().toString();
                try {
                    Field field = MainActivity.class.getDeclaredField("engine");
                    field.setAccessible(true);
                    ((EngineSession) field.get(activity)).shutdown();
                    FakeEngine engine = new FakeEngine(activity.getCacheDir());
                    field.set(activity, engine);
                    File model = EvaluationFileStore.file(activity);
                    assertTrue(model.getParentFile().isDirectory() || model.getParentFile().mkdirs());
                    if (model.createNewFile()) temporaryModel.set(model);

                    engine.acceptSearch = false;
                    activity.findViewById(R.id.hint_button).performClick();
                    assertEquals(R.id.tab_pinned, tabs(activity).getCheckedButtonId());
                    engine.acceptSearch = true;
                    activity.findViewById(R.id.hint_button).performClick();
                    assertEquals(R.id.tab_reading, tabs(activity).getCheckedButtonId());
                    assertEquals(pinned, ((TextView) activity.findViewById(R.id.pinned_reading_view))
                            .getText().toString());
                    tabs(activity).check(R.id.tab_pinned);
                    // Stopping a search must not take the user away from the pinned reading.
                    activity.findViewById(R.id.hint_button).performClick();
                    assertEquals(R.id.tab_pinned, tabs(activity).getCheckedButtonId());
                } catch (ReflectiveOperationException | java.io.IOException e) {
                    throw new AssertionError(e);
                }
            });
        } finally {
            if (temporaryModel.get() != null) assertTrue(temporaryModel.get().delete());
        }
    }

    private static MaterialButtonToggleGroup tabs(MainActivity activity) {
        return activity.findViewById(R.id.info_tabs);
    }

    private static void showCandidates(MainActivity activity, int score) {
        try {
            Field field = MainActivity.class.getDeclaredField("game");
            field.setAccessible(true);
            GameSession game = (GameSession) field.get(activity);
            BoardView board = activity.findViewById(R.id.board_view);
            game.analysis().put(2, PositionAnalysis.from(board.getSFEN(), candidates(score), "2g2f"));
            board.seekTo(2);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static List<EngineInfo> candidates(int score) {
        List<EngineInfo> infos = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            infos.add(EngineInfo.parse("info depth 12 multipv " + i + " score cp " + (score - i)
                    + " pv 2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 7i6h 3a4b 5i6i 5a6b"));
        }
        return infos;
    }

    private static final class FakeEngine extends EngineSession {
        boolean acceptSearch;
        boolean searching;
        FakeEngine(File directory) { super("unused", directory.getAbsolutePath()); }
        @Override public State getState() { return State.READY; }
        @Override public boolean isSearching() { return searching; }
        @Override public boolean startSearch(String sfen, int timeMs, SearchCallback callback) {
            searching = acceptSearch;
            return acceptSearch;
        }
        @Override public void stopSearch() { searching = false; }
    }
}
