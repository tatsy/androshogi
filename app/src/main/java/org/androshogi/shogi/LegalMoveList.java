package org.androshogi.shogi;

import androidx.annotation.NonNull;

import java.util.Iterator;

public class LegalMoveList implements Iterable<Integer> {
    static {
        System.loadLibrary("androshogi");
    }

    private long nativeHandle;

    public LegalMoveList(Board board) {
        nativeHandle = nativeCreate(board.getNativeHandle());
    }

    // Clean up native resources
    public void cleanup() {
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
    }

    public int size() {
        return nativeSize(nativeHandle);
    }

    @NonNull
    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !nativeEnd(nativeHandle);
            }

            @Override
            public Integer next() {
                int move = nativeMove(nativeHandle);
                nativeNext(nativeHandle);
                return move;
            }
        };
    }

    // JNI native methods
    private native long nativeCreate(long boardPtr);
    private native void nativeDestroy(long ptr);
    private native boolean nativeEnd(long ptr);
    private native int nativeMove(long ptr);
    private native void nativeNext(long ptr);
    private native int nativeSize(long ptr);

    @Override
    protected void finalize() throws Throwable {
        cleanup();
        super.finalize();
    }
}
