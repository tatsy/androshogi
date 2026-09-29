package org.androshogi.game;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * Engine results for the positions of the current {@link GameRecord}, indexed
 * by ply so that a move list and an evaluation graph can walk them in order.
 *
 * <p>Ply {@code p} is the position after {@code p} moves; entries are null
 * until analyzed. The analysis loop and the "検討" button both feed this.
 * When the record is truncated the entries past the cut are dropped with it,
 * since they described positions that no longer exist in the record.
 */
public class GameAnalysis {
    private final ArrayList<PositionAnalysis> results = new ArrayList<>();

    public GameAnalysis() {
    }

    /** Detached container used when a game is serialized on a background thread. */
    public GameAnalysis(GameAnalysis other) {
        putAll(other);
    }

    public void put(int ply, PositionAnalysis analysis) {
        while (results.size() <= ply) {
            results.add(null);
        }
        results.set(ply, analysis);
    }

    /** Copies every result of {@code other} into this, replacing entries at the same ply. */
    public void putAll(GameAnalysis other) {
        for (int ply = 0; ply < other.plies(); ply++) {
            PositionAnalysis a = other.get(ply);
            if (a != null) {
                put(ply, a);
            }
        }
    }

    /** Result for the position after {@code ply} moves, or null if not analyzed. */
    @Nullable
    public PositionAnalysis get(int ply) {
        return ply >= 0 && ply < results.size() ? results.get(ply) : null;
    }

    public boolean has(int ply) {
        return get(ply) != null;
    }

    /** Number of analyzed positions. */
    public int count() {
        int n = 0;
        for (PositionAnalysis a : results) {
            if (a != null) {
                n++;
            }
        }
        return n;
    }

    /** One past the highest ply that could hold a result. */
    public int plies() {
        return results.size();
    }

    /** Forgets the results from {@code fromPly} on. */
    public void truncate(int fromPly) {
        int keep = Math.max(0, fromPly);
        while (results.size() > keep) {
            results.remove(results.size() - 1);
        }
    }

    /** Forgets everything, e.g. when another game record is loaded. */
    public void clear() {
        results.clear();
    }
}
