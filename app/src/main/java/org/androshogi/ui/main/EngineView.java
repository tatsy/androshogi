package org.androshogi.ui.main;

import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;

import org.androshogi.engine.EngineInfo;
import org.androshogi.engine.EngineUpdateListener;

import org.androshogi.kifu.MoveNotation;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

import java.util.List;
import java.util.Locale;

/** Shows the engine's search statistics and its candidate variations. */
public class EngineView extends AppCompatTextView implements EngineUpdateListener {
    /** Number of moves of each variation that is spelled out. */
    private static final int PV_MOVES_SHOWN = 5;

    /** True: scores from black's point of view. False: from the side to move, as the engine reports. */
    private boolean scoreFromBlack = true;
    /**
     * The move that led to the position being shown, so that a first variation
     * move onto the same square reads 同. The engine's board is built from an
     * SFEN and has no history of its own.
     */
    private int previousMove = Shogi.MOVE_NONE;

    public EngineView(Context context, AttributeSet attr) {
        super(context, attr);
    }

    public EngineView(Context context) {
        super(context);
    }

    public void setScoreFromBlack(boolean fromBlack) {
        this.scoreFromBlack = fromBlack;
    }

    /** Sets the move that led to the position on screen, or {@link Shogi#MOVE_NONE}. */
    public void setPreviousMove(int move) {
        this.previousMove = move;
    }

    @Override
    public void onEngineUpdated(Board board, List<EngineInfo> infos) {
        if (infos.isEmpty()) {
            // A new search has started, or the engine reported nothing at all.
            setText("");
            return;
        }

        StringBuilder msg = new StringBuilder();
        appendSearchStatistics(msg, infos.get(0));
        for (EngineInfo info : infos) {
            appendCandidate(msg, board, info);
        }
        setText(msg.toString());
    }

    /** Appends only the statistics the engine actually reported. */
    private void appendSearchStatistics(StringBuilder msg, EngineInfo info) {
        if (info.depth() != EngineInfo.NO_VALUE) {
            if (info.seldepth() != EngineInfo.NO_VALUE) {
                msg.append(String.format(Locale.JAPANESE, "深さ: %d/%d",
                        info.seldepth(), info.depth()));
            } else {
                msg.append(String.format(Locale.JAPANESE, "深さ: %d", info.depth()));
            }
            msg.append("　");
        }
        if (info.nodes() != EngineInfo.NO_VALUE) {
            msg.append(String.format(Locale.JAPANESE, "ノード数: %dk", info.nodes() / 1000));
            msg.append("　");
        }
        if (info.nps() != EngineInfo.NO_VALUE) {
            msg.append(String.format(Locale.JAPANESE, "(速度: %dk/s)", info.nps() / 1000));
        }
        msg.append("\n");
    }

    private void appendCandidate(StringBuilder msg, Board board, EngineInfo info) {
        msg.append(info.multipv()).append(": ");
        appendScore(msg, board, info);
        msg.append("　");
        appendVariation(msg, board, info);
        msg.append("\n");
    }

    private void appendScore(StringBuilder msg, Board board, EngineInfo info) {
        if (!info.hasScore()) {
            return;
        }
        // The engine scores from the side to move; flip it for black's view if asked.
        int sign = (!scoreFromBlack || board.turn() == Shogi.BLACK) ? 1 : -1;
        int score = info.score() * sign;
        if (!info.isMate()) {
            msg.append(String.format(Locale.JAPANESE, "%d", score));
        } else if (info.isMateWithUnknownPly()) {
            msg.append(score < 0 ? "-詰" : "+詰");
        } else {
            msg.append(String.format(Locale.JAPANESE, "%s詰%d",
                    score < 0 ? "-" : "+", Math.abs(score)));
        }
    }

    private void appendVariation(StringBuilder msg, Board board, EngineInfo info) {
        List<String> pv = info.pv();
        Board copyBoard = board.copy();
        try {
            // The move that led to the position decides whether the first move is 同.
            int previous = previousMove;
            int numShow = Math.min(PV_MOVES_SHOWN, pv.size());
            for (int i = 0; i < numShow; i++) {
                int moveCode = Move.fromUSI(copyBoard, pv.get(i));
                if (moveCode == Shogi.MOVE_NONE) {
                    break;
                }
                msg.append(copyBoard.turn() == Shogi.BLACK ? "☗" : "☖");
                msg.append(MoveNotation.describe(copyBoard, moveCode, previous));
                msg.append(" ");
                copyBoard.push(moveCode);
                previous = moveCode;
            }
        } finally {
            // Release the native position right away instead of waiting for GC.
            copyBoard.cleanup();
        }
    }
}
