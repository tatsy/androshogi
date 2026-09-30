package org.androshogi.ui.main;

import org.androshogi.shogi.Board;
import org.androshogi.shogi.LegalMoveList;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Piece;
import org.androshogi.shogi.Shogi;
import org.androshogi.R;

import org.androshogi.engine.EngineInfo;
import org.androshogi.engine.EngineUpdateListener;

import org.androshogi.game.GameRecord;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BoardView extends View implements EngineUpdateListener {
    static final String TAG = "BoardView";

    // Shogi parameters
    static private final int BOARD_SIZE = 9;
    static private final int N_HAND_PIECES = 7;
    /** Stroke width of the arrow showing the next move of the game record. */
    static private final float NEXT_MOVE_ARROW_WIDTH = 22.0f;
    /** Distance, in pixels on screen, by which a piece's shadow falls to the lower right. */
    static private final int SHADOW_OFFSET = 5;
    private Board board;
    private boolean upsideDown = false;
    private Piece[][] pieces;
    private Piece[] blackHandPieces;
    private Piece[] whiteHandPieces;
    /**
     * The game record shown on the board. {@link #board} always holds the
     * position at {@link GameRecord#currentPly()}; every navigation method
     * moves both together.
     */
    private GameRecord record;

    // Selection parameters
    private int selectedRow = -1;
    private int selectedColumn = -1;
    private Piece selectedPiece = null;
    /** Legal moves for the current selection, copied out of the native list once. */
    private final List<Integer> selectedLegalMoves = new ArrayList<>();

    // Suggested moves
    private List<Move> suggestedMoves;

    // Position change notification and input locking (used by the analysis loop)
    private OnPositionChangedListener positionListener;
    private boolean inputLocked = false;

    // Layout parameters
    private int boardMargin;
    private Rect boardRect;
    private Rect blackHandRect;
    private Rect whiteHandRect;
    private float cellSize;

    // Graphics parameters
    private Paint fillPaint;
    private Paint strokePaint;

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BoardView(Context context) {
        super(context);
        init();
    }

    private void init() {
        // Game states
        board = new Board();
        pieces = new Piece[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                pieces[i][j] = new Piece(0);
            }
        }

        blackHandPieces = new Piece[N_HAND_PIECES];
        whiteHandPieces = new Piece[N_HAND_PIECES];
        for (int i = 0; i < N_HAND_PIECES; i++) {
            blackHandPieces[i] = new Piece(Shogi.PieceType.fromHandPieceIndex(i), 0);
            whiteHandPieces[i] = new Piece(Shogi.PieceType.fromHandPieceIndex(i), 1);
        }

        record = new GameRecord(Shogi.STARTING_SFEN, "", "");
        suggestedMoves = new ArrayList<>();

        // Graphics
        fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint = new Paint();
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        float baseSize = Math.min(width, height);
        float margin = baseSize * 0.03f;
        float boardSize = baseSize - margin * 2;
        cellSize = boardSize / (float)BOARD_SIZE;

        whiteHandRect = new Rect((int)margin, 0, (int)(boardSize + margin), (int)cellSize);
        boardRect = new Rect((int)margin, (int)(cellSize + margin), (int)(boardSize + margin), (int)(cellSize + boardSize + margin));
        blackHandRect = new Rect((int)margin, (int)(cellSize + boardSize + margin * 2), (int)(boardSize + margin), (int)(cellSize * 2 + boardSize + margin * 2));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int originalWidth = MeasureSpec.getSize(widthMeasureSpec);
        int calculatedHeight = (int)(originalWidth * 1.21f);
        int finalWidthSpec = MeasureSpec.makeMeasureSpec(originalWidth, MeasureSpec.EXACTLY);
        int finalHeightSpec = MeasureSpec.makeMeasureSpec(calculatedHeight, MeasureSpec.EXACTLY);
        super.onMeasure(finalWidthSpec, finalHeightSpec);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // 上下が反転していたらキャンバスを回転してから描画する
        if (upsideDown) {
            canvas.save();
            canvas.rotate(180, getWidth() * 0.5f, getHeight() * 0.5f);
        }

        // 背景：白番の持ち駒
        fillPaint.setColor(getResources().getColor(R.color.board_background, getContext().getTheme()));
        canvas.drawRect(whiteHandRect, fillPaint);

        // 将棋盤の木目
        BitmapDrawable background = (BitmapDrawable) AppCompatResources.getDrawable(getContext(), R.drawable.wood);
        assert background != null;
        background.setBounds(0, whiteHandRect.bottom, getWidth(), blackHandRect.top);
        background.draw(canvas);

        // 背景：黒番の持ち駒
        fillPaint.setColor(getResources().getColor(R.color.board_background, getContext().getTheme()));
        canvas.drawRect(blackHandRect, fillPaint);

        // 将棋盤の枠
        strokePaint.setColor(Color.BLACK);
        strokePaint.setStrokeWidth(4);
        canvas.drawRect(boardRect, strokePaint);
        strokePaint.setStrokeWidth(2);
        canvas.drawRect(1, whiteHandRect.bottom + 1, getWidth() - 1, blackHandRect.top - 1, strokePaint);
        canvas.drawRect(1, whiteHandRect.top + 1, getWidth() - 1, whiteHandRect.bottom - 1, strokePaint);
        canvas.drawRect(1, blackHandRect.top + 1, getWidth() - 1, blackHandRect.bottom - 1, strokePaint);
        
        // Highlight cell of the last move destination
        if (board.peek() != 0) {
            int dest = Move.dest(board.peek());
            int column = BOARD_SIZE - dest / BOARD_SIZE - 1;
            int row = dest % BOARD_SIZE;
            float left = boardRect.left + column * cellSize;
            float right = boardRect.left + (column + 1) * cellSize;
            float top = boardRect.top + row * cellSize;
            float bottom = boardRect.top + (row + 1) * cellSize;

            fillPaint.setColor(getResources().getColor(R.color.cell_last_move, getContext().getTheme()));
            canvas.drawRect(left, top, right, bottom, fillPaint);
        }

        // 選択された駒をハイライトする
        if (selectedPiece != null) {
            int borderWidth = 12;
            strokePaint.setStrokeWidth(12);
            strokePaint.setColor(getResources().getColor(R.color.cell_selected_piece, getContext().getTheme()));
            if (selectedRow >= 0 && selectedRow < BOARD_SIZE) {
                float left = boardRect.left + selectedColumn * cellSize + borderWidth * 0.5f;
                float right = boardRect.left + (selectedColumn + 1) * cellSize - borderWidth * 0.5f;
                float top = boardRect.top + selectedRow * cellSize + borderWidth * 0.5f;
                float bottom = boardRect.top + (selectedRow + 1) * cellSize - borderWidth * 0.5f;
                canvas.drawRect(left, top, right, bottom, strokePaint);
            } else if (selectedRow >= BOARD_SIZE) {
                if (selectedRow - BOARD_SIZE == 0) {
                    // 先手番の持ち駒
                    float left = blackHandRect.left + selectedColumn * cellSize;
                    float right = blackHandRect.left + (selectedColumn + 1) * cellSize;
                    float top = blackHandRect.top;
                    float bottom = blackHandRect.bottom;
                    if (upsideDown) {
                        float t = top;
                        top = getHeight() - bottom;
                        bottom = getHeight() - t;
                        float l = left;
                        left = getWidth() - right;
                        right = getWidth() - l;
                    }
                    canvas.drawRect(left, top, right, bottom, strokePaint);
                } else {
                    // 後手番の持ち駒
                    float left = whiteHandRect.left + selectedColumn * cellSize;
                    float right = whiteHandRect.left + (selectedColumn + 1) * cellSize;
                    float top = whiteHandRect.top;
                    float bottom = whiteHandRect.bottom;
                    if (upsideDown) {
                        float t = top;
                        top = getHeight() - bottom;
                        bottom = getHeight() - t;
                        float l = left;
                        left = getWidth() - right;
                        right = getWidth() - l;
                    }
                    canvas.drawRect(left, top, right, bottom, strokePaint);
                }
            }
        }

        // Draw grid lines
        strokePaint.setColor(Color.BLACK);
        strokePaint.setStrokeWidth(2);
        for (int i = 0; i <= BOARD_SIZE; i++) {
            // Vertical lines
            canvas.drawLine(boardRect.left + i * cellSize, boardRect.top, boardRect.left + i * cellSize, boardRect.bottom, strokePaint);

            // Horizontal lines
            canvas.drawLine(boardRect.left, boardRect.top + i * cellSize, boardRect.right, boardRect.top + i * cellSize, strokePaint);
        }

        // Draw dots
        fillPaint.setColor(Color.BLACK);
        canvas.drawCircle(boardRect.left + 3 * cellSize, boardRect.top + 3 * cellSize, 6, fillPaint);
        canvas.drawCircle(boardRect.left + 3 * cellSize, boardRect.top + 6 * cellSize, 6, fillPaint);
        canvas.drawCircle(boardRect.left + 6 * cellSize, boardRect.top + 3 * cellSize, 6, fillPaint);
        canvas.drawCircle(boardRect.left + 6 * cellSize, boardRect.top + 6 * cellSize, 6, fillPaint);

        // 盤上の駒の描画
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                int square = (BOARD_SIZE - j - 1) * BOARD_SIZE + i;
                pieces[i][j].setCode(board.piece(square));

                float left = boardRect.left + j * cellSize;
                float right = boardRect.left + (j + 1) * cellSize;
                float top = boardRect.top + i * cellSize;
                float bottom = boardRect.top + (i + 1) * cellSize;

                if (!pieces[i][j].isEmpty()) {
                    BitmapDrawable bitmap = (BitmapDrawable)pieces[i][j].getDrawable(getContext());

                    // The shadow has to fall to the lower right on screen. The
                    // canvas is rotated 180 degrees for white's pieces and once
                    // more when the board is upside down, and each rotation
                    // flips the direction of the offset.
                    boolean rotated = pieces[i][j].isWhite() != upsideDown;
                    int shadowOffset = rotated ? -SHADOW_OFFSET : SHADOW_OFFSET;
                    if (pieces[i][j].isWhite()) {
                        canvas.save();
                        canvas.rotate(180, left + cellSize / 2, top + cellSize / 2);
                        drawPiece(bitmap, canvas, (int) left, (int) top, (int) right, (int) bottom, shadowOffset);
                        canvas.restore();
                    } else {
                        drawPiece(bitmap, canvas, (int) left, (int) top, (int) right, (int) bottom, shadowOffset);
                    }
                }
            }
        }

        // 持ち駒の描画
        int[] blackHandCounts = board.piecesInHand(0);
        for (int i = 0; i < blackHandCounts.length; i++) {
            if (blackHandCounts[i] != 0) {
                Rect targetRect = blackHandRect;
                float left = targetRect.left + i * cellSize;
                float right = targetRect.left + (i + 1) * cellSize;
                float top = targetRect.top;
                float bottom = targetRect.bottom;
                if (upsideDown) {
                    float t = right;
                    right = getWidth() - left;
                    left = getWidth() - t;
                    canvas.save();
                    canvas.rotate(180, left + cellSize * 0.5f, top + cellSize * 0.5f);
                }

                // Hand pieces are upright on screen in both orientations (the
                // rotation above cancels the board's), so the offset keeps its sign.
                BitmapDrawable bitmap = (BitmapDrawable)blackHandPieces[i].getDrawable(getContext());
                drawPiece(bitmap, canvas, (int)left, (int)top, (int)right, (int)bottom, SHADOW_OFFSET);

                // Draw small circle
                float r = cellSize * 0.125f;
                fillPaint.setColor(getResources().getColor(R.color.circle_n_hand_pieces, getContext().getTheme()));
                canvas.drawCircle(right - r * 2, bottom - r * 2, r, fillPaint);

                // Draw piece count
                fillPaint.setColor(Color.WHITE);
                fillPaint.setTextSize(cellSize * 0.2f);
                fillPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(Integer.toString(blackHandCounts[i]), right - r * 2, bottom - r * 1.5f, fillPaint);

                if (upsideDown) {
                    canvas.restore();
                }
            }
        }

        int[] whiteHandCounts = board.piecesInHand(1);
        for (int i = 0; i < whiteHandCounts.length; i++) {
            if (whiteHandCounts[i] != 0) {
                Rect targetRect = whiteHandRect;
                float left = targetRect.left + i * cellSize;
                float right = targetRect.left + (i + 1) * cellSize;
                float top = targetRect.top;
                float bottom = targetRect.bottom;
                if (upsideDown) {
                    float t = right;
                    right = getWidth() - left;
                    left = getWidth() - t;
                    canvas.save();
                    canvas.rotate(180, left + cellSize * 0.5f, top + cellSize * 0.5f);
                }

                BitmapDrawable bitmap = (BitmapDrawable) whiteHandPieces[i].getDrawable(getContext());
                drawPiece(bitmap, canvas, (int) left, (int) top, (int) right, (int) bottom, SHADOW_OFFSET);

                // Draw small circle
                float r = cellSize * 0.125f;
                fillPaint.setColor(getResources().getColor(R.color.circle_n_hand_pieces, getContext().getTheme()));
                canvas.drawCircle(right - r * 2, bottom - r * 2, r, fillPaint);

                // Draw piece count
                fillPaint.setColor(Color.WHITE);
                fillPaint.setTextSize(cellSize * 0.2f);
                fillPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(Integer.toString(whiteHandCounts[i]), right - r * 2, bottom - r * 1.5f, fillPaint);

                if (upsideDown) {
                    canvas.restore();
                }
            }
        }

        // Highlight destination of the legal moves cached when the piece was selected.
        if (selectedPiece != null) {
            fillPaint.setColor(getResources().getColor(R.color.cell_legal_moves, getContext().getTheme()));
            for (int moveCode : selectedLegalMoves) {
                int destColumn = BOARD_SIZE - (Move.dest(moveCode) / BOARD_SIZE) - 1;
                int destRow = Move.dest(moveCode) % BOARD_SIZE;
                float cx = boardRect.left + (destColumn + 0.5f) * cellSize;
                float cy = boardRect.top + (destRow + 0.5f) * cellSize;
                canvas.drawCircle(cx, cy, cellSize * 0.125f, fillPaint);
            }
        }

        // 読み筋の矢印を表示
        if (!suggestedMoves.isEmpty()) {
            int nSuggestion = suggestedMoves.size();
            for (int i = 0; i < nSuggestion; i++) {
                float alpha = 0.2f + 0.8f * ((float)(nSuggestion - i - 1) / nSuggestion);
                drawMoveArrow(canvas, suggestedMoves.get(i),
                        Color.argb(alpha, 1.0f, 0.0f, 0.0f), 30 - i * 4);
            }
        }

        // 棋譜の次の一手。読み筋より後に描くので、最善手と一致していても見える
        int nextMove = nextRecordMove();
        if (nextMove != Shogi.MOVE_NONE) {
            drawMoveArrow(canvas, new Move(nextMove),
                    getResources().getColor(R.color.arrow_next_move, getContext().getTheme()),
                    NEXT_MOVE_ARROW_WIDTH);
        }

        if (upsideDown) {
            canvas.restore();
        }

        // Draw row and column numbers
        fillPaint.setColor(Color.BLACK);
        fillPaint.setTextSize(cellSize * 0.2f);
        fillPaint.setTextAlign(Paint.Align.CENTER);
        for (int j = 0; j < BOARD_SIZE; j++) {
            float x = boardRect.left + cellSize * (j + 0.5f);
            float y = boardRect.top - cellSize * 0.1f;
            if (upsideDown) {
                x = getWidth() - x;
            }
            canvas.drawText(Integer.toString(BOARD_SIZE - j), x, y, fillPaint);
        }
        for (int i = 0; i < BOARD_SIZE; i++) {
            float x = boardRect.right + cellSize * 0.15f;
            float y = boardRect.top + cellSize * (i + 0.5f);
            if (upsideDown) {
                y = getHeight() - y;
            }
            canvas.drawText("一二三四五六七八九".substring(i, i + 1), x, y, fillPaint);
        }
    }

    /**
     * Draws one move as an arrow, from its origin square (or from the mover's
     * hand stand for a drop) to its destination.
     *
     * <p>Must be called from {@link #onDraw}, while the canvas still carries
     * the rotation applied for a flipped board.
     */
    private void drawMoveArrow(Canvas canvas, Move move, int color, float strokeWidth) {
        strokePaint.setStrokeWidth(strokeWidth);
        strokePaint.setColor(color);

        float startX, startY;
        if (move.isDrop()) {
            Rect targetRect = board.turn() == Shogi.BLACK ? blackHandRect : whiteHandRect;
            int handPieceIndex = move.piece().toHandPieceIndex();
            startX = targetRect.left + (handPieceIndex + 0.5f) * cellSize;
            startY = targetRect.top + 0.5f * cellSize;
            if (upsideDown) {
                startX = getWidth() - startX;
            }
        } else {
            Shogi.Square fromSq = Shogi.Square.values()[move.source()];
            startX = boardRect.left + ((BOARD_SIZE - fromSq.column() - 1) + 0.5f) * cellSize;
            startY = boardRect.top + (fromSq.row() + 0.5f) * cellSize;
        }

        Shogi.Square toSq = Shogi.Square.values()[move.dest()];
        float endX = boardRect.left + ((BOARD_SIZE - toSq.column() - 1) + 0.5f) * cellSize;
        float endY = boardRect.top + (toSq.row() + 0.5f) * cellSize;
        float arrowLength = (float)Math.hypot(endX - startX, endY - startY);
        if (arrowLength <= 0.0f) {
            // Would divide by zero below; nothing sensible to draw anyway.
            return;
        }
        float ux = (endX - startX) / arrowLength;
        float uy = (endY - startY) / arrowLength;

        // Stop the shaft short of the head so the two do not overlap.
        canvas.drawLine(startX, startY,
                startX + ux * (arrowLength - cellSize * 0.2f),
                startY + uy * (arrowLength - cellSize * 0.2f), strokePaint);

        fillPaint.setColor(color);
        Path triangle = new Path();
        for (int j = 0; j < 3; j++) {
            double theta = 2.0 * Math.PI * j / 3.0;
            double cosTheta = Math.cos(theta) * cellSize * 0.4;
            double sinTheta = Math.sin(theta) * cellSize * 0.4;
            double px = ux * cosTheta - uy * sinTheta + endX;
            double py = ux * sinTheta + uy * cosTheta + endY;
            if (j == 0) {
                triangle.moveTo((float) px, (float) py);
            } else {
                triangle.lineTo((float) px, (float) py);
            }
        }
        triangle.close();
        canvas.drawPath(triangle, fillPaint);
    }

    private void drawPiece(BitmapDrawable bitmap, Canvas canvas, int left, int top, int right, int bottom, int shadowOffset) {
        BitmapDrawable shadow = (BitmapDrawable) AppCompatResources.getDrawable(getContext(), R.drawable.koma_shadow);
        assert shadow != null;

        int sLeft = left + shadowOffset;
        int sTop = top + shadowOffset;
        int sRight = right + shadowOffset;
        int sBottom = bottom + shadowOffset;
        shadow.setBounds(sLeft, sTop, sRight, sBottom);
        shadow.draw(canvas);
        bitmap.setBounds(left, top, right, bottom);
        bitmap.draw(canvas);
    }

    /** Plays a user move, keeping other continuations and their analysis as branches. */
    public void commitMove(int move) {
        boolean routeChanged = record.isAtEnd() || record.nextMove() != move;
        record.playVariation(move);
        board.push(move);
        afterPositionChanged();
        if (routeChanged) notifyRecordChanged();
        notifyPositionChanged();
    }

    public boolean forwardBoard() {
        int move = record.nextMove();
        if (move != Shogi.MOVE_NONE) {
            record.forward();
            board.push(move);
            afterPositionChanged();
            notifyPositionChanged();
            return true;
        } else {
            this.post(() -> {
                Toast.makeText(getContext(), "これが最後の手です", Toast.LENGTH_SHORT).show();
            });
            return false;
        }
    }

    public boolean backwardBoard() {
        if (record.backward()) {
            board.pop();
            afterPositionChanged();
            notifyPositionChanged();
            return true;
        } else {
            this.post(() -> {
                Toast.makeText(getContext(), "これ以上戻せません", Toast.LENGTH_SHORT).show();
            });
            return false;
        }
    }

    /** Drops the selection and the arrows, which belonged to the previous position. */
    private void afterPositionChanged() {
        clearSelection();
        suggestedMoves.clear();
        invalidate();
    }

    public void flipUpsideDown() {
        upsideDown = !upsideDown;
        invalidate();
    }

    public boolean isUpsideDown() {
        return upsideDown;
    }

    /**
     * Shows {@code record}: the board is set to its starting position and the
     * moves up to its current ply are replayed. The view keeps the reference,
     * so navigating the board moves the record's current ply as well. Any
     * selection and candidate arrows are discarded.
     */
    public void setRecord(@NonNull GameRecord record) {
        this.record = record;
        board.setSFEN(record.startSfen());
        for (int i = 0; i < record.currentPly(); i++) {
            board.push(record.move(i));
        }
        afterPositionChanged();
        notifyPositionChanged();
    }

    /** The record shown on the board. Navigate through this view, not the record. */
    @NonNull
    public GameRecord getRecord() {
        return record;
    }

    /**
     * Moves to {@code ply} of the record (clamped), taking back or replaying
     * only the moves in between. The listener is notified once.
     */
    public void seekTo(int ply) {
        int target = Math.max(0, Math.min(record.length(), ply));
        while (record.currentPly() > target) {
            record.backward();
            board.pop();
        }
        while (record.currentPly() < target) {
            board.push(record.nextMove());
            record.forward();
        }
        afterPositionChanged();
        notifyPositionChanged();
    }

    private void clearSelection() {
        selectedRow = -1;
        selectedColumn = -1;
        selectedPiece = null;
        selectedLegalMoves.clear();
    }

    /** Selects one piece and snapshots only the legal moves that can start from it. */
    private void selectPiece(Piece piece, int row, int column) {
        selectedPiece = piece;
        selectedRow = row;
        selectedColumn = column;
        selectedLegalMoves.clear();

        LegalMoveList legalMoves = board.legalMoves();
        try {
            if (row < BOARD_SIZE) {
                int source = (BOARD_SIZE - column - 1) * BOARD_SIZE + row;
                for (int moveCode : legalMoves) {
                    if (!Move.isDrop(moveCode) && Move.source(moveCode) == source) {
                        selectedLegalMoves.add(moveCode);
                    }
                }
            } else {
                for (int moveCode : legalMoves) {
                    if (Move.isDrop(moveCode)
                            && Move.piece(moveCode) == piece.type().ordinal()) {
                        selectedLegalMoves.add(moveCode);
                    }
                }
            }
        } finally {
            legalMoves.cleanup();
        }
    }

    public String getSFEN() {
        return board.getSFEN();
    }

    /**
     * The move {@link #forwardBoard()} would play, or {@link Shogi#MOVE_NONE}
     * at the end of the record.
     *
     * <p>Cheap enough for the draw path: {@link #commitMove} keeps the record
     * a continuation of the position, so nothing has to be validated here.
     */
    public int nextRecordMove() {
        return record.nextMove();
    }

    public void setStartBoard() {
        seekTo(0);
    }

    public void setEndBoard() {
        seekTo(record.length());
    }

    /**
     * Notified on the main thread whenever the displayed position changes:
     * a move is played or taken back, or a game record is shown.
     */
    public interface OnPositionChangedListener {
        void onPositionChanged();

        /** Called before the position notification when the selected route changes. */
        default void onRecordChanged() {}
    }

    public void setOnPositionChangedListener(OnPositionChangedListener listener) {
        this.positionListener = listener;
    }

    private void notifyPositionChanged() {
        if (positionListener != null) {
            positionListener.onPositionChanged();
        }
    }

    private void notifyRecordChanged() {
        if (positionListener != null) {
            positionListener.onRecordChanged();
        }
    }

    /**
     * While locked, touches on the board are swallowed. The analysis loop
     * drives the position itself and must not have the user move pieces
     * underneath it.
     */
    public void setInputLocked(boolean locked) {
        this.inputLocked = locked;
        if (locked) {
            clearSelection();
            invalidate();
        }
    }

    public boolean isInputLocked() {
        return inputLocked;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (inputLocked) {
            return true;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                float x = event.getX();
                float y = event.getY();

                // Check touch for board pieces
                if (boardRect.contains((int)x, (int)y)) {
                    int column = (int)((x - boardRect.left) / cellSize);
                    int row = (int)((y - boardRect.top) / cellSize);
                    if (upsideDown) {
                        column = BOARD_SIZE - column - 1;
                        row = BOARD_SIZE - row - 1;
                    }
                    handleTouch(row, column);
                }

                // Check touch for hand pieces
                if (!upsideDown) {
                    if (board.turn() == 1 && whiteHandRect.contains((int) x, (int) y)) {
                        int whiteHandIndex = (int) ((x - whiteHandRect.left) / cellSize);
                        if (whiteHandIndex >= 0 && whiteHandIndex < N_HAND_PIECES) {
                            int[] handPieces = board.piecesInHand(1);
                            if (handPieces[whiteHandIndex] > 0) {
                                handleTouch(BOARD_SIZE + 1, whiteHandIndex);
                            }
                        }
                    }

                    if (board.turn() == 0 && blackHandRect.contains((int) x, (int) y)) {
                        int blackHandIndex = (int) ((x - blackHandRect.left) / cellSize);
                        if (blackHandIndex >= 0 && blackHandIndex < N_HAND_PIECES) {
                            int[] handPieces = board.piecesInHand(0);
                            if (handPieces[blackHandIndex] > 0) {
                                handleTouch(BOARD_SIZE, blackHandIndex);
                            }
                        }
                    }
                } else {
                    if (board.turn() == 0 && whiteHandRect.contains((int) x, (int) y)) {
                        int whiteHandIndex = (int) ((x - whiteHandRect.left) / cellSize);
                        if (whiteHandIndex >= 0 && whiteHandIndex < N_HAND_PIECES) {
                            int[] handPieces = board.piecesInHand(0);
                            if (handPieces[whiteHandIndex] > 0) {
                                handleTouch(BOARD_SIZE + 1, whiteHandIndex);
                            }
                        }
                    }

                    if (board.turn() == 1 && blackHandRect.contains((int) x, (int) y)) {
                        int blackHandIndex = (int) ((x - blackHandRect.left) / cellSize);
                        if (blackHandIndex >= 0 && blackHandIndex < N_HAND_PIECES) {
                            int[] handPieces = board.piecesInHand(1);
                            if (handPieces[blackHandIndex] > 0) {
                                handleTouch(BOARD_SIZE, blackHandIndex);
                            }
                        }
                    }
                }
                return true;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    private void handleTouch(int row, int column) {
        Piece piece;
        if (row < BOARD_SIZE) {
            int square = (BOARD_SIZE - column - 1) * BOARD_SIZE + row;
            piece = new Piece(board.piece(square));
        } else {
            Shogi.PieceType pieceType = Shogi.PieceType.fromHandPieceIndex(column);
            piece = new Piece(pieceType, board.turn());
        }

        // A move can only end on the board. Tapping the piece stand only changes selection.
        if (selectedPiece != null && row < BOARD_SIZE) {
            int dest = (BOARD_SIZE - column - 1) * BOARD_SIZE + row;

            if (selectedRow < BOARD_SIZE) {
                int promotedMove = Shogi.MOVE_NONE;
                int unpromotedMove = Shogi.MOVE_NONE;
                for (int moveCode : selectedLegalMoves) {
                    if (Move.dest(moveCode) != dest) {
                        continue;
                    }
                    if (Move.isProm(moveCode)) {
                        promotedMove = moveCode;
                    } else {
                        unpromotedMove = moveCode;
                    }
                }

                if (promotedMove != Shogi.MOVE_NONE && unpromotedMove != Shogi.MOVE_NONE) {
                    final int prom = promotedMove;
                    final int noProm = unpromotedMove;
                    showPromotionDialog(getContext(),
                            isPromoted -> commitMove(isPromoted ? prom : noProm));
                    return;
                } else if (promotedMove != Shogi.MOVE_NONE) {
                    commitMove(promotedMove);
                    return;
                } else if (unpromotedMove != Shogi.MOVE_NONE) {
                    commitMove(unpromotedMove);
                    return;
                }
            } else {
                for (int moveCode : selectedLegalMoves) {
                    if (Move.dest(moveCode) == dest) {
                        commitMove(moveCode);
                        return;
                    }
                }
            }
        }

        if (!piece.isEmpty() && piece.color() == board.turn()
                && (row != selectedRow || column != selectedColumn)) {
            selectPiece(piece, row, column);
        } else {
            clearSelection();
        }
        invalidate();
    }

    @Override
    public void onEngineUpdated(Board board, List<EngineInfo> infos) {
        suggestedMoves.clear();
        for (EngineInfo info : infos) {
            if (!info.hasPv()) {
                continue;
            }
            int moveCode = Move.fromUSI(board, info.pv().get(0));
            if (moveCode != Shogi.MOVE_NONE) {
                suggestedMoves.add(new Move(moveCode));
            }
        }
        invalidate();
    }

    private void showPromotionDialog(Context context, PromotionCallback callback) {
        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Androshogi_AlertDialog);
        builder.setTitle("成り・不成");
        builder.setMessage("成りますか？");

        // 成りのボタン (左側に表示)
        builder.setNegativeButton(R.string.prom_label, (DialogInterface dialog, int which) -> {
                callback.onPromotionChoice(true); // Promote
                dialog.dismiss();
        });

        // 不成のボタン (右側に表示)
        builder.setPositiveButton(R.string.not_prom_label, (DialogInterface dialog, int which) -> {
                callback.onPromotionChoice(false); // Don't Promote
                dialog.dismiss();
        });

        builder.show();
    }
}

interface PromotionCallback {
    void onPromotionChoice(boolean isPromoted);

}
