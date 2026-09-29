package org.androshogi.game;

import org.androshogi.engine.EngineInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class GameAnalysisTest {
    private static PositionAnalysis sample(String sfen) {
        return PositionAnalysis.from(sfen,
                Arrays.asList(EngineInfo.parse("info score cp 10 multipv 1 pv 7g7f")), "7g7f");
    }

    @Test
    public void storesByPlyWithGaps() {
        GameAnalysis g = new GameAnalysis();
        PositionAnalysis p3 = sample("s3 b - 4");
        g.put(3, p3);
        assertSame(p3, g.get(3));
        assertNull(g.get(0));
        assertNull(g.get(2));
        assertNull(g.get(99));
        assertNull(g.get(-1));
        assertTrue(g.has(3));
        assertFalse(g.has(1));
        assertEquals(1, g.count());
        assertEquals(4, g.plies());
    }

    @Test
    public void newerResultReplacesOlderResultAtSamePly() {
        GameAnalysis g = new GameAnalysis();
        PositionAnalysis older = sample("old b - 1");
        PositionAnalysis newer = sample("new b - 1");

        g.put(2, older);
        g.put(2, newer);

        assertSame(newer, g.get(2));
        assertEquals(1, g.count());
    }

    @Test
    public void truncateDropsFromThePlyOn() {
        GameAnalysis g = new GameAnalysis();
        for (int ply = 0; ply < 5; ply++) {
            g.put(ply, sample("s" + ply + " b - " + (ply + 1)));
        }
        g.truncate(2);
        assertEquals(2, g.plies());
        assertTrue(g.has(1));
        assertFalse(g.has(2));
        assertEquals(2, g.count());
    }

    @Test
    public void clearForgetsEverything() {
        GameAnalysis g = new GameAnalysis();
        g.put(1, sample("s b - 2"));
        g.clear();
        assertEquals(0, g.count());
        assertEquals(0, g.plies());
    }
}
