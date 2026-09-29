package org.androshogi.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;

import org.androshogi.game.GameRecord;
import org.androshogi.game.GameSession;
import org.androshogi.game.PositionAnalysis;
import org.junit.Test;

import java.util.Collections;

public class GameSessionResultTest {
    @Test public void onlyCompletedResultsForTheCurrentPositionReplaceStoredAnalysis() {
        GameRecord record = new GameRecord("position", "Black", "White");
        GameSession game = new GameSession(record, () -> "id", () -> 100);
        EngineSession.SearchResult first = result("position", false);
        assertTrue(game.storeResult(record, 0, "position", first));
        PositionAnalysis stored = game.analysis().get(0);

        assertFalse(game.storeResult(record, 0, "position", result("position", true)));
        assertFalse(game.storeResult(record, 0, "changed", result("position", false)));
        assertSame(stored, game.analysis().get(0));

        game.startNew(new GameRecord("new", "A", "B"), true);
        assertFalse(game.storeResult(record, 0, "position", first));
        assertFalse(game.analysis().has(0));
    }

    private static EngineSession.SearchResult result(String sfen, boolean stopped) {
        return new EngineSession.SearchResult(sfen, null, 0, null,
                Collections.emptyList(), stopped);
    }
}
