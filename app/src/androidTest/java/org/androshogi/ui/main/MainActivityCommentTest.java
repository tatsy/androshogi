package org.androshogi.ui.main;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.androshogi.R;
import org.androshogi.game.GameRecord;
import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises comment display through KIF import, navigation, and variation selection. */
@RunWith(AndroidJUnit4.class)
public class MainActivityCommentTest {
    private static final String HEADER = "手合割：平手\n先手：B\n後手：W\n"
            + "手数----指手---------消費時間--\n";

    @Test
    public void commentsFollowMovesAndTabsWithoutLeakingIntoAnotherGame() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.board_view)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                MaterialButtonToggleGroup tabs = activity.findViewById(R.id.info_tabs);
                assertEquals(R.id.tab_reading, tabs.getCheckedButtonId());
                assertEquals(R.id.tab_comment, tabs.getChildAt(0).getId());
                activity.parseShogiData(HEADER + "1 ７六歩(77)\n*最初のコメント\n*続きの行\n"
                        + "2 ３四歩(33)\n3 ２六歩(27)\n*最後のコメント\n");
                TextView text = activity.findViewById(R.id.comment_view);
                assertTrue(text.isTextSelectable());
                assertFalse(text.onCheckIsTextEditor());
            });

            onView(withId(R.id.tab_comment)).perform(click());
            onView(withId(R.id.comment_view)).check(matches(withText("最後のコメント")));
            onView(withId(R.id.backward_button)).perform(click());
            onView(withId(R.id.comment_view)).check(matches(withText(R.string.comment_empty)));
            onView(withId(R.id.backward_button)).perform(click());
            onView(withId(R.id.comment_view)).check(matches(withText("最初のコメント\n続きの行")));
            onView(withId(R.id.backward_button)).perform(click());
            onView(withId(R.id.comment_view)).check(matches(withText(R.string.comment_empty)));
            onView(withId(R.id.forward_button)).perform(click());
            onView(withId(R.id.comment_view)).check(matches(withText("最初のコメント\n続きの行")));

            onView(withId(R.id.tab_reading)).perform(click());
            onView(withId(R.id.engine_view)).check(matches(isDisplayed()));
            onView(withId(R.id.forward_button)).perform(click());
            onView(withId(R.id.tab_comment)).perform(click());
            onView(withId(R.id.comment_view)).check(matches(withText(R.string.comment_empty)));

            scenario.onActivity(activity -> activity.parseShogiData(HEADER + "1 ２六歩(27)\n"));
            onView(withId(R.id.comment_view)).check(matches(withText(R.string.comment_empty)));
        }
    }

    @Test
    public void longCommentsScrollAndBranchSwitchesShowTheirOwnComment() {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 80; i++) lines.append("*長いコメント ").append(i).append('\n');
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.board_view)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> activity.parseShogiData(HEADER
                    + "1 ７六歩(77)\n" + lines + "2 ３四歩(33)\n*本譜のコメント\n"));
            onView(withId(R.id.tab_comment)).perform(click());
            onView(withId(R.id.backward_button)).perform(click());
            scenario.onActivity(activity -> {
                ScrollView panel = activity.findViewById(R.id.comment_panel);
                assertTrue(panel.canScrollVertically(1));
                panel.scrollTo(0, panel.getChildAt(0).getHeight());
                assertTrue(panel.getScrollY() > 0);
            });
            onView(withId(R.id.forward_button)).perform(click());
            onView(withId(R.id.comment_view)).check(matches(withText("本譜のコメント")));
            scenario.onActivity(activity -> {
                ScrollView panel = activity.findViewById(R.id.comment_panel);
                assertEquals(0, panel.getScrollY());
                BoardView view = activity.findViewById(R.id.board_view);
                GameRecord record = view.getRecord();
                Board fork = new Board(record.startSfen());
                try {
                    fork.push(record.move(0));
                    record.addVariation(record.nodeIdAtPly(1), Move.fromUSI(fork, "8c8d"),
                            0, "分岐のコメント");
                } finally {
                    fork.cleanup();
                }
                view.seekTo(1);
            });

            onView(withId(R.id.menu_button)).perform(click());
            onView(withText(R.string.select_branch)).perform(click());
            onView(withText("☖８四歩")).perform(click());
            onView(withId(R.id.comment_view)).check(matches(withText("分岐のコメント")));
            onView(withId(R.id.menu_button)).perform(click());
            onView(withText(R.string.return_main_line)).perform(click());
            onView(withId(R.id.forward_button)).perform(click());
            onView(withId(R.id.comment_view)).check(matches(withText("本譜のコメント")));
        }
    }
}
