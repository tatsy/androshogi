package org.androshogi.kifu;

import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;
import org.androshogi.shogi.Board;

import org.androshogi.game.GameRecord;
import org.androshogi.game.GameAnalysis;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a game as KIF text, the form {@link KifParser} reads back and other
 * shogi apps import: a header, then one line per move such as
 * {@code    1 ７六歩(77)     ( 0:03/00:00:03)}, with comments as {@code *} lines.
 *
 * <p>{@link #format} and everything below it are free of JNI, so the text is
 * unit tested on the host. {@link #write} decodes the record's move codes with
 * {@link Move} and is the entry point for the activity.
 */
public final class KifWriter {
    /** "from" of a drop, which has no source square. */
    public static final int NO_SQUARE = -1;

    private KifWriter() {}

    /** One move of the record with everything the KIF line needs. */
    public static final class Ply {
        final Shogi.PieceType piece;
        final int from;
        final int to;
        final boolean promote;
        final int seconds;
        final String comment;

        /**
         * @param piece   the piece as it stands before the move
         * @param from    source square index, or {@link #NO_SQUARE} for a drop
         * @param seconds time spent on the move
         * @param comment the comment after the move, or null
         */
        public Ply(Shogi.PieceType piece, int from, int to, boolean promote, int seconds, String comment) {
            this.piece = piece;
            this.from = from;
            this.to = to;
            this.promote = promote;
            this.seconds = seconds;
            this.comment = comment;
        }
    }

    /**
     * The whole record as KifFormat.
     *
     * @param createdAt when the game began, in epoch milliseconds, for the 開始日時 line; 0 leaves it out
     */
    public static String write(GameRecord record, long createdAt) {
        return write(record, createdAt, null);
    }

    /** Non-null analysis opts into exporting results for the selected route. */
    public static String write(GameRecord record, long createdAt, GameAnalysis analysis) {
        List<Ply> plies = new ArrayList<>(record.length());
        Board board = analysis == null ? null : new Board(record.startSfen());
        String startComment = null;
        try {
            if (board != null) startComment = KifAnalysisComment.describe(board, analysis.get(0), 0, Shogi.MOVE_NONE);
            for (int i = 0; i < record.length(); i++) {
                int move = record.move(i);
                boolean drop = Move.isDrop(move);
                String comment = record.comment(i);
                if (board != null) {
                    board.push(move);
                    String extra = KifAnalysisComment.describe(board, analysis.get(i + 1), i + 1, move);
                    if (extra != null) {
                        comment = comment == null || comment.isEmpty() ? extra : comment + "\n" + extra;
                    }
                }
                plies.add(new Ply(Shogi.PieceType.values()[Move.piece(move)],
                        drop ? NO_SQUARE : Move.source(move), Move.dest(move), Move.isProm(move),
                        record.timeSeconds(i), comment));
            }
        } finally {
            if (board != null) board.cleanup();
        }
        return format(record.startSfen(), record.blackName(), record.whiteName(), createdAt, plies, startComment);
    }

    /**
     * The KIF text for a game that starts from {@code startSfen}. A handicap
     * position is written by its name (手合割); any other one as a board
     * diagram (BOD), which is what the common readers expect.
     */
    public static String format(String startSfen, String blackName, String whiteName,
                                long createdAt, List<Ply> plies) {
        return format(startSfen, blackName, whiteName, createdAt, plies, null);
    }

    static String format(String startSfen, String blackName, String whiteName,
                         long createdAt, List<Ply> plies, String startComment) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ---- AndroShogi 棋譜ファイル ----\n");
        if (createdAt > 0) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);
            sb.append("開始日時：").append(dateFormat.format(new Date(createdAt))).append('\n');
        }
        String handicap = handicapName(startSfen);
        if (handicap != null) {
            sb.append("手合割：").append(handicap).append('\n');
        } else {
            sb.append(boardDiagram(startSfen));
        }
        sb.append("先手：").append(blackName).append('\n');
        sb.append("後手：").append(whiteName).append('\n');
        sb.append("手数----指手---------消費時間--\n");
        appendComment(sb, startComment);

        // 消費時間 accumulates per player, as the readers show it.
        int[] total = new int[2];
        int previousTo = NO_SQUARE;
        int side = sideToMove(startSfen);
        for (int i = 0; i < plies.size(); i++) {
            Ply ply = plies.get(i);
            total[side] += ply.seconds;
            String text = moveText(ply.piece, ply.from, ply.to, ply.promote, previousTo);
            sb.append(String.format(Locale.ROOT, "%4d %s%s(%2d:%02d/%02d:%02d:%02d)\n",
                    i + 1, text, padding(text),
                    ply.seconds / 60, ply.seconds % 60,
                    total[side] / 3600, total[side] % 3600 / 60, total[side] % 60));
            appendComment(sb, ply.comment);
            previousTo = ply.to;
            side ^= 1;
        }
        return sb.toString();
    }

    private static void appendComment(StringBuilder sb, String comment) {
        if (comment == null || comment.isEmpty()) return;
        for (String line : comment.split("\n", -1)) sb.append('*').append(line).append('\n');
    }

    /**
     * The move as KIF writes it: the square (同　 when it is the previous move's),
     * the piece, 成 when promoting, and the source square in digits, or 打.
     * Examples: ７六歩(77), 同　歩(23), ２二角成(88), ５五角打.
     */
    public static String moveText(Shogi.PieceType piece, int from, int to, boolean promote, int previousTo) {
        StringBuilder sb = new StringBuilder();
        sb.append(to == previousTo ? "同　" : Shogi.Square.values()[to].kanji());
        sb.append(piece.toKanji());
        if (from == NO_SQUARE) {
            sb.append("打");
        } else {
            if (promote) {
                sb.append("成");
            }
            sb.append('(').append(KifFormat.KIFU_FROM_SQUARE_NAMES[from]).append(')');
        }
        return sb.toString();
    }

    /** Spaces that bring every move text to the same column, so the times line up. */
    private static String padding(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) < 0x80 ? 1 : 2;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = width; i < 15; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** The 手合割 name of {@code sfen}, or null when it is not one of the standard handicaps. */
    static String handicapName(String sfen) {
        String position = positionFields(sfen);
        for (Map.Entry<String, String> entry : KifFormat.HANDYCAP_SFENS.entrySet()) {
            if (entry.getValue() != null && positionFields(entry.getValue()).equals(position)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Board, side to move and hands: the SFEN without its move counter. */
    private static String positionFields(String sfen) {
        String[] fields = sfen.trim().split("\\s+");
        return fields.length >= 3 ? fields[0] + " " + fields[1] + " " + fields[2] : sfen.trim();
    }

    private static int sideToMove(String sfen) {
        String[] fields = sfen.trim().split("\\s+");
        return fields.length >= 2 && fields[1].equals("w") ? Shogi.WHITE : Shogi.BLACK;
    }

    /**
     * The position as a board diagram, the KIF way of giving a start position
     * that has no handicap name:
     * <pre>
     * 後手の持駒：角　歩二
     *   ９ ８ ７ ６ ５ ４ ３ ２ １
     * +---------------------------+
     * |v香v桂 ・ ・ ・ ・ ・v桂v香|一
     * ...
     * +---------------------------+
     * 先手の持駒：なし
     * 後手番
     * </pre>
     */
    static String boardDiagram(String sfen) {
        String[] fields = sfen.trim().split("\\s+");
        String[] ranks = fields[0].split("/");
        String hands = fields.length >= 3 ? fields[2] : "-";
        StringBuilder sb = new StringBuilder();
        sb.append("後手の持駒：").append(handText(hands, false)).append('\n');
        sb.append("  ９ ８ ７ ６ ５ ４ ３ ２ １\n");
        sb.append("+---------------------------+\n");
        for (int rank = 0; rank < 9; rank++) {
            sb.append('|');
            String row = rank < ranks.length ? ranks[rank] : "9";
            boolean promoted = false;
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c == '+') {
                    promoted = true;
                } else if (Character.isDigit(c)) {
                    for (int n = c - '0'; n > 0; n--) {
                        sb.append(" ・");
                    }
                } else {
                    sb.append(Character.isUpperCase(c) ? ' ' : 'v');
                    sb.append(pieceKanji(c, promoted));
                    promoted = false;
                }
            }
            sb.append('|').append(Shogi.KanjiNumber.valueOf(rank + 1)).append('\n');
        }
        sb.append("+---------------------------+\n");
        sb.append("先手の持駒：").append(handText(hands, true)).append('\n');
        if (sideToMove(sfen) == Shogi.WHITE) {
            sb.append("後手番\n");
        }
        return sb.toString();
    }

    private static final String HAND_ORDER = "RBGSNLP";

    /** One side's pieces in hand from the SFEN hand field, e.g. "角　歩二", or なし. */
    private static String handText(String hands, boolean black) {
        int[] count = new int[HAND_ORDER.length()];
        int n = 0;
        for (int i = 0; i < hands.length(); i++) {
            char c = hands.charAt(i);
            if (Character.isDigit(c)) {
                n = n * 10 + (c - '0');
                continue;
            }
            int index = HAND_ORDER.indexOf(Character.toUpperCase(c));
            if (index >= 0 && Character.isUpperCase(c) == black) {
                count[index] += n == 0 ? 1 : n;
            }
            n = 0;
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < count.length; i++) {
            if (count[i] == 0) {
                continue;
            }
            String kanji = pieceKanji(HAND_ORDER.charAt(i), false);
            parts.add(count[i] == 1 ? kanji : kanji + kanjiNumber(count[i]));
        }
        return parts.isEmpty() ? "なし" : String.join("　", parts);
    }

    /** 1..18 as 一 … 十八. */
    private static String kanjiNumber(int n) {
        if (n < 10) {
            return Shogi.KanjiNumber.valueOf(n);
        }
        String tens = n / 10 == 1 ? "" : Shogi.KanjiNumber.valueOf(n / 10);
        String ones = n % 10 == 0 ? "" : Shogi.KanjiNumber.valueOf(n % 10);
        return tens + "十" + ones;
    }

    private static String pieceKanji(char sfenPiece, boolean promoted) {
        int index = "PLNSBRGK".indexOf(Character.toUpperCase(sfenPiece));
        if (index < 0) {
            throw new IllegalArgumentException("Unknown piece: " + sfenPiece);
        }
        Shogi.PieceType type = Shogi.PieceType.values()[index + 1];
        if (promoted) {
            type = Shogi.PieceType.values()[type.ordinal() + Shogi.PieceType.PROM_PAWN.ordinal() - 1];
        }
        return type.toKanji();
    }
}
