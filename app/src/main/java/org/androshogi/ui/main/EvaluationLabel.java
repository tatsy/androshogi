package org.androshogi.ui.main;

import org.androshogi.game.PositionAnalysis;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Formats the win rate and evaluation of a position for one player's row,
 * e.g. "勝率 62%　+180" or "勝率 99%　+詰". Each side sees its own numbers:
 * black's win rate is p, white's is 1 - p, and the score sign follows.
 *
 * <p>Free of Android dependencies so it can be tested on the host JVM.
 */
public final class EvaluationLabel {
    private EvaluationLabel() {}

    /** Label for black's row; empty when there is nothing to show. */
    public static String forBlack(@Nullable PositionAnalysis analysis) {
        return format(analysis, true);
    }

    /** Label for white's row; empty when there is nothing to show. */
    public static String forWhite(@Nullable PositionAnalysis analysis) {
        return format(analysis, false);
    }

    static String format(@Nullable PositionAnalysis analysis, boolean forBlack) {
        if (analysis == null || !analysis.hasScore()) {
            return "";
        }
        return String.format(Locale.JAPANESE, "勝率 %s　%s",
                winRate(analysis, forBlack), score(analysis, forBlack));
    }

    /** Black's win rate as "62%", or empty when there is no score. */
    public static String winRate(@Nullable PositionAnalysis analysis) {
        return analysis == null || !analysis.hasScore() ? "" : winRate(analysis, true);
    }

    /** Black's score as "+300", "+詰" or "-詰", or empty when there is no score. */
    public static String score(@Nullable PositionAnalysis analysis) {
        return analysis == null || !analysis.hasScore() ? "" : score(analysis, true);
    }

    private static String winRate(PositionAnalysis analysis, boolean forBlack) {
        double rate = analysis.winRateForBlack();
        if (!forBlack) {
            rate = 1.0 - rate;
        }
        return String.format(Locale.JAPANESE, "%d%%", (int) Math.round(rate * 100.0));
    }

    private static String score(PositionAnalysis analysis, boolean forBlack) {
        int score = forBlack ? analysis.scoreForBlack() : -analysis.scoreForBlack();
        if (analysis.isMate()) {
            return score < 0 ? "-詰" : "+詰";
        }
        return String.format(Locale.JAPANESE, "%+d", score);
    }
}
