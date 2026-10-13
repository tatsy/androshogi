package org.androshogi.ui.main;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.androshogi.ui.main.MainActivityMenuActions.menuItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.androshogi.R;
import org.androshogi.game.GameRecord;
import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.androshogi.storage.GameStore;
import org.androshogi.storage.SavedGame;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class MainActivityLeafDeletionTest {
    private static final String KIF = "手合割：平手\n先手：削除検証\n後手：W\n"
            + "手数----指手---------消費時間--\n"
            + "1 ７六歩(77)\n*初手コメント\n2 ３四歩(33)\n*末端コメント\n";

    private void menu(int id) {
        onView(withId(R.id.menu_button)).perform(click());
        menuItem(id).perform(click());
    }

    private void assertUndoEnabled(boolean enabled) {
        onView(withId(R.id.menu_button)).perform(click());
        menuItem(R.id.undo_delete_move).check(matches(enabled ? isEnabled() : not(isEnabled())));
        pressBack();
    }

    private void assertNoDeletionPopup() {
        onView(withId(com.google.android.material.R.id.snackbar_text)).check(doesNotExist());
    }

    private void assertPosition(BoardView view, String... moves) {
        Board expected = new Board();
        try {
            for (String move : moves) expected.pushUSI(move);
            assertEquals(expected.getSFEN(), view.getSFEN());
            assertEquals(moves.length, view.getRecord().currentPly());
        } finally {
            expected.cleanup();
        }
    }

    private long addBranch(BoardView view, String usi) {
        GameRecord record = view.getRecord();
        Board fork = new Board(record.startSfen());
        try {
            fork.pushUSI("7g7f");
            return record.addVariation(record.nodeIdAtPly(1), Move.fromUSI(fork, usi), 0, "分岐");
        } finally {
            fork.cleanup();
        }
    }

    @Test public void deletesOneMoveAndUndoRebuildsTheNativeBoardAndComments() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.parseShogiData(KIF));
            assertUndoEnabled(false);
            onView(withId(R.id.backward_button)).perform(click());
            onView(withId(R.id.backward_button)).perform(longClick());
            scenario.onActivity(activity -> {
                BoardView view = activity.findViewById(R.id.board_view);
                assertPosition(view, "7g7f");
                assertEquals(2, view.getRecord().length());
            });
            assertNoDeletionPopup();
            onView(withId(R.id.menu_button)).perform(click());
            menuItem(R.id.delete_last_move).check(matches(not(isEnabled())));
            pressBack();
            scenario.onActivity(activity -> ((BoardView) activity.findViewById(R.id.board_view)).seekTo(0));
            onView(withId(R.id.backward_button)).perform(longClick());
            scenario.onActivity(activity -> assertPosition(activity.findViewById(R.id.board_view)));
            assertNoDeletionPopup();
            onView(withId(R.id.menu_button)).perform(click());
            menuItem(R.id.delete_last_move).check(matches(not(isEnabled())));
            pressBack();
            scenario.onActivity(activity -> ((BoardView) activity.findViewById(R.id.board_view)).seekTo(2));
            onView(withId(R.id.backward_button)).perform(longClick());
            assertNoDeletionPopup();
            scenario.onActivity(activity -> {
                BoardView view = activity.findViewById(R.id.board_view);
                assertPosition(view, "7g7f");
                assertEquals(1, view.getRecord().length());
                assertEquals(2, view.getRecord().nodes().size());
                assertEquals("初手コメント", view.getRecord().comment(0));
            });
            scenario.onActivity(activity -> ((BoardView) activity.findViewById(R.id.board_view)).seekTo(0));
            assertUndoEnabled(true);
            menu(R.id.undo_delete_move);
            scenario.onActivity(activity -> {
                BoardView view = activity.findViewById(R.id.board_view);
                assertPosition(view, "7g7f", "3c3d");
                assertEquals("末端コメント", view.getRecord().comment(1));
                view.backwardBoard();
                assertPosition(view, "7g7f");
                view.forwardBoard();
                assertPosition(view, "7g7f", "3c3d");
            });
            assertUndoEnabled(false);
        }
    }

    @Test public void soleRemainingBranchStaysNavigableAfterReturningToEndedMain() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.parseShogiData(KIF);
                addBranch(activity.findViewById(R.id.board_view), "8c8d");
            });
            menu(R.id.delete_last_move);
            onView(withId(R.id.forward_button)).perform(click());
            scenario.onActivity(activity -> assertPosition(activity.findViewById(R.id.board_view), "7g7f", "8c8d"));
            menu(R.id.return_main_line);
            scenario.onActivity(activity -> {
                BoardView view = activity.findViewById(R.id.board_view);
                assertPosition(view, "7g7f");
                assertEquals(1, view.getRecord().length());
                assertNull(view.getRecord().node(view.getRecord().currentNodeId()).mainChildId());
            });
            onView(withId(R.id.forward_button)).perform(click());
            scenario.onActivity(activity -> assertPosition(activity.findViewById(R.id.board_view), "7g7f", "8c8d"));
            assertUndoEnabled(true);
            menu(R.id.undo_delete_move);
            scenario.onActivity(activity -> assertPosition(activity.findViewById(R.id.board_view), "7g7f", "3c3d"));
        }
    }

    @Test public void repeatedDeletionKeepsOnlyLastUndoAndInvalidatesItOnEditsAndGameChange() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.parseShogiData(KIF));
            onView(withId(R.id.backward_button)).perform(longClick());
            onView(withId(R.id.backward_button)).perform(longClick());
            // An unavailable long press must not discard the last successful deletion.
            onView(withId(R.id.backward_button)).perform(longClick());
            assertNoDeletionPopup();
            scenario.onActivity(activity -> assertPosition(activity.findViewById(R.id.board_view)));
            assertUndoEnabled(true);
            menu(R.id.undo_delete_move);
            scenario.onActivity(activity -> {
                BoardView view = activity.findViewById(R.id.board_view);
                assertPosition(view, "7g7f");
                assertEquals(1, view.getRecord().length());
                assertEquals("初手コメント", view.getRecord().comment(0));
            });
            assertUndoEnabled(false);
            menu(R.id.delete_last_move);
            scenario.onActivity(activity -> {
                BoardView view = activity.findViewById(R.id.board_view);
                Board board = new Board(view.getSFEN());
                try {
                    view.commitMove(Move.fromUSI(board, "7g7f"));
                } finally {
                    board.cleanup();
                }
            });
            assertUndoEnabled(false);
            menu(R.id.delete_last_move);
            assertNoDeletionPopup();
            onView(withId(R.id.black_info_view)).perform(click());
            onView(withId(R.id.black_player_name)).perform(replaceText("編集済み"));
            onView(withText(R.string.save_player_names)).perform(click());
            assertUndoEnabled(false);
            scenario.onActivity(activity -> activity.parseShogiData(KIF));
            menu(R.id.delete_last_move);
            assertUndoEnabled(true);
            menu(R.id.new_game);
            assertUndoEnabled(false);
        }
    }

    @Test public void unselectedForkSurvivesSaveReloadAndForwardOpensChoice() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.parseShogiData(KIF);
                BoardView view = activity.findViewById(R.id.board_view);
                addBranch(view, "8c8d");
                addBranch(view, "4c4d");
            });
            menu(R.id.delete_last_move);
            GameStore store = new GameStore(ApplicationProvider.getApplicationContext());
            long deadline = SystemClock.uptimeMillis() + 5000;
            SavedGame saved;
            do {
                saved = store.loadLast();
                if (saved != null && "削除検証".equals(saved.record.blackName())
                        && saved.record.nodes().size() == 4 && saved.record.length() == 1
                        && saved.record.node(saved.record.currentNodeId()).childIds().size() == 2) break;
                SystemClock.sleep(20);
            } while (SystemClock.uptimeMillis() < deadline);
            assertNotNull(saved);
            assertEquals(4, saved.record.nodes().size());
            scenario.recreate();
            AtomicBoolean restored = new AtomicBoolean();
            deadline = SystemClock.uptimeMillis() + 5000;
            do {
                scenario.onActivity(activity -> {
                    GameRecord r = ((BoardView) activity.findViewById(R.id.board_view)).getRecord();
                    restored.set("削除検証".equals(r.blackName()) && r.nodes().size() == 4);
                });
                if (restored.get()) break;
                SystemClock.sleep(20);
            } while (SystemClock.uptimeMillis() < deadline);
            assertTrue(restored.get());
            scenario.onActivity(activity -> {
                BoardView view = activity.findViewById(R.id.board_view);
                assertPosition(view, "7g7f");
                assertFalse(view.getRecord().canDeleteCurrentLeaf());
                assertNull(view.getRecord().node(view.getRecord().currentNodeId()).mainChildId());
            });
            onView(withId(R.id.forward_button)).perform(click());
            onView(withText("☖８四歩")).perform(click());
            scenario.onActivity(activity -> assertPosition(activity.findViewById(R.id.board_view), "7g7f", "8c8d"));
            menu(R.id.return_main_line);
            scenario.onActivity(activity -> assertPosition(activity.findViewById(R.id.board_view), "7g7f"));
            onView(withId(R.id.forward_button)).perform(click());
            onView(withText("☖４四歩")).perform(click());
            scenario.onActivity(activity -> assertPosition(activity.findViewById(R.id.board_view), "7g7f", "4c4d"));
        }
    }
}
