package org.androshogi.engine;

import org.androshogi.shogi.Move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One parsed USI {@code info} line.
 *
 * <p>The principal variation is kept as USI move strings so that this class
 * stays free of JNI dependencies and can be unit tested on the host JVM.
 * Converting the moves to {@link Move} is left to the views, which know the
 * position the search was started from.
 *
 * <p>Scores are reported from the point of view of the side to move, exactly
 * as the engine emits them.
 */
public final class EngineInfo {
    /** Value returned by integer getters when the engine did not report the field. */
    public static final int NO_VALUE = -1;

    /**
     * Magnitude used for {@code score mate +} / {@code score mate -}, which
     * some engines emit when a mate exists but its length is unknown.
     */
    public static final int MATE_UNKNOWN = Integer.MAX_VALUE;

    public enum ScoreType { CP, MATE }

    /** Whether the score is exact or only a bound of the true value. */
    public enum Bound { EXACT, LOWER, UPPER }

    private final int depth;
    private final int seldepth;
    private final int multipv;
    private final int time;
    private final int hashfull;
    private final long nodes;
    private final long nps;
    private final ScoreType scoreType;
    private final int score;
    private final Bound bound;
    private final List<String> pv;

    private EngineInfo(int depth, int seldepth, int multipv, int time, int hashfull,
                       long nodes, long nps, ScoreType scoreType, int score, Bound bound,
                       List<String> pv) {
        this.depth = depth;
        this.seldepth = seldepth;
        this.multipv = multipv;
        this.time = time;
        this.hashfull = hashfull;
        this.nodes = nodes;
        this.nps = nps;
        this.scoreType = scoreType;
        this.score = score;
        this.bound = bound;
        this.pv = Collections.unmodifiableList(pv);
    }

    public int depth() { return depth; }
    public int seldepth() { return seldepth; }
    /** 1-based multi-PV index; 1 when the engine omits {@code multipv}. */
    public int multipv() { return multipv; }
    public int timeMs() { return time; }
    public int hashfull() { return hashfull; }
    public long nodes() { return nodes; }
    public long nps() { return nps; }

    public boolean hasScore() { return scoreType != null; }
    public ScoreType scoreType() { return scoreType; }
    /** Score in centipawns, or the mate distance in plies, signed for the side to move. */
    public int score() { return score; }
    public boolean isMate() { return scoreType == ScoreType.MATE; }
    /** True for {@code score mate +} / {@code score mate -}, where the ply count is unknown. */
    public boolean isMateWithUnknownPly() {
        return scoreType == ScoreType.MATE && Math.abs(score) == MATE_UNKNOWN;
    }

    public Bound bound() { return bound; }

    /** Principal variation as USI move strings; empty when none was reported. */
    public List<String> pv() { return pv; }
    public boolean hasPv() { return !pv.isEmpty(); }

    /**
     * Rebuilds a USI {@code info} line carrying every field this object holds,
     * so that {@link #parse(String)} gives back an equal object. This is how
     * infos are stored in a saved game.
     */
    public String toLine() {
        StringBuilder sb = new StringBuilder("info");
        if (depth != NO_VALUE) {
            sb.append(" depth ").append(depth);
        }
        if (seldepth != NO_VALUE) {
            sb.append(" seldepth ").append(seldepth);
        }
        sb.append(" multipv ").append(multipv);
        if (time != NO_VALUE) {
            sb.append(" time ").append(time);
        }
        if (nodes != NO_VALUE) {
            sb.append(" nodes ").append(nodes);
        }
        if (nps != NO_VALUE) {
            sb.append(" nps ").append(nps);
        }
        if (hashfull != NO_VALUE) {
            sb.append(" hashfull ").append(hashfull);
        }
        if (scoreType == ScoreType.CP) {
            sb.append(" score cp ").append(score);
        } else if (scoreType == ScoreType.MATE) {
            sb.append(" score mate ");
            if (score == MATE_UNKNOWN) {
                sb.append('+');
            } else if (score == -MATE_UNKNOWN) {
                sb.append('-');
            } else {
                sb.append(score);
            }
        }
        if (bound == Bound.LOWER) {
            sb.append(" lowerbound");
        } else if (bound == Bound.UPPER) {
            sb.append(" upperbound");
        }
        if (!pv.isEmpty()) {
            sb.append(" pv");
            for (String move : pv) {
                sb.append(' ').append(move);
            }
        }
        return sb.toString();
    }

    /**
     * Parses a line emitted by a USI engine.
     *
     * @return the parsed info, or null if the line is not an {@code info} line
     *         or carries no machine readable data (e.g. {@code info string ...})
     */
    public static EngineInfo parse(String line) {
        if (line == null) {
            return null;
        }
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length < 2 || !tokens[0].equals("info")) {
            return null;
        }

        int depth = NO_VALUE;
        int seldepth = NO_VALUE;
        int multipv = 1;
        int time = NO_VALUE;
        int hashfull = NO_VALUE;
        long nodes = NO_VALUE;
        long nps = NO_VALUE;
        ScoreType scoreType = null;
        int score = 0;
        Bound bound = Bound.EXACT;
        List<String> pv = new ArrayList<>();

        for (int i = 1; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "string":
                    // Free-form engine message; nothing else can follow it.
                    return null;
                case "depth":
                    depth = intAt(tokens, ++i, NO_VALUE);
                    break;
                case "seldepth":
                    seldepth = intAt(tokens, ++i, NO_VALUE);
                    break;
                case "multipv":
                    multipv = intAt(tokens, ++i, 1);
                    break;
                case "time":
                    time = intAt(tokens, ++i, NO_VALUE);
                    break;
                case "hashfull":
                    hashfull = intAt(tokens, ++i, NO_VALUE);
                    break;
                case "nodes":
                    nodes = longAt(tokens, ++i, NO_VALUE);
                    break;
                case "nps":
                    nps = longAt(tokens, ++i, NO_VALUE);
                    break;
                case "score":
                    if (i + 2 < tokens.length) {
                        String type = tokens[++i];
                        String value = tokens[++i];
                        if (type.equals("cp")) {
                            scoreType = ScoreType.CP;
                            score = parseInt(value, 0);
                        } else if (type.equals("mate")) {
                            scoreType = ScoreType.MATE;
                            if (value.equals("+")) {
                                score = MATE_UNKNOWN;
                            } else if (value.equals("-")) {
                                score = -MATE_UNKNOWN;
                            } else {
                                score = parseInt(value, 0);
                            }
                        }
                    }
                    break;
                case "lowerbound":
                    bound = Bound.LOWER;
                    break;
                case "upperbound":
                    bound = Bound.UPPER;
                    break;
                case "pv":
                    // The principal variation runs to the end of the line.
                    for (int j = i + 1; j < tokens.length; j++) {
                        pv.add(tokens[j]);
                    }
                    i = tokens.length;
                    break;
                default:
                    // currmove, currmovenumber, refutation, ... : skip the key and
                    // let the loop re-examine the following token as a new key.
                    break;
            }
        }

        return new EngineInfo(depth, seldepth, multipv, time, hashfull, nodes, nps,
                scoreType, score, bound, pv);
    }

    private static int intAt(String[] tokens, int index, int fallback) {
        return index < tokens.length ? parseInt(tokens[index], fallback) : fallback;
    }

    private static long longAt(String[] tokens, int index, long fallback) {
        if (index >= tokens.length) {
            return fallback;
        }
        try {
            return Long.parseLong(tokens[index]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String token, int fallback) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
