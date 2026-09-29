package org.androshogi.shogi;

import java.util.HashMap;
import java.util.Map;

public class Shogi {
    public static final int BLACK = 0;
    public static final int WHITE = 1;

    public static final String STARTING_SFEN = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1";
    public enum Square {
        A1, B1, C1, D1, E1, F1, G1, H1, I1,
        A2, B2, C2, D2, E2, F2, G2, H2, I2,
        A3, B3, C3, D3, E3, F3, G3, H3, I3,
        A4, B4, C4, D4, E4, F4, G4, H4, I4,
        A5, B5, C5, D5, E5, F5, G5, H5, I5,
        A6, B6, C6, D6, E6, F6, G6, H6, I6,
        A7, B7, C7, D7, E7, F7, G7, H7, I7,
        A8, B8, C8, D8, E8, F8, G8, H8, I8,
        A9, B9, C9, D9, E9, F9, G9, H9, I9;

        public int row() {
            char c = this.name().charAt(0);
            return c - 'A';
        }

        public int column() {
            char c = this.name().charAt(1);
            return c - '1';
        }


        public String kanji() {
            char c0 = "１２３４５６７８９".charAt(column());
            char c1 = "一二三四五六七八九".charAt(row());
            return String.valueOf(c0) + c1;
        }
    }

    public static String[] SQUARE_NAMES = new String[]{
            "1a", "1b", "1c", "1d", "1e", "1f", "1g", "1h", "1i",
            "2a", "2b", "2c", "2d", "2e", "2f", "2g", "2h", "2i",
            "3a", "3b", "3c", "3d", "3e", "3f", "3g", "3h", "3i",
            "4a", "4b", "4c", "4d", "4e", "4f", "4g", "4h", "4i",
            "5a", "5b", "5c", "5d", "5e", "5f", "5g", "5h", "5i",
            "6a", "6b", "6c", "6d", "6e", "6f", "6g", "6h", "6i",
            "7a", "7b", "7c", "7d", "7e", "7f", "7g", "7h", "7i",
            "8a", "8b", "8c", "8d", "8e", "8f", "8g", "8h", "8i",
            "9a", "9b", "9c", "9d", "9e", "9f", "9g", "9h", "9i",
    };

    public enum GameResult {
        DRAW, BLACK_WIN, WHITE_WIN
    }

    public enum PieceType {
        NONE,
        PAWN, LANCE, KNIGHT, SILVER,
        BISHOP, ROOK,
        GOLD,
        KING,
        PROM_PAWN, PROM_LANCE, PROM_KNIGHT, PROM_SILVER,
        PROM_BISHOP, PROM_ROOK;

        private static Map<String, PieceType> KANJI_TO_PIECE = new HashMap<>() {
            {
                put("歩", PAWN);
                put("香", LANCE);
                put("桂", KNIGHT);
                put("銀", SILVER);
                put("角", BISHOP);
                put("飛", ROOK);
                put("金", GOLD);
                put("玉", KING);
                put("と", PROM_PAWN);
                put("杏", PROM_LANCE);
                put("圭", PROM_KNIGHT);
                put("全", PROM_SILVER);
                put("馬", PROM_BISHOP);
                put("龍", PROM_ROOK);
            }
        };

        public String toKanji() {
            int index = this.ordinal();
            return "　歩香桂銀角飛金玉と杏圭全馬龍".substring(index, index + 1);
        }

        public static PieceType fromKanji(String kanji) {
            PieceType piece = KANJI_TO_PIECE.get(kanji);
            if (piece != null) {
                return piece;
            }

            return NONE;
        }

        public int toHandPieceIndex() {
            switch (this) {
                case PAWN:
                    return 0;
                case LANCE:
                    return 1;
                case KNIGHT:
                    return 2;
                case SILVER:
                    return 3;
                case GOLD:
                    return 4;
                case BISHOP:
                    return 5;
                case ROOK:
                    return 6;
                default:
                    throw new IllegalArgumentException("Invalid piece type: " + this);
            }
        }

        public static PieceType fromHandPieceIndex(int index) {
            switch (index) {
                case 0:
                    return PAWN;
                case 1:
                    return LANCE;
                case 2:
                    return KNIGHT;
                case 3:
                    return SILVER;
                case 4:
                    return GOLD;
                case 5:
                    return BISHOP;
                case 6:
                    return ROOK;
                default:
                    throw new IllegalArgumentException("Invalid hand piece index: " + index);
            }
        }
    }

//    public enum HandPieceType {
//        HPAWN,
//        HLANCE, HKNIGHT, HSILVER,
//        HGOLD,
//        HBISHOP, HROOK;
//
//        public PieceType piece() {
//            String pieceName = this.name().substring(1);
//            return PieceType.valueOf(pieceName);
//        }
//
//        public static HandPieceType fromPieceType(PieceType type) {
//            return HandPieceType.valueOf("H" + type.toString());
//        }
//    }

    public static class ZenkakuNumber {
        private static final Map<String, Integer> ZENKAKU_TO_INT = new HashMap<>() {
            {
                put("０", 0);
                put("１", 1);
                put("２", 2);
                put("３", 3);
                put("４", 4);
                put("５", 5);
                put("６", 6);
                put("７", 7);
                put("８", 8);
                put("９", 9);
            }
        };

        public static int parse(String zenkaku) {
            Integer value = ZENKAKU_TO_INT.get(zenkaku);
            if (value != null) {
                return value;
            } else {
                throw new IllegalArgumentException("Invalid zenkaku number: " + zenkaku);
            }
        }

        public static String valueOf(int n) {
            return "０１２３４５６７８９".substring(n, n + 1);
        }
    }

    public static class KanjiNumber {
        private static final Map<String, Integer> KANJI_TO_INT = new HashMap<>() {
            {
                put("零", 0);
                put("一", 1);
                put("二", 2);
                put("三", 3);
                put("四", 4);
                put("五", 5);
                put("六", 6);
                put("七", 7);
                put("八", 8);
                put("九", 9);
            }
        };

        public static int parse(String kanji) {
            Integer value = KANJI_TO_INT.get(kanji);
            if (value != null) {
                return value;
            } else {
                throw new IllegalArgumentException("Invalid kanji number: " + kanji);
            }
        }

        public static String valueOf(int n) {
            return "零一二三四五六七八九".substring(n, n + 1);
        }

        /** Parses numbers such as "三", "十", "十八" (1..18 is enough for pieces in hand). */
        public static int parseMultiDigit(String kanji) {
            if (!kanji.contains("十")) {
                return parse(kanji);
            }
            String[] parts = kanji.split("十", -1);
            int tens = parts[0].isEmpty() ? 1 : parse(parts[0]);
            int ones = parts[1].isEmpty() ? 0 : parse(parts[1]);
            return tens * 10 + ones;
        }
    }
    public static final String[] PIECE_SYMBOLS = new String[]{
            "",
            "p", "l", "n", "s", "b", "r", "g", "k", "+p", "+l", "+n", "+s", "+b", "+r"
    };
//    public static final Map<String, Integer> PIECE_JAPANESE_SYMBOLS = new HashMap<>() {
//        {
//            put("", 0);
//            put("歩", 1);
//            put("香", 2);
//            put("桂", 3);
//            put("銀", 4);
//            put("角", 5);
//            put("飛", 6);
//            put("金", 7);
//            put("玉", 8);
//            put("と", 9);
//            put("杏", 10);
//            put("圭", 11);
//            put("全", 12);
//            put("馬", 13);
//            put("龍", 14);
//        }
//    };
    public static final Map<String, Integer> HAND_PIECE_SYMBOLS = new HashMap<>() {
        {
            put("p", 0);
            put("l", 1);
            put("n", 2);
            put("s", 3);
            put("g", 4);
            put("b", 5);
            put("r", 6);
        }
    };
    public static final Map<String, Integer> HAND_PIECE_JAPANESE_SYMBOLS = new HashMap<>() {
        {
            put("歩", 0);
            put("香", 1);
            put("桂", 2);
            put("銀", 3);
            put("金", 4);
            put("角", 5);
            put("飛", 6);
        }
    };

    public int[] MaxPiecesInHand = new int[]{
            18, 4, 4, 4,
            4,
            2, 2,
    };

    public static final int MOVE_NONE = 0;

    public enum RepetitionTypes {
        NOT_REPETITION, REPETITION_DRAW, REPETITION_WIN, REPETITION_LOSE,
        REPETITION_SUPERIOR, REPETITION_INFERIOR
    }
}
