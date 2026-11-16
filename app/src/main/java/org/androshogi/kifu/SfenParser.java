package org.androshogi.kifu;

/** Validates a standalone SFEN before any untrusted text reaches the native board. */
public final class SfenParser {
    private static final String TYPES = "PLNSGBRK";
    private static final int[] LIMITS = {18, 4, 4, 4, 4, 2, 2, 2};
    private static final String HAND_ORDER = "RBGSNLP";

    private SfenParser() {}

    /** Clipboard detection only; KIF file and shared-text loading keep their existing parser. */
    public static boolean looksLikeSfen(String text) {
        String value = clean(text);
        String[] fields = value.split("\\s+", 4);
        String first = fields[0];
        if (first.equals("sfen") || first.equals("position")) return true;
        return (first.contains("/") && !first.contains("：") && !first.contains(":")
                && !first.startsWith("#") && !first.startsWith("*"))
                || (fields.length >= 2 && (fields[1].equals("b") || fields[1].equals("w")));
    }

    /**
     * Accepts four SFEN fields, optionally prefixed with "sfen" or "position sfen".
     * No move history is accepted. The move number is validated, then reset to 1:
     * this position starts a new record whose cursor and move list count from zero.
     * Missing pieces are allowed, but both kings and ordinary position rules are required.
     */
    public static String parse(String text) {
        String[] fields = clean(text).split("\\s+");
        int offset = fields.length > 0 && fields[0].equals("sfen") ? 1 : 0;
        if (fields.length > 1 && fields[0].equals("position") && fields[1].equals("sfen")) {
            offset = 2;
        }
        require(fields.length - offset == 4,
                "SFEN は盤面・手番・持ち駒・手数の4項目で指定してください（指し手の列は非対応です）");
        String placement = fields[offset];
        String turn = fields[offset + 1];
        String hand = fields[offset + 2];
        String moveNumber = fields[offset + 3];
        require(turn.equals("b") || turn.equals("w"), "手番には b（先手）か w（後手）を指定してください");
        require(moveNumber.matches("[1-9][0-9]*"), "手数には1以上の整数を指定してください");
        try {
            Integer.parseInt(moveNumber);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("手数が大きすぎます");
        }

        // Positive pieces are black, negative pieces white; promoted types have 8 added.
        int[][] board = new int[9][9];
        int[] totals = new int[8];
        int[] kings = new int[2];
        int[][] kingSquares = new int[2][2];
        String[] ranks = placement.split("/", -1);
        require(ranks.length == 9, "盤面は / で区切った9段で指定してください");
        for (int rank = 0; rank < 9; rank++) {
            int file = 0;
            boolean previousDigit = false;
            for (int i = 0; i < ranks[rank].length(); i++) {
                char symbol = ranks[rank].charAt(i);
                if (symbol >= '1' && symbol <= '9') {
                    require(!previousDigit, "空き升の数字を連続して指定することはできません");
                    file += symbol - '0';
                    require(file <= 9, "盤面の各段は9升で指定してください");
                    previousDigit = true;
                    continue;
                }
                previousDigit = false;
                boolean promoted = symbol == '+';
                if (promoted) {
                    require(++i < ranks[rank].length(), "成駒の + の後には駒を指定してください");
                    symbol = ranks[rank].charAt(i);
                }
                require("PLNSGBRKplnsgbrk".indexOf(symbol) >= 0, "盤面の駒が正しくありません");
                int type = TYPES.indexOf(Character.toUpperCase(symbol));
                require(type >= 0 && file < 9, "盤面の駒または升数が正しくありません");
                require(!promoted || (type != 4 && type != 7), "金と玉は成駒にできません");
                int side = Character.isUpperCase(symbol) ? 0 : 1;
                int encoded = type + 1 + (promoted ? 8 : 0);
                board[rank][file] = side == 0 ? encoded : -encoded;
                totals[type]++;
                if (type == 7) {
                    kings[side]++;
                    kingSquares[side][0] = rank;
                    kingSquares[side][1] = file;
                }
                int distanceToLastRank = side == 0 ? rank : 8 - rank;
                require(promoted || !((type <= 1 && distanceToLastRank == 0)
                                || (type == 2 && distanceToLastRank <= 1)),
                        "行き場のない歩・香・桂を配置することはできません");
                file++;
            }
            require(file == 9, "盤面の各段は9升で指定してください");
        }
        require(kings[0] == 1 && kings[1] == 1, "先手玉と後手玉が1枚ずつ必要です");

        int[][] hands = new int[2][7];
        if (!hand.equals("-")) {
            int count = 0;
            for (int i = 0; i < hand.length(); i++) {
                char symbol = hand.charAt(i);
                if (symbol >= '0' && symbol <= '9') {
                    require(symbol != '0' || count > 0, "持ち駒の枚数に0や先頭の0は指定できません");
                    count = count * 10 + symbol - '0';
                    require(count <= 18, "持ち駒の枚数が多すぎます");
                    continue;
                }
                require("PLNSGBRplnsgbr".indexOf(symbol) >= 0, "持ち駒には成駒や玉を指定できません");
                int type = TYPES.indexOf(Character.toUpperCase(symbol));
                require(type >= 0 && type < 7, "持ち駒には成駒や玉を指定できません");
                int side = Character.isUpperCase(symbol) ? 0 : 1;
                require(hands[side][type] == 0, "同じ持ち駒を重複して指定することはできません");
                hands[side][type] = count == 0 ? 1 : count;
                totals[type] += hands[side][type];
                count = 0;
            }
            require(count == 0, "持ち駒の枚数の後には駒を指定してください");
        }
        for (int type = 0; type < totals.length; type++) {
            require(totals[type] <= LIMITS[type], "盤上と持ち駒を合わせた駒の枚数が多すぎます");
        }
        for (int file = 0; file < 9; file++) {
            int[] pawns = new int[2];
            for (int rank = 0; rank < 9; rank++) {
                if (board[rank][file] == 1) pawns[0]++;
                if (board[rank][file] == -1) pawns[1]++;
            }
            require(pawns[0] <= 1 && pawns[1] <= 1, "二歩の局面は取り込めません");
        }

        // The player who just moved cannot have left their own king attacked.
        // Otherwise native move generation can produce a king capture.
        int sideToMove = turn.equals("b") ? 0 : 1;
        int[] enemyKing = kingSquares[1 - sideToMove];
        for (int rank = 0; rank < 9; rank++) {
            for (int file = 0; file < 9; file++) {
                int piece = board[rank][file];
                if (piece == 0 || (piece > 0 ? 0 : 1) != sideToMove) continue;
                require(!attacks(board, rank, file, enemyKing[0], enemyKing[1]),
                        "手番側が相手玉を取れる局面は取り込めません（手番を確認してください）");
            }
        }

        StringBuilder canonicalHand = new StringBuilder();
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < HAND_ORDER.length(); i++) {
                char symbol = HAND_ORDER.charAt(i);
                int count = hands[side][TYPES.indexOf(symbol)];
                if (count == 0) continue;
                if (count > 1) canonicalHand.append(count);
                canonicalHand.append(side == 0 ? symbol : Character.toLowerCase(symbol));
            }
        }
        return placement + " " + turn + " "
                + (canonicalHand.length() == 0 ? "-" : canonicalHand) + " 1";
    }

    /** Geometric attacks only, used to reject an already capturable king, not to generate moves. */
    private static boolean attacks(int[][] board, int rank, int file, int toRank, int toFile) {
        int piece = board[rank][file];
        int type = Math.abs(piece);
        int dr = toRank - rank;
        int df = toFile - file;
        int forward = piece > 0 ? -1 : 1;
        int relativeRank = dr * forward;
        if (type >= 9 && type <= 12) type = 5; // と・成香・成桂・成銀 use gold attacks.
        switch (type) {
            case 1: return relativeRank == 1 && df == 0;
            case 2: if (relativeRank <= 0 || df != 0) return false; break;
            case 3: return relativeRank == 2 && Math.abs(df) == 1;
            case 4: return (relativeRank == 1 && Math.abs(df) <= 1)
                    || (relativeRank == -1 && Math.abs(df) == 1);
            case 5: return (relativeRank == 1 && Math.abs(df) <= 1)
                    || (dr == 0 && Math.abs(df) == 1) || (relativeRank == -1 && df == 0);
            case 8: return Math.max(Math.abs(dr), Math.abs(df)) == 1;
            case 14: if (Math.abs(dr) + Math.abs(df) == 1) return true; // 馬
            case 6: if (Math.abs(dr) != Math.abs(df)) return false; break;
            case 15: if (Math.abs(dr) == 1 && Math.abs(df) == 1) return true; // 竜
            case 7: if (dr != 0 && df != 0) return false; break;
            default: return false;
        }
        int stepRank = Integer.signum(dr);
        int stepFile = Integer.signum(df);
        for (int r = rank + stepRank, f = file + stepFile; r != toRank || f != toFile;
                r += stepRank, f += stepFile) {
            if (board[r][f] != 0) return false;
        }
        return true;
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim().replaceFirst("^\uFEFF", "").trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
