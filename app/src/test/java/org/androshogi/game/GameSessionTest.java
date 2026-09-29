package org.androshogi.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.androshogi.shogi.Shogi;
import org.androshogi.storage.SavedGame;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class GameSessionTest {
    private final AtomicInteger ids = new AtomicInteger();
    private final AtomicLong time = new AtomicLong(100);

    private GameRecord empty() {
        return new GameRecord(Shogi.STARTING_SFEN, "Black", "White");
    }

    private GameSession session() {
        return new GameSession(empty(), () -> "id-" + ids.incrementAndGet(), time::get);
    }

    @Test public void untouchedScratchGameReusesItsIdButRefreshesCreationTime() {
        GameSession game = session();
        time.set(200);
        game.startNew(empty(), false);
        assertEquals("id-1", game.id());
        assertEquals(200, game.createdAt());

        game.startNew(empty(), true);
        assertEquals("id-2", game.id());
    }

    @Test public void analyzedOrModifiedGameGetsANewId() {
        GameSession game = session();
        game.analysis().put(0, PositionAnalysis.from(Shogi.STARTING_SFEN,
                Collections.emptyList(), null));
        assertFalse(game.isScratchGame());
        game.startNew(empty(), false);
        assertEquals("id-2", game.id());
        assertEquals(0, game.analysis().count());

        game.record().play(42);
        game.startNew(empty(), false);
        assertEquals("id-3", game.id());
    }

    @Test public void loadingReplacesStateWhileKeepingAnalysisReferenceForViews() {
        GameSession game = session();
        GameAnalysis viewAnalysis = game.analysis();
        GameRecord other = new GameRecord("other-start", "A", "B");
        GameAnalysis otherAnalysis = new GameAnalysis();
        otherAnalysis.put(0, PositionAnalysis.from("other-start", Collections.emptyList(), null));
        game.load(new SavedGame("loaded", 42, 70, other, otherAnalysis));

        assertEquals("loaded", game.id());
        assertEquals(42, game.createdAt());
        assertSame(other, game.record());
        assertSame(viewAnalysis, game.analysis());
        assertTrue(game.analysis().has(0));
        game.truncateAnalysis(0);
        assertEquals(0, viewAnalysis.count());
    }

    @Test public void snapshotIsDetachedFromLaterChanges() {
        GameSession game = session();
        game.record().play(42);
        game.analysis().put(1, PositionAnalysis.from("position", Collections.emptyList(), null));
        SavedGame saved = game.snapshot(300);
        game.record().play(43);
        game.analysis().clear();

        assertEquals("id-1", saved.id);
        assertEquals(100, saved.createdAt);
        assertEquals(300, saved.updatedAt);
        assertNotSame(game.record(), saved.record);
        assertEquals(1, saved.record.length());
        assertEquals(1, saved.analysis.count());
    }
}
