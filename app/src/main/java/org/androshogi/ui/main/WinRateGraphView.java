package org.androshogi.ui.main;

import org.androshogi.R;

import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;
import org.androshogi.game.PositionAnalysis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.util.Locale;

/**
 * Black's win rate over the moves of a {@link GameRecord}, from the results
 * in a {@link GameAnalysis}. Plies without a result leave a gap in the line.
 * The position on the board is marked, and a tap on the plot asks the
 * listener to jump to the ply under the finger.
 *
  * <p>The grid and the labels follow the theme. The line and its fill use
 * {@code R.color.accent_on_surface}, because the theme accent is a dark tone
 * that all but disappears as a thin line on the dark card.
 * The view reads the record and the analysis the activity owns; call
 * {@link #invalidate()} when either changes.
 */
public class WinRateGraphView extends View {
    /** Told which ply was tapped. */
    public interface OnPlySelectedListener {
        void onPlySelected(int ply);
    }

    /** A short game still gets a readable x axis instead of a few stretched segments. */
    private static final int MIN_PLIES_SHOWN = 40;
    /** Spacing of the labelled ticks along the x axis, in plies. */
    private static final int PLY_TICK_STEP = 20;

    @Nullable
    private GameRecord record;
    @Nullable
    private GameAnalysis analysis;
    private int currentPly;
    @Nullable
    private OnPlySelectedListener listener;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint midlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private final float density;
    /** Room for the labels: percentages on the left, move numbers below. */
    private final float leftInset;
    private final float bottomInset;

    public WinRateGraphView(Context context) {
        this(context, null);
    }

    public WinRateGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        leftInset = 30 * density;
        bottomInset = 16 * density;

        int outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, Color.LTGRAY);
        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY);
        int marker = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, Color.RED);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1 * density);
        gridPaint.setColor(outline);

        midlinePaint.setStyle(Paint.Style.STROKE);
        midlinePaint.setStrokeWidth(1.5f * density);
        midlinePaint.setColor(onSurface);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2 * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(context.getColor(R.color.accent_on_surface));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(context.getColor(R.color.win_rate_fill));

        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(1.5f * density);
        markerPaint.setColor(marker);

        labelPaint.setColor(onSurface);
        labelPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10.0f, getResources().getDisplayMetrics()));
    }

    /** Shows {@code record} with the results in {@code analysis}. */
    public void setData(GameRecord record, GameAnalysis analysis) {
        this.record = record;
        this.analysis = analysis;
        this.currentPly = record.currentPly();
        invalidate();
    }

    public void setCurrentPly(int ply) {
        if (ply != currentPly) {
            currentPly = ply;
            invalidate();
        }
    }

    public void setOnPlySelectedListener(@Nullable OnPlySelectedListener listener) {
        this.listener = listener;
    }

    /** Number of plies the x axis spans. */
    private int pliesShown() {
        int length = record == null ? 0 : record.length();
        return Math.max(MIN_PLIES_SHOWN, length);
    }

    private float plotLeft() { return getPaddingLeft() + leftInset; }
    private float plotRight() { return getWidth() - getPaddingRight() - 4 * density; }
    private float plotTop() { return getPaddingTop() + 6 * density; }
    private float plotBottom() { return getHeight() - getPaddingBottom() - bottomInset; }

    private float xOf(int ply) {
        return plotLeft() + (plotRight() - plotLeft()) * ply / (float) pliesShown();
    }

    private float yOf(double winRate) {
        return plotBottom() - (float) ((plotBottom() - plotTop()) * winRate);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float left = plotLeft();
        float right = plotRight();
        if (right <= left || plotBottom() <= plotTop()) {
            return;
        }

        drawGrid(canvas, left, right);
        if (record != null && analysis != null) {
            drawWinRate(canvas);
            drawCurrentPly(canvas);
        }
    }

    private void drawGrid(Canvas canvas, float left, float right) {
        // Horizontal lines at every 25%, the 50% line stronger; labels on the left.
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        float labelOffset = labelPaint.getTextSize() * 0.35f;
        for (int percent = 0; percent <= 100; percent += 25) {
            float y = yOf(percent / 100.0);
            canvas.drawLine(left, y, right, y, percent == 50 ? midlinePaint : gridPaint);
            canvas.drawText(String.format(Locale.JAPANESE, "%d", percent), left - 4 * density, y + labelOffset, labelPaint);
        }
        // Move numbers along the bottom.
        labelPaint.setTextAlign(Paint.Align.CENTER);
        float labelY = plotBottom() + bottomInset - 3 * density;
        int plies = pliesShown();
        for (int ply = 0; ply <= plies; ply += PLY_TICK_STEP) {
            float x = xOf(ply);
            canvas.drawLine(x, plotTop(), x, plotBottom(), gridPaint);
            canvas.drawText(String.format(Locale.JAPANESE, "%d", ply), x, labelY, labelPaint);
        }
    }

    private void drawWinRate(Canvas canvas) {
        float baseline = yOf(0.5);
        linePath.reset();
        fillPath.reset();
        boolean open = false;
        float lastX = 0;
        int length = record.length();
        for (int ply = 0; ply <= length; ply++) {
            PositionAnalysis a = analysis.get(ply);
            if (a == null || !a.hasScore()) {
                if (open) {
                    closeFill(baseline, lastX);
                    open = false;
                }
                continue;
            }
            float x = xOf(ply);
            float y = yOf(a.winRateForBlack());
            if (!open) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, baseline);
                fillPath.lineTo(x, y);
                open = true;
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
            lastX = x;
        }
        if (open) {
            closeFill(baseline, lastX);
        }
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        // Single analyzed positions have no segment to draw; mark them with a dot.
        for (int ply = 0; ply <= length; ply++) {
            PositionAnalysis a = analysis.get(ply);
            if (a != null && a.hasScore() && isIsolated(ply)) {
                canvas.drawCircle(xOf(ply), yOf(a.winRateForBlack()), 2.5f * density, linePaint);
            }
        }
    }

    private void closeFill(float baseline, float lastX) {
        fillPath.lineTo(lastX, baseline);
        fillPath.close();
    }

    private boolean isIsolated(int ply) {
        return !hasScore(ply - 1) && !hasScore(ply + 1);
    }

    private boolean hasScore(int ply) {
        PositionAnalysis a = analysis == null ? null : analysis.get(ply);
        return a != null && a.hasScore();
    }

    private void drawCurrentPly(Canvas canvas) {
        float x = xOf(currentPly);
        canvas.drawLine(x, plotTop(), x, plotBottom(), markerPaint);
        PositionAnalysis a = analysis.get(currentPly);
        if (a != null && a.hasScore()) {
            canvas.drawCircle(x, yOf(a.winRateForBlack()), 4 * density, markerPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (record == null || listener == null) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float fraction = (event.getX() - plotLeft()) / (plotRight() - plotLeft());
            int ply = Math.round(fraction * pliesShown());
            ply = Math.max(0, Math.min(record.length(), ply));
            performClick();
            listener.onPlySelected(ply);
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
