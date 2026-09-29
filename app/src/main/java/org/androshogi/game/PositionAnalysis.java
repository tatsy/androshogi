package org.androshogi.game;

import org.androshogi.engine.EngineInfo;
import org.androshogi.engine.EngineSession;

import java.util.Collections;
import java.util.List;

/**
 * What the engine concluded about one position: the evaluation from black's
 * point of view, the best move, and the info lines to show again later.
 *
 * <p>Free of JNI and Android dependencies so it can be tested on the host JVM
 * and, later, written to JSON.
 */
public final class PositionAnalysis {
    /** Magnitude used in place of a centipawn score when the position is a forced mate. */
    public static final int MATE_SCORE = 30000;
    /**
     * Default scale of the logistic win-rate conversion, 1 / (1 + exp(-cp / scale)).
     * Ponanza popularised 600, which YaneuraOu's learner also uses, but that reads
     * too sharp for a viewer (+1000 shows as 84%); 1000 gives 73% there and keeps
     * -100 at 48%. The user can pick another value in the settings.
     */
    public static final double DEFAULT_WIN_RATE_SCALE = 1000.0;
    private static volatile double winRateScale = DEFAULT_WIN_RATE_SCALE;

    private final String sfen;
    private final boolean hasScore;
    private final int scoreForBlack;
    private final boolean mate;
    private final String bestMoveUsi;
    private final List<EngineInfo> infos;

    private PositionAnalysis(String sfen, boolean hasScore, int scoreForBlack, boolean mate,
                             String bestMoveUsi, List<EngineInfo> infos) {
        this.sfen = sfen;
        this.hasScore = hasScore;
        this.scoreForBlack = scoreForBlack;
        this.mate = mate;
        this.bestMoveUsi = bestMoveUsi;
        this.infos = Collections.unmodifiableList(infos);
    }

    public static PositionAnalysis from(EngineSession.SearchResult result) {
        return from(result.sfen, result.infos, result.bestMoveUSI);
    }

    /**
     * @param sfen        position searched; its side-to-move field decides the sign
     * @param infos       final info line per multi-PV index
     * @param bestMoveUsi may be null when the engine reported none
     */
    public static PositionAnalysis from(String sfen, List<EngineInfo> infos, String bestMoveUsi) {
        EngineInfo best = null;
        for (EngineInfo info : infos) {
            if (info.multipv() == 1) {
                best = info;
                break;
            }
        }
        if (best == null && !infos.isEmpty()) {
            best = infos.get(0);
        }

        if (best == null || !best.hasScore()) {
            return new PositionAnalysis(sfen, false, 0, false, bestMoveUsi, infos);
        }

        // The engine scores from the side to move; flip when white is to move.
        int sign = isWhiteToMove(sfen) ? -1 : 1;
        if (best.isMate()) {
            int mateSign = best.score() >= 0 ? 1 : -1;
            return new PositionAnalysis(sfen, true, sign * mateSign * MATE_SCORE, true, bestMoveUsi, infos);
        }
        return new PositionAnalysis(sfen, true, sign * best.score(), false, bestMoveUsi, infos);
    }

    public static boolean isWhiteToMove(String sfen) {
        String[] fields = sfen.trim().split("\\s+");
        return fields.length >= 2 && fields[1].equals("w");
    }

    public String sfen() { return sfen; }
    public boolean hasScore() { return hasScore; }
    /** Evaluation from black's point of view; ±{@link #MATE_SCORE} for a mate. */
    public int scoreForBlack() { return scoreForBlack; }
    public boolean isMate() { return mate; }
    public String bestMoveUsi() { return bestMoveUsi; }
    public List<EngineInfo> infos() { return infos; }

    /** Sets the centipawn scale of the win-rate curve for every later call of {@link #winRateForBlack()}. */
    public static void setWinRateScale(double scale) {
        winRateScale = scale > 0 ? scale : DEFAULT_WIN_RATE_SCALE;
    }

    public static double winRateScale() {
        return winRateScale;
    }

    /** Black's winning probability in [0, 1]; 0.5 when the engine gave no score. */
    public double winRateForBlack() {
        if (!hasScore) {
            return 0.5;
        }
        return 1.0 / (1.0 + Math.exp(-scoreForBlack / winRateScale));
    }
}
