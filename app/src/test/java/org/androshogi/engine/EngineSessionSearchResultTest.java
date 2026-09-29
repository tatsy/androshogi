package org.androshogi.engine;

import org.androshogi.shogi.Shogi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public class EngineSessionSearchResultTest {
    @Test
    public void normalResultIsCompletedNormally() {
        EngineSession.SearchResult result = new EngineSession.SearchResult(
                Shogi.STARTING_SFEN, "7g7f", 1, null, Collections.emptyList(), false);

        assertTrue(result.completedNormally());
    }

    @Test
    public void stoppedResultIsNotCompletedNormally() {
        EngineSession.SearchResult result = new EngineSession.SearchResult(
                Shogi.STARTING_SFEN, "7g7f", 1, null, Collections.emptyList(), true);

        assertFalse(result.completedNormally());
    }
}
