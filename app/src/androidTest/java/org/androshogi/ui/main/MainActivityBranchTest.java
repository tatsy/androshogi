package org.androshogi.ui.main;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.androshogi.R;
import org.androshogi.game.GameRecord;
import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Covers branch arrows, menu routing, and the native undo stack after returning to a fork. */
@RunWith(AndroidJUnit4.class)
public class MainActivityBranchTest {
    @Test
    public void createsSelectsAndReturnsFromAVariationWithoutLosingMainMoves() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.board_view)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                activity.parseShogiData("手合割：平手\n先手：B\n後手：W\n"
                        + "手数----指手---------消費時間--\n"
                        + "1 ７六歩(77)\n2 ３四歩(33)\n3 ２六歩(27)\n");
                BoardView view = activity.findViewById(R.id.board_view);
                assertEquals(3, view.getRecord().length());
                view.seekTo(1);
                assertArrows(view, true, false);
                play(view, "8c8d");
                assertEquals(5, view.getRecord().nodes().size());
                assertPosition(view, "7g7f", "8c8d");
                assertArrows(view, false, false);
            });

            onView(withId(R.id.menu_button)).perform(click());
            onView(withText(R.string.return_main_line)).perform(click());
            scenario.onActivity(activity -> {
                BoardView view = activity.findViewById(R.id.board_view);
                assertEquals(3, view.getRecord().length());
                assertPosition(view, "7g7f");
                assertArrows(view, true, true);
                view.flipUpsideDown();
                assertArrows(view, true, true);
                view.flipUpsideDown();
                view.forwardBoard();
                assertPosition(view, "7g7f", "3c3d");
                view.backwardBoard();
                assertPosition(view, "7g7f");
            });

            onView(withId(R.id.tab_record)).perform(click());
            onView(withText("☗７六歩（分岐あり）")).check(matches(isDisplayed()));
            onView(withId(R.id.menu_button)).perform(click());
            onView(withText(R.string.select_branch)).perform(click());
            onView(withText("☖３四歩（元の手順）")).check(matches(isDisplayed()));
            onView(withText("☖８四歩")).perform(click());
            scenario.onActivity(activity -> {
                BoardView view = activity.findViewById(R.id.board_view);
                assertPosition(view, "7g7f", "8c8d");
                play(view, "7f7e");
                assertPosition(view, "7g7f", "8c8d", "7f7e");
                assertEquals(3, view.getRecord().currentPly());
            });

            // Returning from the leaf must stop at the fork, with a valid native undo stack.
            onView(withId(R.id.menu_button)).perform(click());
            onView(withText(R.string.return_main_line)).perform(click());
            scenario.onActivity(activity -> {
                BoardView view = activity.findViewById(R.id.board_view);
                GameRecord record = view.getRecord();
                assertEquals(1, record.currentPly());
                assertEquals(6, record.nodes().size());
                assertPosition(view, "7g7f");
                assertArrows(view, true, true);
                view.backwardBoard();
                assertPosition(view);
                assertArrows(view, true, false);
                view.forwardBoard();
                assertPosition(view, "7g7f");
                view.forwardBoard();
                assertPosition(view, "7g7f", "3c3d");
                view.forwardBoard();
                assertPosition(view, "7g7f", "3c3d", "2g2f");
            });
        }
    }

    /** Tests the actual rendered arrows, including disappearance after leaving a branch point. */
    private static void assertArrows(BoardView view, boolean selectedExpected, boolean variationExpected) {
        assertTrue(view.getWidth() > 0 && view.getHeight() > 0);
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(bitmap));
            int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            int blue = 0, green = 0;
            for (int pixel : pixels) {
                int r = Color.red(pixel), g = Color.green(pixel), b = Color.blue(pixel);
                if (b > r + 25 && b > g + 15) blue++;
                if (g > r + 20 && g > b + 15) green++;
            }
            assertEquals("Selected continuation arrow", selectedExpected, blue > 20);
            assertEquals("Other continuation arrows", variationExpected, green > 20);
        } finally {
            bitmap.recycle();
        }
    }

    private static void play(BoardView view, String usi) {
        Board board = new Board(view.getSFEN());
        try {
            int move = Move.fromUSI(board, usi);
            assertNotEquals(Shogi.MOVE_NONE, move);
            view.commitMove(move);
        } finally {
            board.cleanup();
        }
    }

    private static void assertPosition(BoardView view, String... moves) {
        Board expected = new Board();
        try {
            for (String move : moves) expected.pushUSI(move);
            assertEquals(expected.getSFEN(), view.getSFEN());
            assertEquals(moves.length, view.getRecord().currentPly());
        } finally {
            expected.cleanup();
        }
    }
}
