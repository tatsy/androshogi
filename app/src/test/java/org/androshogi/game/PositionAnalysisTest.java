package org.androshogi.game;

import org.androshogi.engine.EngineInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PositionAnalysisTest {
    private static final String BLACK_TO_MOVE = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1";
    private static final String WHITE_TO_MOVE = "lnsgkgsnl/1r5b1/ppppppppp/9/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL w - 2";

    private static EngineInfo info(String line) {
        return EngineInfo.parse(line);
    }

    @Test
    public void keepsTheSignWhenBlackIsToMove() {
        PositionAnalysis a = PositionAnalysis.from(BLACK_TO_MOVE,
                Arrays.asList(info("info depth 10 score cp 84 multipv 1 pv 7g7f")), "7g7f");
        assertTrue(a.hasScore());
        assertEquals(84, a.scoreForBlack());
        assertFalse(a.isMate());
        assertEquals("7g7f", a.bestMoveUsi());
    }

    @Test
    public void flipsTheSignWhenWhiteIsToMove() {
        PositionAnalysis a = PositionAnalysis.from(WHITE_TO_MOVE,
                Arrays.asList(info("info depth 10 score cp 84 multipv 1 pv 3c3d")), "3c3d");
        assertEquals(-84, a.scoreForBlack());
    }

    @Test
    public void prefersMultiPvOneOverTheFirstEntry() {
        PositionAnalysis a = PositionAnalysis.from(BLACK_TO_MOVE, Arrays.asList(
                info("info depth 10 score cp 20 multipv 2 pv 2g2f"),
                info("info depth 10 score cp 84 multipv 1 pv 7g7f")), "7g7f");
        assertEquals(84, a.scoreForBlack());
    }

    @Test
    public void mateBecomesTheMateScoreWithTheRightSign() {
        PositionAnalysis win = PositionAnalysis.from(BLACK_TO_MOVE,
                Arrays.asList(info("info depth 20 score mate 5 multipv 1 pv 5e5d")), "5e5d");
        assertTrue(win.isMate());
        assertEquals(PositionAnalysis.MATE_SCORE, win.scoreForBlack());

        PositionAnalysis lossForWhite = PositionAnalysis.from(WHITE_TO_MOVE,
                Arrays.asList(info("info depth 20 score mate -3 multipv 1 pv 5e5d")), "5e5d");
        assertEquals(PositionAnalysis.MATE_SCORE, lossForWhite.scoreForBlack());
    }

    @Test
    public void winRateScaleDefaultsTo1000AndRejectsNonPositiveValues() {
        PositionAnalysis.setWinRateScale(0);
        assertEquals(1000.0, PositionAnalysis.winRateScale(), 1e-9);
        PositionAnalysis.setWinRateScale(600);
        assertEquals(600.0, PositionAnalysis.winRateScale(), 1e-9);
        PositionAnalysis.setWinRateScale(PositionAnalysis.DEFAULT_WIN_RATE_SCALE);
    }

    @Test
    public void winRateFollowsTheLogisticCurve() {
        PositionAnalysis.setWinRateScale(600);
        PositionAnalysis even = PositionAnalysis.from(BLACK_TO_MOVE,
                Arrays.asList(info("info score cp 0 multipv 1 pv 7g7f")), "7g7f");
        assertEquals(0.5, even.winRateForBlack(), 1e-9);

        PositionAnalysis plus600 = PositionAnalysis.from(BLACK_TO_MOVE,
                Arrays.asList(info("info score cp 600 multipv 1 pv 7g7f")), "7g7f");
        assertEquals(1.0 / (1.0 + Math.exp(-1.0)), plus600.winRateForBlack(), 1e-9);

        PositionAnalysis minus1000 = PositionAnalysis.from(BLACK_TO_MOVE,
                Arrays.asList(info("info score cp -1000 multipv 1 pv 7g7f")), "7g7f");
        assertTrue(minus1000.winRateForBlack() < 0.2);
    }

    @Test
    public void noScoreMeansEvenOdds() {
        PositionAnalysis a = PositionAnalysis.from(BLACK_TO_MOVE, Collections.<EngineInfo>emptyList(), null);
        assertFalse(a.hasScore());
        assertEquals(0.5, a.winRateForBlack(), 1e-9);
        assertEquals(0, a.scoreForBlack());
    }
}
