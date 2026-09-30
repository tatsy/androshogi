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

    private static GameRecord fourMoves() {
        return new GameRecord("start", Arrays.asList(11, 22, 33, 44), null, null, "B", "W");
    }

    @Test
    public void storesByPlyWithGaps() {
        GameAnalysis g = new GameAnalysis(fourMoves());
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
        GameAnalysis g = new GameAnalysis(fourMoves());
        PositionAnalysis older = sample("old b - 1");
        PositionAnalysis newer = sample("new b - 1");

        g.put(2, older);
        g.put(2, newer);

        assertSame(newer, g.get(2));
        assertEquals(1, g.count());
    }

    @Test
    public void truncateDropsFromThePlyOn() {
        GameAnalysis g = new GameAnalysis(fourMoves());
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
        GameAnalysis g = new GameAnalysis(fourMoves());
        g.put(1, sample("s b - 2"));
        g.clear();
        assertEquals(0, g.count());
        assertEquals(0, g.plies());
    }

    @Test public void samePlyOnDifferentBranchesKeepsSeparateResults() {
        GameRecord r = fourMoves();
        GameAnalysis g = new GameAnalysis(r);
        long main = r.nodeIdAtPly(2);
        long branch = r.addVariation(r.nodeIdAtPly(1), 99, 0, null);
        PositionAnalysis mainResult = sample("main b - 3");
        PositionAnalysis branchResult = sample("branch b - 3");
        g.put(2, mainResult);
        r.selectNode(branch);
        assertNull(g.get(2));
        g.put(2, branchResult);
        assertSame(branchResult, g.get(2));
        r.selectNode(main);
        assertSame(mainResult, g.get(2));
        assertSame(branchResult, g.getByNode(branch));
        assertEquals(2, g.count());
    }

    @Test public void userVariationAndMainReturnRetainAllAnalysis() {
        GameRecord r = fourMoves();
        GameAnalysis g = new GameAnalysis(r);
        PositionAnalysis mainResult = sample("main b - 3");
        PositionAnalysis endResult = sample("end b - 5");
        g.put(2, mainResult);
        g.put(4, endResult);
        r.seek(1);
        r.playVariation(99);
        long branch = r.currentNodeId();
        assertNull(g.get(2));
        PositionAnalysis branchResult = sample("branch b - 3");
        g.put(2, branchResult);
        r.selectMainLine();
        assertSame(mainResult, g.get(2));
        assertSame(endResult, g.get(4));
        assertSame(branchResult, g.getByNode(branch));
        assertEquals(3, g.count());
        r.seek(1);
        r.playVariation(99);
        assertSame(branchResult, g.get(2));
    }

    @Test public void editingPrunesDeletedNodesWithoutAttachingResultsToTheReplacement() {
        GameRecord r = fourMoves();
        GameAnalysis g = new GameAnalysis(r);
        g.put(0, sample("start b - 1"));
        g.put(1, sample("cut w - 2"));
        g.put(2, sample("old b - 3"));
        long deleted = r.nodeIdAtPly(2);
        r.seek(1);
        int cut = r.play(99);
        g.truncate(cut + 1); // Explicit destructive editing retains the position before the new move.
        assertTrue(g.has(0));
        assertTrue(g.has(1));
        assertNull(g.get(2));
        assertNull(g.getByNode(deleted));
        assertEquals(2, g.count());
        assertEquals(2, g.entries().size());
    }

}
