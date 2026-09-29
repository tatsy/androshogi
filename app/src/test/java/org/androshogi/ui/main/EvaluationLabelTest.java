package org.androshogi.ui.main;

import org.androshogi.engine.EngineInfo;

import org.androshogi.game.PositionAnalysis;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class EvaluationLabelTest {
    private static final String BLACK_TO_MOVE = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1";

    @Before
    public void useTheClassicScale() {
        // The expected percentages below are for 1 / (1 + exp(-cp / 600)).
        PositionAnalysis.setWinRateScale(600);
    }

    private static PositionAnalysis analysis(String infoLine) {
        return PositionAnalysis.from(BLACK_TO_MOVE,
                Arrays.asList(EngineInfo.parse(infoLine)), null);
    }

    @Test
    public void emptyWithoutAnAnalysisOrAScore() {
        assertEquals("", EvaluationLabel.forBlack(null));
        assertEquals("", EvaluationLabel.forWhite(null));
        PositionAnalysis noScore = PositionAnalysis.from(BLACK_TO_MOVE, Collections.emptyList(), null);
        assertEquals("", EvaluationLabel.forBlack(noScore));
    }

    @Test
    public void eachSideSeesItsOwnWinRateAndSign() {
        // 1 / (1 + exp(-300 / 600)) = 0.622...
        PositionAnalysis a = analysis("info depth 10 score cp 300 multipv 1 pv 7g7f");
        assertEquals("勝率 62%　+300", EvaluationLabel.forBlack(a));
        assertEquals("勝率 38%　-300", EvaluationLabel.forWhite(a));
    }

    @Test
    public void zeroIsShownWithAPlusSign() {
        PositionAnalysis a = analysis("info depth 10 score cp 0 multipv 1 pv 7g7f");
        assertEquals("勝率 50%　+0", EvaluationLabel.forBlack(a));
        assertEquals("勝率 50%　+0", EvaluationLabel.forWhite(a));
    }

    @Test
    public void shortFormsGiveBlacksNumbersForTheMoveList() {
        PositionAnalysis a = analysis("info depth 10 score cp -300 multipv 1 pv 7g7f");
        assertEquals("38%", EvaluationLabel.winRate(a));
        assertEquals("-300", EvaluationLabel.score(a));
        assertEquals("", EvaluationLabel.winRate(null));
        assertEquals("", EvaluationLabel.score(null));
        PositionAnalysis mate = analysis("info depth 10 score mate -3 multipv 1 pv 7g7f");
        assertEquals("-詰", EvaluationLabel.score(mate));
    }

    @Test
    public void mateIsSpelledOutInsteadOfTheScore() {
        PositionAnalysis a = analysis("info depth 10 score mate 5 multipv 1 pv 7g7f");
        assertEquals("勝率 100%　+詰", EvaluationLabel.forBlack(a));
        assertEquals("勝率 0%　-詰", EvaluationLabel.forWhite(a));
    }
}
