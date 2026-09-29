package org.androshogi.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for the USI "info" line parser.
 *
 * <p>{@link EngineInfo} deliberately keeps the principal variation as USI
 * strings, so these tests run on the host JVM without the native library.
 */
public class EngineInfoTest {
    @Test
    public void parsesAFullInfoLine() {
        EngineInfo info = EngineInfo.parse(
                "info depth 12 seldepth 18 score cp 84 multipv 2 nodes 123456 nps 654321 "
                        + "hashfull 123 time 1000 pv 7g7f 3c3d 2g2f");

        assertEquals(12, info.depth());
        assertEquals(18, info.seldepth());
        assertEquals(2, info.multipv());
        assertEquals(123456L, info.nodes());
        assertEquals(654321L, info.nps());
        assertEquals(123, info.hashfull());
        assertEquals(1000, info.timeMs());
        assertTrue(info.hasScore());
        assertFalse(info.isMate());
        assertEquals(84, info.score());
        assertEquals(EngineInfo.Bound.EXACT, info.bound());
        assertEquals(Arrays.asList("7g7f", "3c3d", "2g2f"), info.pv());
    }

    @Test
    public void toleratesFieldsInAnyOrderAndMissingOnes() {
        // The previous regex based parser required one exact field order.
        EngineInfo info = EngineInfo.parse("info time 300 nodes 900 depth 5 score cp -30 pv 8h2b+");

        assertEquals(5, info.depth());
        assertEquals(EngineInfo.NO_VALUE, info.seldepth());
        assertEquals(1, info.multipv()); // defaults to 1 when MultiPV is off
        assertEquals(-30, info.score());
        assertEquals(Arrays.asList("8h2b+"), info.pv());
    }

    @Test
    public void keepsBoundedScores() {
        EngineInfo lower = EngineInfo.parse("info depth 9 score cp 250 lowerbound multipv 1 pv 7g7f");
        assertEquals(EngineInfo.Bound.LOWER, lower.bound());
        assertEquals(250, lower.score());

        EngineInfo upper = EngineInfo.parse("info depth 9 score cp -250 upperbound multipv 1 pv 7g7f");
        assertEquals(EngineInfo.Bound.UPPER, upper.bound());
    }

    @Test
    public void parsesMateScores() {
        EngineInfo mate = EngineInfo.parse("info depth 20 score mate 5 multipv 1 pv 5e5d P*5c");
        assertTrue(mate.isMate());
        assertFalse(mate.isMateWithUnknownPly());
        assertEquals(5, mate.score());

        EngineInfo mated = EngineInfo.parse("info depth 20 score mate -3 multipv 1 pv 5e5d");
        assertEquals(-3, mated.score());
    }

    @Test
    public void parsesMateWithUnknownPly() {
        EngineInfo plus = EngineInfo.parse("info score mate + multipv 1 pv 5e5d");
        assertTrue(plus.isMateWithUnknownPly());
        assertTrue(plus.score() > 0);

        EngineInfo minus = EngineInfo.parse("info score mate - multipv 1 pv 5e5d");
        assertTrue(minus.isMateWithUnknownPly());
        assertTrue(minus.score() < 0);
    }

    @Test
    public void skipsUnknownKeysBeforeThePv() {
        EngineInfo info = EngineInfo.parse(
                "info depth 7 currmove 7g7f currmovenumber 1 score cp 10 pv 7g7f 3c3d");
        assertEquals(7, info.depth());
        assertEquals(Arrays.asList("7g7f", "3c3d"), info.pv());
    }

    @Test
    public void returnsNullForNonInfoLines() {
        assertNull(EngineInfo.parse(null));
        assertNull(EngineInfo.parse(""));
        assertNull(EngineInfo.parse("bestmove 7g7f ponder 3c3d"));
        assertNull(EngineInfo.parse("readyok"));
        assertNull(EngineInfo.parse("info string Loading eval file"));
    }

    @Test
    public void reportsMissingPv() {
        EngineInfo info = EngineInfo.parse("info depth 3 nodes 100 nps 1000 time 100");
        assertFalse(info.hasPv());
        assertFalse(info.hasScore());
    }

    @Test
    public void toLineRoundTripsEveryField() {
        String[] lines = {
                "info depth 12 seldepth 20 multipv 1 time 1000 nodes 500000 nps 500000 hashfull 12 score cp -84 pv 7g7f 3c3d",
                "info depth 5 multipv 2 score mate + pv 2h2b+",
                "info depth 5 multipv 1 score mate -3 upperbound",
                "info depth 3 multipv 1",
        };
        for (String line : lines) {
            EngineInfo info = EngineInfo.parse(line);
            assertEquals(line, info.toLine());
            EngineInfo again = EngineInfo.parse(info.toLine());
            assertEquals(info.depth(), again.depth());
            assertEquals(info.seldepth(), again.seldepth());
            assertEquals(info.multipv(), again.multipv());
            assertEquals(info.hasScore(), again.hasScore());
            assertEquals(info.score(), again.score());
            assertEquals(info.bound(), again.bound());
            assertEquals(info.pv(), again.pv());
        }
    }
}
