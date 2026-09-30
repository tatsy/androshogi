package org.androshogi.ui.main;

import org.androshogi.shogi.Move;
import org.androshogi.R;

import org.androshogi.kifu.MoveNotation;

import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;
import org.androshogi.game.PositionAnalysis;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The move list: one row for the starting position and one per move of the
 * {@link GameRecord}, each with black's win rate and score for the position
 * after it when {@link GameAnalysis} has one. Row {@code ply} is the position
 * after {@code ply} moves, so rows and analysis entries share the index.
 *
 * <p>The adapter reads the record and the analysis that the activity owns;
 * it must be told when either changes.
 */
public class MoveListAdapter extends RecyclerView.Adapter<MoveListAdapter.RowHolder> {

    /** Told which ply a tapped row stands for. */
    public interface OnPlySelectedListener {
        void onPlySelected(int ply);
    }

    private final GameAnalysis analysis;
    private final OnPlySelectedListener listener;
    @Nullable
    private GameRecord record;
    private boolean whiteMovesFirst;
    private int currentPly;
    /**
     * Notation of every move, computed once per record change because the
     * marks (同, 右, 不成...) need the position before each move.
     */
    private List<String> notations = new ArrayList<>();

    public MoveListAdapter(GameAnalysis analysis, OnPlySelectedListener listener) {
        this.analysis = analysis;
        this.listener = listener;
    }

    /** Shows another record; the whole list is rebuilt. */
    public void setRecord(GameRecord record) {
        this.record = record;
        this.whiteMovesFirst = PositionAnalysis.isWhiteToMove(record.startSfen());
        this.currentPly = record.currentPly();
        this.notations = MoveNotation.describeRecord(record);
        notifyDataSetChanged();
    }

    /** Moves the highlight to {@code ply}. */
    public void setCurrentPly(int ply) {
        if (ply == currentPly) {
            return;
        }
        int previous = currentPly;
        currentPly = ply;
        notifyRow(previous);
        notifyRow(ply);
    }

    /** The selected route changed; rebuild its labels and row count. */
    public void recordChanged() {
        if (record != null) {
            currentPly = record.currentPly();
            notations = MoveNotation.describeRecord(record);
        }
        notifyDataSetChanged();
    }

    /** Redraws every row under new display settings; the record itself is unchanged. */
    public void refreshLabels() {
        notifyDataSetChanged();
    }

    /** The analysis for {@code ply} was stored or dropped. */
    public void analysisChanged(int ply) {
        notifyRow(ply);
    }

    private void notifyRow(int ply) {
        if (ply >= 0 && ply < getItemCount()) {
            notifyItemChanged(ply);
        }
    }

    @Override
    public int getItemCount() {
        return record == null ? 0 : record.length() + 1;
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_move, parent, false);
        return new RowHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        int ply = position;
        if (ply == 0) {
            holder.number.setText("");
            holder.text.setText(R.string.move_list_start);
        } else {
            holder.number.setText(String.format(Locale.JAPANESE, "%d", ply));
            holder.text.setText(describeMove(ply - 1));
        }
        if (record.node(record.nodeIdAtPly(ply)).childIds().size() > 1) {
            holder.text.append(holder.itemView.getContext().getString(R.string.branch_point_marker));
        }
        PositionAnalysis a = analysis.get(ply);
        holder.winRate.setText(EvaluationLabel.winRate(a));
        holder.score.setText(EvaluationLabel.score(a));
        holder.itemView.setSelected(ply == currentPly);
        holder.itemView.setOnClickListener(v -> listener.onPlySelected(ply));
    }

    /** "☗７六歩", "☖同歩", "☗５八金右" for the move at {@code index} of the record. */
    private String describeMove(int index) {
        boolean white = whiteMovesFirst != (index % 2 == 1);
        String text = index < notations.size() ? notations.get(index) : new Move(record.move(index)).toString();
        return (white ? "☖" : "☗") + text;
    }

    static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView text;
        final TextView winRate;
        final TextView score;

        RowHolder(View itemView) {
            super(itemView);
            number = itemView.findViewById(R.id.move_number);
            text = itemView.findViewById(R.id.move_text);
            winRate = itemView.findViewById(R.id.move_win_rate);
            score = itemView.findViewById(R.id.move_score);
        }
    }
}
