package org.androshogi.kifu;

import org.androshogi.shogi.Board;
import org.androshogi.shogi.LegalMoveList;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;

import org.androshogi.game.GameRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Japanese move notation after the 日本将棋連盟 rules: the square (or 同 when
 * it is the previous move's), the piece, and then whatever the rules call for:
 * 成／不成, 打, and 右／左／上／引／寄／直 when another piece of the same kind
 * could have reached the same square.
 *
 * <p>{@link #format} is the rule and is free of JNI, so it is unit tested on
 * the host. {@link #describe} gathers the alternatives from the native board
 * and is the entry point for the views.
 *
 * <p>竜 and 馬 are the one exception to "motion first": the Federation's own
 * example writes ８二竜左 (from ９一, a retreat) and ８二竜右 (from ７二, a
 * sideways move), so for them the file decides before the motion.
 */
public final class MoveNotation {
    /** "from" of a drop, which has no source square. */
    public static final int NO_SQUARE = -1;

    private MoveNotation() {}

    /**
     * The notation of a move shown on {@code board}.
     *
     * @param previousMove the move that led to {@code board}, or {@link Shogi#MOVE_NONE};
     *                     decides 同
     */
    public static String describe(Board board, int move, int previousMove) {
        int to = Move.dest(move);
        boolean drop = Move.isDrop(move);
        int from = drop ? NO_SQUARE : Move.source(move);
        int pieceIndex = Move.piece(move);
        List<Integer> otherFroms = new ArrayList<>();
        if (!drop) {
            LegalMoveList legal = board.legalMoves();
            try {
                for (int other : legal) {
                    if (Move.isDrop(other) || Move.dest(other) != to || Move.piece(other) != pieceIndex) {
                        continue;
                    }
                    int src = Move.source(other);
                    if (src != from && !otherFroms.contains(src)) {
                        otherFroms.add(src);
                    }
                }
            } finally {
                legal.cleanup();
            }
        }
        int previousTo = previousMove == Shogi.MOVE_NONE ? NO_SQUARE : Move.dest(previousMove);
        return format(Shogi.PieceType.values()[pieceIndex], from, to, Move.isProm(move),
                board.turn(), previousTo, otherFroms);
    }

    /** The notation of every move of {@code record}, replayed from its start position. */
    public static List<String> describeRecord(GameRecord record) {
        List<String> out = new ArrayList<>(record.length());
        Board board = new Board(record.startSfen());
        try {
            int previous = Shogi.MOVE_NONE;
            for (int i = 0; i < record.length(); i++) {
                int move = record.move(i);
                out.add(describe(board, move, previous));
                board.push(move);
                previous = move;
            }
        } finally {
            board.cleanup();
        }
        return out;
    }

    /**
     * The rule itself.
     *
     * @param piece      the piece as it stands before the move (a promoted piece is its own kind)
     * @param from       source square index, or {@link #NO_SQUARE} for a drop
     * @param to         destination square index
     * @param promote    whether the move promotes
     * @param sideToMove {@link Shogi#BLACK} or {@link Shogi#WHITE}; 上／右 are from the mover's view
     * @param previousTo destination of the previous move, or {@link #NO_SQUARE}
     * @param otherFroms source squares of the other legal moves of the same kind of piece to {@code to}
     */
    public static String format(Shogi.PieceType piece, int from, int to, boolean promote,
                                int sideToMove, int previousTo, List<Integer> otherFroms) {
        StringBuilder sb = new StringBuilder();
        sb.append(to == previousTo ? "同" : Shogi.Square.values()[to].kanji());
        sb.append(pieceName(piece));
        if (from == NO_SQUARE) {
            sb.append("打");
            return sb.toString();
        }
        if (!otherFroms.isEmpty()) {
            sb.append(relativeMark(piece, from, to, sideToMove, otherFroms));
        }
        if (promote) {
            sb.append("成");
        } else if (canPromote(piece, from, to, sideToMove)) {
            sb.append("不成");
        }
        return sb.toString();
    }

    /** Move text spells out promoted minor pieces; board symbols remain single characters. */
    private static String pieceName(Shogi.PieceType piece) {
        switch (piece) {
            case PROM_LANCE:
                return "成香";
            case PROM_KNIGHT:
                return "成桂";
            case PROM_SILVER:
                return "成銀";
            default:
                return piece.toKanji();
        }
    }

    // Vertical motion seen from the mover, and lateral position of the source seen from the mover.
    private static final int UP = 0, DOWN = 1, SIDEWAYS = 2;
    private static final int RIGHT = 0, LEFT = 1, STRAIGHT = 2;

    private static String relativeMark(Shogi.PieceType piece, int from, int to, int side, List<Integer> others) {
        int myMotion = motion(from, to, side);
        int mySide = lateral(from, to, side);
        boolean motionUnique = true;
        boolean sideUnique = true;
        boolean rightmost = true;
        boolean leftmost = true;
        for (int other : others) {
            if (motion(other, to, side) == myMotion) {
                motionUnique = false;
            }
            if (lateral(other, to, side) == mySide) {
                sideUnique = false;
            }
            int order = compareRightward(from, other, side);
            if (order <= 0) {
                rightmost = false;
            }
            if (order >= 0) {
                leftmost = false;
            }
        }

        boolean dragonOrHorse = piece == Shogi.PieceType.PROM_ROOK || piece == Shogi.PieceType.PROM_BISHOP;
        if (dragonOrHorse) {
            // 竜 and 馬 go by where they stand, whatever their motion: the one
            // further right is 右, the other 左, and only on the same file do
            // 上 and 引 decide. They never take 直.
            if (rightmost) {
                return "右";
            }
            if (leftmost) {
                return "左";
            }
            return motionMark(myMotion);
        }

        // 1. The way the piece moves settles it: 上 (forward), 引 (back), 寄 (sideways).
        if (motionUnique) {
            return motionMark(myMotion);
        }

        // 2. Straight forward is 直; otherwise the side the piece came from.
        if (mySide == STRAIGHT && myMotion == UP) {
            return "直";
        }
        if (sideUnique && mySide != STRAIGHT) {
            return lateralMark(mySide);
        }
        if (mySide == STRAIGHT) {
            // Straight but not forward, which 直 does not cover: fall back to the
            // relative order, then to the motion.
            if (rightmost) {
                return "右";
            }
            if (leftmost) {
                return "左";
            }
            return motionMark(myMotion);
        }
        // 3. Neither alone is enough: 右上, 左引, 右寄 and so on.
        return lateralMark(mySide) + motionMark(myMotion);
    }

    private static int row(int square) { return square % 9; }
    private static int column(int square) { return square / 9; }

    /** Forward is toward rank 一 for black and toward rank 九 for white. */
    private static int motion(int from, int to, int side) {
        int delta = row(to) - row(from);
        if (side == Shogi.WHITE) {
            delta = -delta;
        }
        return delta < 0 ? UP : delta > 0 ? DOWN : SIDEWAYS;
    }

    /** Black's right is toward file １ (smaller column); white's is the opposite. */
    private static int lateral(int from, int to, int side) {
        int delta = column(from) - column(to);
        if (side == Shogi.WHITE) {
            delta = -delta;
        }
        return delta < 0 ? RIGHT : delta > 0 ? LEFT : STRAIGHT;
    }

    /** Positive when {@code a} is further right than {@code b} from the mover's view. */
    private static int compareRightward(int a, int b, int side) {
        int delta = column(b) - column(a);
        return side == Shogi.WHITE ? -delta : delta;
    }

    private static String motionMark(int motion) {
        return motion == UP ? "上" : motion == DOWN ? "引" : "寄";
    }

    private static String lateralMark(int lateral) {
        return lateral == RIGHT ? "右" : "左";
    }

    /** True when the move could promote, so that declining has to be written as 不成. */
    static boolean canPromote(Shogi.PieceType piece, int from, int to, int side) {
        switch (piece) {
            case PAWN:
            case LANCE:
            case KNIGHT:
            case SILVER:
            case BISHOP:
            case ROOK:
                break;
            default:
                return false;
        }
        return from != NO_SQUARE && (inPromotionZone(from, side) || inPromotionZone(to, side));
    }

    private static boolean inPromotionZone(int square, int side) {
        int row = row(square);
        return side == Shogi.BLACK ? row <= 2 : row >= 6;
    }
}
