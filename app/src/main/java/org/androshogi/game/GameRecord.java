package org.androshogi.game;

import org.androshogi.shogi.Shogi;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A position tree with a selected continuation and a cursor on that route.
 *
 * <p>The existing ply-based API presents the selected route as a linear record,
 * so board navigation, the move list, KIF export and automatic analysis retain
 * their behavior. The main continuation is independent of route selection.
 * This model has no JNI or Android dependency.
 *
 * <p>{@link #play(int)} retains destructive editing for callers that need it;
 * user moves use {@link #playVariation(int)} to retain existing continuations.
 */
public final class GameRecord {
    public static final long ROOT_ID = 0;
    private final String startSfen;
    private final Map<Long, GameNode> nodes = new LinkedHashMap<>();
    private final Map<Long, Long> selectedChildren = new HashMap<>();
    // A derived view, never the canonical record. Includes the starting position.
    private final List<GameNode> selectedRoute = new ArrayList<>();
    private final List<Integer> moves = Collections.unmodifiableList(new AbstractList<Integer>() {
        @Override public Integer get(int index) { return moveNode(index).move(); }
        @Override public int size() { return length(); }
    });
    private long nextNodeId = 1;
    private String blackName;
    private String whiteName;
    private int currentPly;

    public GameRecord(String startSfen, String blackName, String whiteName) {
        this.startSfen = startSfen;
        this.blackName = blackName;
        this.whiteName = whiteName;
        nodes.put(ROOT_ID, new GameNode(ROOT_ID, null, Shogi.MOVE_NONE, 0, null));
        rebuildRoute();
    }

    /** Loaded linear records start at their end; missing move metadata is padded. */
    public GameRecord(String startSfen, List<Integer> moves, List<Integer> times,
                      List<String> comments, String blackName, String whiteName) {
        this(startSfen, blackName, whiteName);
        long parent = ROOT_ID;
        for (int i = 0; i < moves.size(); i++) {
            parent = appendChild(parent, moves.get(i),
                    times != null && i < times.size() && times.get(i) != null ? times.get(i) : 0,
                    comments != null && i < comments.size() ? comments.get(i) : null);
        }
        rebuildRoute();
        currentPly = length();
    }

    /** Detached tree and selection used by the background serializer. IDs are preserved. */
    public GameRecord(GameRecord other) {
        this(other.startSfen, other.blackName, other.whiteName);
        nodes.clear();
        for (GameNode source : other.nodes.values()) {
            GameNode copy = new GameNode(source.id(), source.parentId(), source.move(),
                    source.timeSeconds(), source.comment());
            for (long child : source.childIds()) copy.addChild(child);
            copy.setMainChild(source.mainChildId());
            nodes.put(copy.id(), copy);
        }
        selectedChildren.putAll(other.selectedChildren);
        nextNodeId = other.nextNodeId;
        rebuildRoute();
        currentPly = other.currentPly;
    }

    public String startSfen() { return startSfen; }
    public String blackName() { return blackName; }
    public String whiteName() { return whiteName; }
    public void setPlayerNames(String black, String white) { blackName = black; whiteName = white; }
    public int length() { return selectedRoute.size() - 1; }
    public int currentPly() { return currentPly; }
    public int remaining() { return length() - currentPly; }
    public boolean hasMoves() { return nodes.size() > 1; }
    public boolean isAtStart() { return currentPly == 0; }
    public boolean isAtEnd() { return currentPly == length(); }
    public int move(int index) { return moves.get(index); }
    public int timeSeconds(int index) { return moveNode(index).timeSeconds(); }
    public String comment(int index) { return moveNode(index).comment(); }
    public List<Integer> moves() { return moves; }
    public int nextMove() { return isAtEnd() ? Shogi.MOVE_NONE : move(currentPly); }
    public int lastMove() { return isAtStart() ? Shogi.MOVE_NONE : move(currentPly - 1); }

    private GameNode moveNode(int index) {
        if (index < 0 || index >= length()) throw new IndexOutOfBoundsException("Move " + index);
        return selectedRoute.get(index + 1);
    }

    public GameNode node(long id) { return nodes.get(id); }
    public List<GameNode> nodes() { return Collections.unmodifiableList(new ArrayList<>(nodes.values())); }
    public long nodeIdAtPly(int ply) { return selectedRoute.get(ply).id(); }
    public long currentNodeId() { return nodeIdAtPly(currentPly); }
    public long nextNodeId() { return nextNodeId; }
    public Long selectedChildId(long parentId) { return selectedChildren.get(parentId); }

    public boolean forward() {
        if (isAtEnd()) return false;
        currentPly++;
        return true;
    }
    public boolean backward() {
        if (isAtStart()) return false;
        currentPly--;
        return true;
    }
    public int seek(int ply) {
        currentPly = Math.max(0, Math.min(length(), ply));
        return currentPly;
    }
    public void seekStart() { seek(0); }
    public void seekEnd() { seek(length()); }

    /** Follows a recorded move, otherwise discards continuations and appends the move. */
    public int play(int move) {
        if (!isAtEnd() && nextMove() == move) {
            currentPly++;
            return -1;
        }
        int from = currentPly;
        truncate(from);
        appendChild(currentNodeId(), move, 0, null);
        rebuildRoute();
        currentPly = from + 1;
        return from;
    }

    /** Drops all descendants of the selected position at ply, clamping the cursor. */
    public void truncate(int ply) {
        int keep = Math.max(0, Math.min(length(), ply));
        GameNode parent = nodes.get(nodeIdAtPly(keep));
        ArrayDeque<Long> pending = new ArrayDeque<>(parent.childIds());
        while (!pending.isEmpty()) {
            GameNode removed = nodes.remove(pending.removeFirst());
            pending.addAll(removed.childIds());
            selectedChildren.remove(removed.id());
        }
        parent.clearChildren();
        selectedChildren.remove(parent.id());
        rebuildRoute();
        currentPly = Math.min(currentPly, keep);
    }

    /** Plays a new or existing child, retaining all other continuations and metadata. */
    public void playVariation(int move) {
        selectNode(addVariation(currentNodeId(), move, 0, null));
    }

    /** Restores the main route, keeping the cursor's ply where that route is long enough. */
    public void selectMainLine() {
        GameNode node = nodes.get(ROOT_ID);
        while (node.mainChildId() != null) {
            selectedChildren.put(node.id(), node.mainChildId());
            node = nodes.get(node.mainChildId());
        }
        rebuildRoute();
        currentPly = Math.min(currentPly, length());
    }

    /** Includes continuations beyond the cursor when deciding whether the main route is selected. */
    public boolean isMainLineSelected() {
        for (int ply = 1; ply < selectedRoute.size(); ply++) {
            if (!Long.valueOf(selectedRoute.get(ply).id()).equals(
                    selectedRoute.get(ply - 1).mainChildId())) return false;
        }
        return true;
    }

    /** Adds a variation, retaining the main and selected continuations. */
    public long addVariation(long parentId, int move, int seconds, String comment) {
        GameNode parent = requireNode(parentId);
        for (long childId : parent.childIds()) {
            if (nodes.get(childId).move() == move) return childId;
        }
        long id = appendChild(parentId, move, seconds, comment);
        rebuildRoute();
        return id;
    }

    /**
     * Selects the route through nodeId and places the cursor there.
     * A native board must be rebuilt from startSfen after a route switch.
     */
    public void selectNode(long nodeId) {
        GameNode current = requireNode(nodeId);
        int ply = 0;
        while (current.parentId() != null) {
            selectedChildren.put(current.parentId(), current.id());
            current = nodes.get(current.parentId());
            ply++;
        }
        rebuildRoute();
        currentPly = ply;
    }

    private GameNode requireNode(long id) {
        GameNode node = nodes.get(id);
        if (node == null) throw new IllegalArgumentException("Unknown node " + id);
        return node;
    }

    private long appendChild(long parentId, int move, int seconds, String comment) {
        GameNode parent = requireNode(parentId);
        if (nextNodeId == Long.MAX_VALUE) throw new IllegalStateException("Node IDs exhausted");
        long id = nextNodeId++;
        nodes.put(id, new GameNode(id, parentId, move, seconds, comment));
        parent.addChild(id);
        if (parent.mainChildId() == null) {
            parent.setMainChild(id);
            selectedChildren.put(parentId, id);
        }
        return id;
    }

    private void rebuildRoute() {
        selectedRoute.clear();
        GameNode node = nodes.get(ROOT_ID);
        while (node != null) {
            selectedRoute.add(node);
            Long child = selectedChildren.get(node.id());
            node = child == null ? null : nodes.get(child);
        }
    }

    /** Serialized topology; selected and main child IDs are deliberately separate. */
    public static final class NodeData {
        public final long id;
        public final Long parentId;
        public final int move;
        public final int seconds;
        public final String comment;
        public final Long mainChildId;
        public final Long selectedChildId;

        public NodeData(long id, Long parentId, int move, int seconds, String comment,
                        Long mainChildId, Long selectedChildId) {
            this.id = id;
            this.parentId = parentId;
            this.move = move;
            this.seconds = seconds;
            this.comment = comment;
            this.mainChildId = mainChildId;
            this.selectedChildId = selectedChildId;
        }
    }

    /** Restores a complete tree, rejecting disconnected nodes, cycles and invalid selections. */
    public static GameRecord fromTree(String startSfen, String black, String white,
                                      List<NodeData> data, long currentNodeId, long nextNodeId) {
        GameRecord record = new GameRecord(startSfen, black, white);
        record.nodes.clear();
        long maxId = ROOT_ID;
        for (NodeData entry : data) {
            if (entry.id < ROOT_ID || record.nodes.containsKey(entry.id)) {
                throw new IllegalArgumentException("Invalid or duplicate node ID");
            }
            record.nodes.put(entry.id, new GameNode(entry.id, entry.parentId,
                    entry.move, entry.seconds, entry.comment));
            maxId = Math.max(maxId, entry.id);
        }
        GameNode root = record.requireNode(ROOT_ID);
        if (root.parentId() != null || root.move() != Shogi.MOVE_NONE || nextNodeId <= maxId) {
            throw new IllegalArgumentException("Invalid root or next node ID");
        }
        for (GameNode node : record.nodes.values()) {
            if (node.id() != ROOT_ID) {
                if (node.parentId() == null) throw new IllegalArgumentException("Missing parent");
                record.requireNode(node.parentId()).addChild(node.id());
            }
        }
        for (NodeData entry : data) {
            GameNode node = record.nodes.get(entry.id);
            if (node.childIds().isEmpty()) {
                if (entry.mainChildId != null || entry.selectedChildId != null) {
                    throw new IllegalArgumentException("Leaf has a continuation");
                }
            } else {
                if (!node.childIds().contains(entry.mainChildId)
                        || !node.childIds().contains(entry.selectedChildId)) {
                    throw new IllegalArgumentException("Continuation is not a child");
                }
                node.setMainChild(entry.mainChildId);
                record.selectedChildren.put(node.id(), entry.selectedChildId);
            }
        }
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(ROOT_ID);
        int visited = 0;
        while (!pending.isEmpty()) {
            GameNode node = record.nodes.get(pending.removeFirst());
            visited++;
            pending.addAll(node.childIds());
        }
        if (visited != record.nodes.size()) throw new IllegalArgumentException("Disconnected tree");
        record.nextNodeId = nextNodeId;
        record.rebuildRoute();
        for (int ply = 0; ply <= record.length(); ply++) {
            if (record.nodeIdAtPly(ply) == currentNodeId) {
                record.currentPly = ply;
                return record;
            }
        }
        throw new IllegalArgumentException("Cursor is outside selected route");
    }
}
