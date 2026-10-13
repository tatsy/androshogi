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
        assertTrue(game.storeResult(record, 0, record.nodeIdAtPly(0), "position", first));
        PositionAnalysis stored = game.analysis().get(0);

        assertFalse(game.storeResult(record, 0, record.nodeIdAtPly(0), "position", result("position", true)));
        assertFalse(game.storeResult(record, 0, record.nodeIdAtPly(0), "changed", result("position", false)));
        assertSame(stored, game.analysis().get(0));

        game.startNew(new GameRecord("new", "A", "B"), true);
        assertFalse(game.storeResult(record, 0, record.nodeIdAtPly(0), "position", first));
        assertFalse(game.analysis().has(0));
    }

    private static EngineSession.SearchResult result(String sfen, boolean stopped) {
        return new EngineSession.SearchResult(sfen, null, 0, null,
                Collections.emptyList(), stopped);
    }

    @Test public void staleNodeCannotOverwriteAnIdenticalPositionAtTheSamePly() {
        GameRecord record = new GameRecord("position", "B", "W");
        record.play(42);
        long searchedNode = record.currentNodeId();
        GameSession game = new GameSession(record, () -> "id", () -> 100);
        record.truncate(0);
        record.play(42);
        // Even an identical SFEN must not make a deleted search target valid again.
        assertFalse(game.storeResult(record, 1, searchedNode, "same", result("same", false)));
        assertTrue(game.storeResult(record, 1, record.currentNodeId(), "same", result("same", false)));
    }

    @Test public void aLateResultCannotResurrectADeletedOrUndoneSearchTarget() {
        GameRecord record = new GameRecord("position", "B", "W");
        record.playVariation(42);
        long deleted = record.currentNodeId();
        record.addVariation(GameRecord.ROOT_ID, 43, 0, null);
        GameSession game = new GameSession(record, () -> "id", () -> 100);
        GameSession.LeafDeletion deletion = game.deleteCurrentLeaf();
        assertFalse(game.storeResult(record, 1, deleted, "same", result("same", false)));
        assertTrue(game.undoLeafDeletion(deletion));
        assertFalse(game.storeResult(record, 1, deleted, "same", result("same", false)));
        assertTrue(game.storeResult(game.record(), 1, deleted, "same", result("same", false)));
    }

}
