package org.androshogi.kifu;

import org.androshogi.engine.EngineInfo;
import org.androshogi.game.PositionAnalysis;
import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Formats stored results as ordinary KIF comments, always scored for black. */
public final class KifAnalysisComment {
    private KifAnalysisComment() {}

    /** Only export results belonging to this exact position. Does not modify the board. */
    public static String describe(Board board, PositionAnalysis analysis, int ply, int previousMove) {
        if (analysis == null || !board.getSFEN().equals(analysis.sfen())) return null;
        List<EngineInfo> infos = new ArrayList<>(analysis.infos());
        infos.sort(Comparator.comparingInt(EngineInfo::multipv));
        List<String> variations = new ArrayList<>();
        for (EngineInfo info : infos) {
            StringBuilder pv = new StringBuilder();
            Board copy = board.copy();
            try {
                int previous = previousMove;
                for (String usi : info.pv()) {
                    int move = Move.fromUSI(copy, usi);
                    if (move == Shogi.MOVE_NONE || !copy.isLegal(move)) break;
                    if (pv.length() > 0) pv.append(' ');
                    // These symbols are representable in both Shift_JIS and UTF-8.
                    pv.append(copy.turn() == Shogi.BLACK ? "▲" : "△");
                    pv.append(MoveNotation.describe(copy, move, previous));
                    copy.push(move);
                    previous = move;
                }
            } finally {
                copy.cleanup();
            }
            variations.add(pv.toString());
        }
        return format(analysis.sfen(), ply, infos, variations);
    }

    /** Pure formatting entry point; variations correspond to the supplied info lines. */
    static String format(String sfen, int ply, List<EngineInfo> infos, List<String> variations) {
        if (infos.isEmpty()) return null;
        StringBuilder out = new StringBuilder("[AndroShogi解析] ");
        out.append(ply == 0 ? "開始局面" : ply + "手目後");
        out.append("（評価値は先手基準）");
        boolean white = PositionAnalysis.isWhiteToMove(sfen);
        for (int i = 0; i < infos.size(); i++) {
            EngineInfo info = infos.get(i);
            out.append("\n候補").append(info.multipv()).append("：評価値 ");
            out.append(scoreText(info, white));
            if (info.depth() != EngineInfo.NO_VALUE) {
                out.append(" / 深さ ").append(info.depth());
            }
            if (!variations.get(i).isEmpty()) {
                out.append("\n読み筋").append(info.multipv()).append("：").append(variations.get(i));
            }
        }
        return out.toString();
    }

    private static String scoreText(EngineInfo info, boolean white) {
        if (!info.hasScore()) return "不明";
        long score = (long) info.score() * (white ? -1 : 1);
        String value;
        if (info.isMate()) {
            value = (score < 0 ? "-" : "+") + "詰";
            if (!info.isMateWithUnknownPly()) value += Math.abs(score);
        } else {
            value = String.format(Locale.ROOT, "%+d", score);
        }
        if (info.bound() == EngineInfo.Bound.EXACT) return value;
        boolean lower = info.bound() == EngineInfo.Bound.LOWER;
        if (white) lower = !lower;
        return value + (lower ? "（下限）" : "（上限）");
    }
}
