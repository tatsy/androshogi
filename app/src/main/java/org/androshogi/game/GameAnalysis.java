package org.androshogi.game;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Results keyed by position ID, with a ply-based view of the selected route for the UI. */
public final class GameAnalysis {
    private GameRecord record;
    private final Map<Long, PositionAnalysis> results = new LinkedHashMap<>();

    public GameAnalysis(GameRecord record) {
        this.record = record;
    }

    /** Detached container bound to a detached record with the same node IDs. */
    public GameAnalysis(GameAnalysis other, GameRecord record) {
        this(record);
        putAll(other);
    }

    /** Rebinds the stable object held by views when a new game is loaded. */
    public void resetForRecord(GameRecord record) {
        clear();
        this.record = record;
    }

    public void put(int ply, PositionAnalysis analysis) {
        putByNode(record.nodeIdAtPly(ply), analysis);
    }

    public void putByNode(long nodeId, PositionAnalysis analysis) {
        if (record.node(nodeId) == null) throw new IllegalArgumentException("Unknown node " + nodeId);
        if (analysis == null) results.remove(nodeId);
        else results.put(nodeId, analysis);
    }

    /** Copies results between containers for the same tree (including detached copies). */
    public void putAll(GameAnalysis other) {
        for (Map.Entry<Long, PositionAnalysis> entry : other.entries().entrySet()) {
            if (record.node(entry.getKey()) != null) results.put(entry.getKey(), entry.getValue());
        }
    }

    @Nullable
    public PositionAnalysis get(int ply) {
        return ply >= 0 && ply <= record.length() ? getByNode(record.nodeIdAtPly(ply)) : null;
    }

    @Nullable
    public PositionAnalysis getByNode(long nodeId) {
        return record.node(nodeId) == null ? null : results.get(nodeId);
    }

    public boolean has(int ply) { return get(ply) != null; }
    public int count() { return entries().size(); }

    /** One past the highest analyzed ply on the selected route. */
    public int plies() {
        for (int ply = record.length(); ply >= 0; ply--) {
            if (has(ply)) return ply + 1;
        }
        return 0;
    }

    /** All live results, including variations outside the selected route. */
    public Map<Long, PositionAnalysis> entries() {
        Map<Long, PositionAnalysis> live = new LinkedHashMap<>();
        for (Map.Entry<Long, PositionAnalysis> entry : results.entrySet()) {
            if (record.node(entry.getKey()) != null) live.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(live);
    }

    /** Forgets selected route results from fromPly on, and prunes results of deleted nodes. */
    public void truncate(int fromPly) {
        for (int ply = Math.max(0, fromPly); ply <= record.length(); ply++) {
            results.remove(record.nodeIdAtPly(ply));
        }
        results.keySet().removeIf(id -> record.node(id) == null);
    }

    public void clear() { results.clear(); }
}
