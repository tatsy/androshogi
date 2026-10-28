package org.androshogi.ui.main;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.androshogi.ui.main.MainActivityMenuActions.menuItem;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.androshogi.R;
import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

/** The current move stays visible outside the tabs and follows the selected branch. */
@RunWith(AndroidJUnit4.class)
public class MainActivityCurrentMoveTest {
    private static final String HEADER = "手合割：平手\n先手：B\n後手：W\n"
            + "手数----指手---------消費時間--\n";
    private static final String KIF = HEADER + "1 ７六歩(77)\n2 ３四歩(33)\n3 ２六歩(27)\n";

    @Test public void movePlayedNotificationExcludesLoadingSeekingAndTakingBack() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicInteger played = new AtomicInteger();
            scenario.onActivity(activity -> {
                activity.parseShogiData(KIF);
                BoardView view = activity.findViewById(R.id.board_view);
                view.setOnMovePlayedListener(played::incrementAndGet);
                view.seekTo(0);
                assertEquals(0, played.get());
                view.forwardBoard();
                assertEquals(1, played.get());
                view.backwardBoard();
                assertEquals(1, played.get());
                view.seekTo(2);
                assertEquals(1, played.get());

                view.seekTo(1);
                Board position = new Board(view.getSFEN());
                try {
                    view.commitMove(Move.fromUSI(position, "8c8d"));
                } finally {
                    position.cleanup();
                }
                assertEquals(2, played.get());
            });
        }
    }

    @Test public void eventNameAppearsWithTheStartingPositionInTheRecordTab() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.parseShogiData(
                    "棋戦：第37期竜王戦\n" + HEADER));
            onView(withId(R.id.tab_record)).perform(click());
            onView(allOf(withId(R.id.move_event), withText("棋戦：第37期竜王戦")))
                    .check(matches(isDisplayed()));
            onView(allOf(withId(R.id.move_text), withText(R.string.move_list_start)))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void currentMoveFollowsNavigationAndRemainsVisibleOnEveryTab() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.parseShogiData(KIF));
            assertMove("3手目 ☗２六歩");
            for (int tab : new int[] {R.id.tab_comment, R.id.tab_record, R.id.tab_graph,
                    R.id.tab_pinned, R.id.tab_reading}) {
                onView(withId(tab)).perform(click());
                assertMove("3手目 ☗２六歩");
            }
            onView(withId(R.id.backward_button)).perform(click());
            assertMove("2手目 ☖３四歩");
            onView(withId(R.id.backward_button)).perform(click());
            assertMove("1手目 ☗７六歩");
            onView(withId(R.id.backward_button)).perform(click());
            onView(withId(R.id.current_move_view)).check(matches(withText(R.string.move_list_start)));
            onView(withId(R.id.forward_button)).perform(click());
            assertMove("1手目 ☗７六歩");
            scenario.onActivity(activity -> activity.parseShogiData(HEADER));
            onView(withId(R.id.current_move_view)).check(matches(withText(R.string.move_list_start)));
        }
    }

    @Test
    public void branchEditsDeletionAndUndoShowTheSelectedMoveAtTheSamePly() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.parseShogiData(KIF);
                BoardView view = activity.findViewById(R.id.board_view);
                view.seekTo(1);
                Board position = new Board(view.getSFEN());
                try {
                    view.commitMove(Move.fromUSI(position, "8c8d"));
                } finally {
                    position.cleanup();
                }
            });
            assertMove("2手目 ☖８四歩");
            onView(withId(R.id.backward_button)).perform(longClick());
            assertMove("1手目 ☗７六歩");
            onView(withId(R.id.menu_button)).perform(click());
            menuItem(R.id.undo_delete_move).perform(click());
            assertMove("2手目 ☖８四歩");
            onView(withId(R.id.menu_button)).perform(click());
            menuItem(R.id.return_main_line).perform(click());
            assertMove("1手目 ☗７六歩");
            onView(withId(R.id.forward_button)).perform(click());
            assertMove("2手目 ☖３四歩");
        }
    }

    @Test
    public void promotedMovesAndSameSquareNotationMatchTheMoveList() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.parseShogiData(HEADER
                    + "1 ７六歩(77)\n2 ３四歩(33)\n3 ２二角成(88)\n4 同銀(31)\n"));
            assertMove("4手目 ☖同銀");
            onView(withId(R.id.backward_button)).perform(click());
            assertMove("3手目 ☗２二角成");
        }
    }

    private static void assertMove(String expected) {
        onView(withId(R.id.current_move_view)).check(matches(withText(expected)))
                .check(matches(isDisplayed()));
    }
}
