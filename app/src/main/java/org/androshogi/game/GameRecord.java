package org.androshogi.game;

import org.androshogi.shogi.Shogi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A game record: the starting position, the moves played from it, and the
 * ply currently shown on the board.
 *
 * <p>This is a plain model with no JNI or Android dependency, so it can be
 * unit tested on the host JVM. The UI keeps its native board position in
 * step with {@link #currentPly()}; nothing here touches the board.
 *
 * <p>Moves beyond the current ply are what "進む" replays. Playing a move
 * that differs from the recorded continuation discards them
 * ({@link #play(int)}); branches are a later phase.
 */
public final class GameRecord {
    private final String startSfen;
    private final List<Integer> moves = new ArrayList<>();
    /** Seconds spent on each move, 0 when the source did not say. */
    private final List<Integer> times = new ArrayList<>();
    /** Comment attached to each move, null when there is none. */
    private final List<String> comments = new ArrayList<>();
    private String blackName;
    private String whiteName;
    private int currentPly;

    /** An empty record starting from {@code startSfen}. */
    public GameRecord(String startSfen, String blackName, String whiteName) {
        this.startSfen = startSfen;
        this.blackName = blackName;
        this.whiteName = whiteName;
    }

    /**
     * A record with moves already played, positioned at its end (as a pasted
     * KIF is shown). {@code times} and {@code comments} may be null or shorter
     * than {@code moves}; missing entries are padded.
     */
    public GameRecord(String startSfen, List<Integer> moves, List<Integer> times,
                      List<String> comments, String blackName, String whiteName) {
        this(startSfen, blackName, whiteName);
        for (int i = 0; i < moves.size(); i++) {
            this.moves.add(moves.get(i));
            this.times.add(times != null && i < times.size() && times.get(i) != null ? times.get(i) : 0);
            this.comments.add(comments != null && i < comments.size() ? comments.get(i) : null);
        }
        currentPly = this.moves.size();
    }

    /** Detached copy used when a game is serialized on a background thread. */
    public GameRecord(GameRecord other) {
        this(other.startSfen, other.moves, other.times, other.comments,
                other.blackName, other.whiteName);
        currentPly = other.currentPly;
    }

    public String startSfen() { return startSfen; }
    public String blackName() { return blackName; }
    public String whiteName() { return whiteName; }

    public void setPlayerNames(String black, String white) {
        blackName = black;
        whiteName = white;
    }

    /** Number of moves in the record. Plies run from 0 (start) to this value. */
    public int length() { return moves.size(); }

    /** 0 at the starting position, {@link #length()} at the end. */
    public int currentPly() { return currentPly; }

    /** Moves that {@link #forward()} can still replay. */
    public int remaining() { return moves.size() - currentPly; }

    public boolean hasMoves() { return !moves.isEmpty(); }
    public boolean isAtStart() { return currentPly == 0; }
    public boolean isAtEnd() { return currentPly == moves.size(); }

    /** The move that leads from ply {@code index} to {@code index + 1}. */
    public int move(int index) { return moves.get(index); }
    public int timeSeconds(int index) { return times.get(index); }
    public String comment(int index) { return comments.get(index); }
    public List<Integer> moves() { return Collections.unmodifiableList(moves); }

    /** The move {@link #forward()} would play, or {@link Shogi#MOVE_NONE} at the end. */
    public int nextMove() {
        return isAtEnd() ? Shogi.MOVE_NONE : moves.get(currentPly);
    }

    /** The move that led to the current position, or {@link Shogi#MOVE_NONE} at the start. */
    public int lastMove() {
        return isAtStart() ? Shogi.MOVE_NONE : moves.get(currentPly - 1);
    }

    /** Advances one ply. Returns false at the end of the record. */
    public boolean forward() {
        if (isAtEnd()) {
            return false;
        }
        currentPly++;
        return true;
    }

    /** Goes back one ply. Returns false at the start of the record. */
    public boolean backward() {
        if (isAtStart()) {
            return false;
        }
        currentPly--;
        return true;
    }

    /** Moves to {@code ply}, clamped to the record, and returns the ply reached. */
    public int seek(int ply) {
        currentPly = Math.max(0, Math.min(moves.size(), ply));
        return currentPly;
    }

    public void seekStart() { seek(0); }
    public void seekEnd() { seek(moves.size()); }

    /**
     * Plays a move at the current position and advances past it.
     *
     * <p>If it is the move the record continues with, the record is simply
     * followed. Otherwise the moves from the current ply on are discarded and
     * this move becomes the record's end.
     *
     * @return the ply from which moves were discarded, or -1 if the record was followed
     */
    public int play(int move) {
        if (!isAtEnd() && moves.get(currentPly) == move) {
            currentPly++;
            return -1;
        }
        int from = currentPly;
        truncate(from);
        moves.add(move);
        times.add(0);
        comments.add(null);
        currentPly = from + 1;
        return from;
    }

    /** Drops the moves from {@code ply} on. The current ply is clamped to the new end. */
    public void truncate(int ply) {
        int keep = Math.max(0, Math.min(moves.size(), ply));
        while (moves.size() > keep) {
            int last = moves.size() - 1;
            moves.remove(last);
            times.remove(last);
            comments.remove(last);
        }
        if (currentPly > keep) {
            currentPly = keep;
        }
    }
}
