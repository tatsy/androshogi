package org.androshogi.ui.main;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.androshogi.ui.main.MainActivityMenuActions.menuItem;
import static org.junit.Assert.*;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.androshogi.R;
import org.androshogi.engine.EngineInfo;
import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;
import org.androshogi.game.PositionAnalysis;
import org.androshogi.kifu.KifParser;
import org.androshogi.kifu.KifWriter;
import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;
import org.androshogi.storage.GameJson;
import org.androshogi.storage.SavedGame;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/** Real clipboard/menu input, native board operations and position-tree persistence. */
@RunWith(AndroidJUnit4.class)
public class MainActivitySfenImportTest {
    private static final String SFEN =
            "ln1gk2nl/1r4g2/3pp2pp/5sP2/2S2Pp1P/PPp2SbR1/1S1PP4/2G6/LN2KG1NL w B2P3p 1";
    private static final String KIF = "手合割：平手\n先手：B\n後手：W\n"
            + "手数----指手---------消費時間--\n1 ７六歩(77)\n";

    @Test public void pastesBookPositionAndKeepsNavigationBranchesAndAnalysisThroughStorage() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.parseShogiData(KIF));
            paste(scenario, "position sfen " + SFEN.replace(" 1", " 65"));
            onView(withId(R.id.current_move_view)).check(matches(withText(R.string.move_list_start)));
            scenario.onActivity(a -> {
                BoardView view = a.findViewById(R.id.board_view);
                GameRecord record = view.getRecord();
                assertEquals(SFEN, view.getSFEN());
                assertEquals(0, record.currentPly());
                assertEquals(0, record.length());
                RecyclerView list = a.findViewById(R.id.move_list_view);
                assertEquals(1, list.getAdapter().getItemCount());
                view.backwardBoard();
                assertEquals(SFEN, view.getSFEN());
                // Both the engine's local search board and UI accept this starting position.
                Board position = new Board(SFEN);
                try {
                    assertEquals(Shogi.WHITE, position.turn());
                    int first = Move.fromUSI(position, "8b8d");
                    assertTrue(position.isLegal(first));
                    view.commitMove(first);
                    view.seekTo(0);
                    view.commitMove(Move.fromUSI(position, "8b8e"));
                } finally {
                    position.cleanup();
                }
                assertEquals(2, record.node(GameRecord.ROOT_ID).childIds().size());
                assertEquals(1, record.currentPly());
                assertEquals(2, list.getAdapter().getItemCount());

                GameAnalysis analysis = new GameAnalysis(record);
                analysis.put(0, PositionAnalysis.from(SFEN, Collections.singletonList(
                        EngineInfo.parse("info depth 10 score cp 100 pv 8b8d")), "8b8d"));
                try {
                    SavedGame restored = GameJson.read(GameJson.write(
                            new SavedGame("sfen-test", 100, 200, record, analysis)));
                    assertEquals(SFEN, restored.record.startSfen());
                    assertEquals(record.currentNodeId(), restored.record.currentNodeId());
                    assertEquals(2, restored.record.node(GameRecord.ROOT_ID).childIds().size());
                    assertNotNull(restored.analysis.get(0));
                    assertEquals(-100, restored.analysis.get(0).scoreForBlack());
                    Board replay = new Board(restored.record.startSfen());
                    try {
                        replay.push(restored.record.move(0));
                        assertEquals(view.getSFEN(), replay.getSFEN());
                    } finally {
                        replay.cleanup();
                    }
                    KifParser kif = KifParser.parse(KifWriter.write(restored.record, 0));
                    assertEquals(SFEN, kif.getSFEN());
                    assertEquals(1, kif.getMoves().size());
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
            onView(withId(R.id.current_move_view)).check(matches(withText("1手目 ☖８五飛")));
            onView(withId(R.id.backward_button)).perform(click());
            onView(withId(R.id.current_move_view)).check(matches(withText(R.string.move_list_start)));
            onView(withId(R.id.forward_button)).perform(click());
            onView(withId(R.id.current_move_view)).check(matches(withText("1手目 ☖８五飛")));
        }
    }

    @Test public void invalidSfenPreservesTheCurrentGameAndClipboardKifStillWorks() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.parseShogiData(KIF));
            GameRecord[] before = new GameRecord[1];
            scenario.onActivity(a -> before[0] = ((BoardView) a.findViewById(R.id.board_view)).getRecord());
            paste(scenario, "sfen " + SFEN.replace("k", "1"));
            onView(withText(R.string.sfen_parse_error_title)).check(matches(withText(R.string.sfen_parse_error_title)));
            onView(withText(android.R.string.ok)).perform(click());
            scenario.onActivity(a -> assertSame(before[0],
                    ((BoardView) a.findViewById(R.id.board_view)).getRecord()));
            onView(withId(R.id.current_move_view)).check(matches(withText("1手目 ☗７六歩")));
            paste(scenario, KIF.replace("７六歩(77)", "２六歩(27)"));
            onView(withId(R.id.current_move_view)).check(matches(withText("1手目 ☗２六歩")));
        }
    }

    @Test public void importedPositionCanBeCopiedAsKifBeforePlayingAnyMoves() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.parseShogiData(KIF));
            paste(scenario, SFEN);
            onView(withId(R.id.menu_button)).perform(click());
            menuItem(R.id.copy_kifu).perform(click());
            scenario.onActivity(a -> {
                ClipboardManager clipboard = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                KifParser kif = KifParser.parse(clipboard.getPrimaryClip().getItemAt(0).getText().toString());
                assertEquals(SFEN, kif.getSFEN());
                assertTrue(kif.getMoves().isEmpty());
            });
        }
    }

    private static void paste(ActivityScenario<MainActivity> scenario, String text) {
        scenario.onActivity(a -> ((ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("test", text)));
        onView(withId(R.id.menu_button)).perform(click());
        menuItem(R.id.paste_kifu).perform(click());
    }
}
