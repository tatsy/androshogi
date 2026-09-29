package org.androshogi.shogi;

import androidx.annotation.NonNull;

public class Move {
    static {
        System.loadLibrary("androshogi");
    }

    private static final int SQUARE_NUM = 81;
    private int code;

    public Move(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public int dest() {
        return Move.dest(code);
    }

    public static int dest(int code) {
        return code & 0x7f;
    }

    public int source() {
        return Move.source(code);
    }

    public static int source(int code) {
        return (code >> 7) & 0x7f;
    }

    public boolean isProm() {
        return Move.isProm(code);
    }

    public static boolean isProm(int code) {
        return ((code >> 14) & 0x1) != 0;
    }

    public Shogi.PieceType piece() {
        return Shogi.PieceType.values()[Move.piece(code)];
    }

    static public int piece(int code) {
        int index = Move.isDrop(code) ? Move.source(code) - (SQUARE_NUM - 1) : (code >> 16) & 0xf;
        return Shogi.PieceType.values()[index].ordinal();
    }

    public boolean isDrop() {
        return Move.isDrop(code);
    }

    public static boolean isDrop(int code) {
        return Move.source(code) >= SQUARE_NUM;
    }

    public static String toUSI(int move) {
        return nativeToUSI(move);
    }

    public static String toCSA(int move) {
        return nativeToCSA(move);
    }

    public static int fromUSI(Board board, String usi) {
        return nativeFromUSI(board.getNativeHandle(), usi);
    }

    public static int fromCSA(Board board, String csa) {
        return nativeFromCSA(board.getNativeHandle(), csa);
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Shogi.Square sq = Shogi.Square.values()[dest()];
        sb.append(sq.kanji());
        sb.append(piece().toKanji());
        if (isProm()) { sb.append("成"); }
        if (isDrop()) { sb.append("打"); }
        return sb.toString();
    }

    private static native String nativeToUSI(int move);
    private static native String nativeToCSA(int move);
    private static native int nativeFromUSI(long ptr, String usi);
    private static native int nativeFromCSA(long ptr, String csa);
}
