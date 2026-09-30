package org.androshogi.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One position in a record. IDs belong to that record and are never reused. */
public final class GameNode {
    private final long id;
    private final Long parentId;
    private final int move;
    private final int timeSeconds;
    private final String comment;
    private final List<Long> childIds = new ArrayList<>();
    private Long mainChildId;

    GameNode(long id, Long parentId, int move, int timeSeconds, String comment) {
        this.id = id;
        this.parentId = parentId;
        this.move = move;
        this.timeSeconds = timeSeconds;
        this.comment = comment;
    }

    public long id() { return id; }
    public Long parentId() { return parentId; }
    /** The move from the parent; MOVE_NONE for the starting position. */
    public int move() { return move; }
    public int timeSeconds() { return timeSeconds; }
    public String comment() { return comment; }
    public List<Long> childIds() { return Collections.unmodifiableList(childIds); }
    public Long mainChildId() { return mainChildId; }

    // Only GameRecord mutates topology; views and serializers receive read-only nodes.
    void addChild(long childId) { childIds.add(childId); }
    void setMainChild(Long childId) { mainChildId = childId; }
    void clearChildren() { childIds.clear(); mainChildId = null; }
}
