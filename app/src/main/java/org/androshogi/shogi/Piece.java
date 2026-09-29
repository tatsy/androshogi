package org.androshogi.shogi;

import org.androshogi.R;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

public class Piece {
    private int color;
    private Shogi.PieceType type;

    public Piece(int code) {
        setCode(code);
    }
    public Piece(Shogi.PieceType type, int color) {
        this.type = type;
        this.color = color;
    }

    public void setCode(int code) {
        color = (code >> 4) & 0x1;
        type = Shogi.PieceType.values()[code & 0xf];
    }

    public boolean isBlack() {
        if (isEmpty()) return false;
        return color == 0;
    }

    public boolean isWhite() {
        if (isEmpty()) return false;
        return color != 0;
    }

    public boolean isEmpty() {
        return type == Shogi.PieceType.NONE;
    }

    public Shogi.PieceType type() {
        return type;
    }

    public int color() {
        return color;
    }

    public Drawable getDrawable(Context context) {
        switch (type) {
            case NONE:
                return null;
            case PAWN:
                return AppCompatResources.getDrawable(context, R.drawable.koma_pawn);
            case LANCE:
                return AppCompatResources.getDrawable(context, R.drawable.koma_lance);
            case KNIGHT:
                return AppCompatResources.getDrawable(context, R.drawable.koma_knight);
            case SILVER:
                return AppCompatResources.getDrawable(context, R.drawable.koma_silver);
            case GOLD:
                return AppCompatResources.getDrawable(context, R.drawable.koma_gold);
            case BISHOP:
                return AppCompatResources.getDrawable(context, R.drawable.koma_bishop);
            case ROOK:
                return AppCompatResources.getDrawable(context, R.drawable.koma_rook);
            case KING:
                return AppCompatResources.getDrawable(context, R.drawable.koma_king);
            case PROM_PAWN:
                return AppCompatResources.getDrawable(context, R.drawable.koma_prom_pawn);
            case PROM_LANCE:
                return AppCompatResources.getDrawable(context, R.drawable.koma_prom_lance);
            case PROM_KNIGHT:
                return AppCompatResources.getDrawable(context, R.drawable.koma_prom_knight);
            case PROM_SILVER:
                return AppCompatResources.getDrawable(context, R.drawable.koma_prom_silver);
            case PROM_BISHOP:
                return AppCompatResources.getDrawable(context, R.drawable.koma_prom_bishop);
            case PROM_ROOK:
                return AppCompatResources.getDrawable(context, R.drawable.koma_prom_rook);
            default:
                return null;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return (isBlack() ? "B_" : "W_") + type.toString();
    }
}
