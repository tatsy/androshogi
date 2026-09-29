package org.androshogi.engine;

import org.androshogi.shogi.Board;

import java.util.List;

/**
 * Receives structured search updates from {@link EngineSession}.
 *
 * <p>Callbacks are delivered on the main thread.
 */
public interface EngineUpdateListener {
    /**
     * @param board position the search was started from; valid for the duration
     *              of this call only, so do not keep a reference to it
     * @param infos latest info line of each multi-PV index, ordered by index
     */
    void onEngineUpdated(Board board, List<EngineInfo> infos);
}
