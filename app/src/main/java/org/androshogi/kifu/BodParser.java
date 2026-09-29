package org.androshogi.kifu;

import org.androshogi.shogi.Shogi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the board diagram (BOD) that KIF uses for a start position without a
 * handicap name, the form {@link KifWriter#boardDiagram} writes:
 * <pre>
 * |v香v桂 ・ ・ ・ ・ ・v桂v香|一
 * </pre>
 * Nine such rows, the two 持駒 lines and an optional 後手番 line become an SFEN.
 * Free of JNI, so it is unit tested on the host.
 */
public final class BodParser {
    private BodParser() {}

    /** True for a row of the diagram, which is what {@link KifParser} feeds to {@link #toSfen}. */
    public static boolean isBoardRow(String line) {
        return line.startsWith("|") && line.indexOf('|', 1) > 0;
    }

    /**
     * The SFEN of a diagram.
     *
     * @param rows      the nine board rows, rank 一 first
     * @param blackHand black's pieces in hand, by type
     * @param whiteHand white's pieces in hand, by type
     * @throws IllegalArgumentException when the rows are not a board or do not contain
     *                                  exactly one king for each side
     */
    public static String toSfen(List<String> rows, Map<Shogi.PieceType, Integer> blackHand,
                                Map<Shogi.PieceType, Integer> whiteHand, boolean whiteToMove) {
        if (rows.size() != 9) {
            throw new IllegalArgumentException("A board diagram has 9 rows, not " + rows.size());
        }

        int blackKings = 0;
        int whiteKings = 0;
        List<String> sfenRows = new ArrayList<>(9);
        for (String row : rows) {
            String sfenRow = rowToSfen(row);
            sfenRows.add(sfenRow);
            for (int i = 0; i < sfenRow.length(); i++) {
                char piece = sfenRow.charAt(i);
                if (piece == 'K') {
                    blackKings++;
                } else if (piece == 'k') {
                    whiteKings++;
                }
            }
        }

        if (blackKings != 1 || whiteKings != 1) {
            throw new IllegalArgumentException("盤面図には先手玉と後手玉が1枚ずつ必要です");
        }

        return String.join("/", sfenRows) + (whiteToMove ? " w " : " b ")
                + handToSfen(blackHand, whiteHand) + " 1";
    }

    /** "|v香v桂 ・ ・ ・ ・ ・v桂v香|一" to "ln5nl". */
    private static String rowToSfen(String row) {
        int start = row.indexOf('|') + 1;
        int end = row.indexOf('|', start);
        String cells = row.substring(start, end)
                .replace("王", "玉").replace("竜", "龍");
        if (cells.length() != 18) {
            throw new IllegalArgumentException("Not a board row: " + row);
        }
        StringBuilder sb = new StringBuilder();
        int empty = 0;
        for (int i = 0; i < 18; i += 2) {
            char owner = cells.charAt(i);
            String kanji = cells.substring(i + 1, i + 2);
            if (kanji.equals("・")) {
                empty++;
                continue;
            }
            Shogi.PieceType type = Shogi.PieceType.fromKanji(kanji);
            if (type == Shogi.PieceType.NONE) {
                throw new IllegalArgumentException("Unknown piece in board row: " + kanji);
            }
            if (empty > 0) {
                sb.append(empty);
                empty = 0;
            }
            String symbol = Shogi.PIECE_SYMBOLS[type.ordinal()];
            sb.append(owner == 'v' || owner == 'V' ? symbol : symbol.toUpperCase());
        }
        if (empty > 0) {
            sb.append(empty);
        }
        return sb.toString();
    }

    private static final Shogi.PieceType[] HAND_ORDER = {
            Shogi.PieceType.ROOK, Shogi.PieceType.BISHOP, Shogi.PieceType.GOLD, Shogi.PieceType.SILVER,
            Shogi.PieceType.KNIGHT, Shogi.PieceType.LANCE, Shogi.PieceType.PAWN,
    };

    private static String handToSfen(Map<Shogi.PieceType, Integer> blackHand, Map<Shogi.PieceType, Integer> whiteHand) {
        StringBuilder sb = new StringBuilder();
        appendHand(sb, blackHand, true);
        appendHand(sb, whiteHand, false);
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static void appendHand(StringBuilder sb, Map<Shogi.PieceType, Integer> hand, boolean black) {
        if (hand == null) {
            return;
        }
        for (Shogi.PieceType type : HAND_ORDER) {
            Integer count = hand.get(type);
            if (count == null || count <= 0) {
                continue;
            }
            if (count > 1) {
                sb.append(count);
            }
            String symbol = Shogi.PIECE_SYMBOLS[type.ordinal()];
            sb.append(black ? symbol.toUpperCase() : symbol);
        }
    }
}
