package org.androshogi.shogi;

import androidx.annotation.NonNull;

public class Board {
    static {
        System.loadLibrary("androshogi");
        // Bitboard tables, Zobrist keys etc. must be set up once before any
        // Position is created, whichever constructor is used first.
        nativeInitialize();
    }

    private long nativeHandle;

    // Constructor
    public Board() {
        nativeHandle = nativeCreate();
    }
    public Board(Board board) {
        nativeHandle = nativeCopy(board.nativeHandle);
    }
    public Board(String sfen) {
        nativeHandle = nativeCreateFromSFEN(sfen);
    }

    public void setSFEN(String sfen) {
        nativeSetSFEN(nativeHandle, sfen);
    }
    public String getSFEN() {
        return nativeGetSFEN(nativeHandle);
    }

    public void reset() {
        nativeReset(nativeHandle);
    }
    public Board copy() { return new Board(this); }

    public LegalMoveList legalMoves() {
        return new LegalMoveList(this);
    }

    public PseudoLegalMoveList pseudoLegalMoves() {
        return new PseudoLegalMoveList(this);
    }

    public int piece(int sq) { return nativePiece(nativeHandle, sq); }
    public int[] pieces() { return nativePieces(nativeHandle); }
    public int[] piecesInHand(int color) { return nativePiecesInHand(nativeHandle, color); }
    public void push(int move) { nativePush(nativeHandle, move); }
    public void pushUSI(String usi) { push(Move.fromUSI(this, usi)); }
    public void pushCSA(String csa) { push(Move.fromCSA(this, csa)); }
    public int pop() { return nativePop(nativeHandle); }
    public int peek() { return nativePeek(nativeHandle); }
    public int turn() { return nativeTurn(nativeHandle); }
    public int getMove(int squareFrom, int squareTo, boolean promotion) {
        return nativeGetMove(nativeHandle, squareFrom, squareTo, promotion);
    }
    public int getDropMove(int squareTo, Shogi.PieceType pieceType) {
        return nativeGetDropMove(nativeHandle, squareTo, pieceType.ordinal());
    }
    public boolean isLegal(int move) { return nativeIsLegal(nativeHandle, move); }

    @NonNull
    @Override
    public String toString() {
        return nativeDump(nativeHandle);
    }

    public void cleanup() {
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
    }

    public long getNativeHandle() {
        return nativeHandle;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            cleanup();
        } finally {
            super.finalize();
        }
    }

    // JNI native methods
    private native long nativeCreate();
    private native long nativeCreateFromSFEN(String sfen);
    private native void nativeSetSFEN(long ptr, String sfen);
    private native String nativeGetSFEN(long ptr);
    private native void nativeDestroy(long ptr);
    private native void nativeReset(long ptr);
    private native long nativeCopy(long ptr);
    private native String nativeDump(long ptr);
    private native int nativePiece(long ptr, int sq);
    private native int[] nativePieces(long ptr);
    private native int[] nativePiecesInHand(long ptr, int color);
    private native void nativePush(long ptr, int move);
    private native int nativePop(long ptr);
    private native int nativePeek(long ptr);
    private native int nativeTurn(long ptr);
    private native int nativeGetMove(long ptr, int squareFrom, int squareTo, boolean promotion);
    private native int nativeGetDropMove(long ptr, int squareTo, int pieceType);
    private native boolean nativeIsLegal(long ptr, int move);
    private static native void nativeInitialize();
}
