package org.androshogi.kifu;

import org.androshogi.shogi.Shogi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The notation rule on hand-made situations. Squares are given in USI form
 * ("5h" is ５八); the alternatives are the source squares of the other pieces
 * of the same kind that could reach the destination.
 */
public class MoveNotationTest {
    private static final List<Integer> NONE = Collections.emptyList();

    /** USI square such as "5h" to the board index used by the app (file * 9 + rank). */
    private static int sq(String usi) {
        int column = usi.charAt(0) - '1';
        int row = usi.charAt(1) - 'a';
        return column * 9 + row;
    }

    private static List<Integer> others(String... squares) {
        Integer[] indices = new Integer[squares.length];
        for (int i = 0; i < squares.length; i++) {
            indices[i] = sq(squares[i]);
        }
        return Arrays.asList(indices);
    }

    private static String black(Shogi.PieceType piece, String from, String to, List<Integer> others) {
        return MoveNotation.format(piece, sq(from), sq(to), false, Shogi.BLACK, MoveNotation.NO_SQUARE, others);
    }

    private static String white(Shogi.PieceType piece, String from, String to, List<Integer> others) {
        return MoveNotation.format(piece, sq(from), sq(to), false, Shogi.WHITE, MoveNotation.NO_SQUARE, others);
    }

    @Test
    public void plainMoveIsSquareAndPiece() {
        assertEquals("５八金", black(Shogi.PieceType.GOLD, "5i", "5h", NONE));
        assertEquals("７六歩", black(Shogi.PieceType.PAWN, "7g", "7f", NONE));
    }

    @Test
    public void sameSquareAsThePreviousMoveIsDou() {
        String text = MoveNotation.format(Shogi.PieceType.PAWN, sq("2c"), sq("2d"), false,
                Shogi.WHITE, sq("2d"), NONE);
        assertEquals("同歩", text);
        String capture = MoveNotation.format(Shogi.PieceType.SILVER, sq("3c"), sq("2d"), false,
                Shogi.WHITE, sq("2d"), NONE);
        assertEquals("同銀", capture);
    }

    @Test
    public void dropsAreMarkedUchi() {
        assertEquals("５五角打", MoveNotation.format(Shogi.PieceType.BISHOP, MoveNotation.NO_SQUARE, sq("5e"),
                false, Shogi.BLACK, MoveNotation.NO_SQUARE, NONE));
    }

    @Test
    public void twoGoldsMovingUpAreToldApartByRightAndLeft() {
        // 金 at ４九 and ６九 both go up to ５八.
        assertEquals("５八金右", black(Shogi.PieceType.GOLD, "4i", "5h", others("6i")));
        assertEquals("５八金左", black(Shogi.PieceType.GOLD, "6i", "5h", others("4i")));
    }

    @Test
    public void straightUpIsSugu() {
        // 金 at ５九 (straight) and ４九 (from the right).
        assertEquals("５八金直", black(Shogi.PieceType.GOLD, "5i", "5h", others("4i")));
        assertEquals("５八金右", black(Shogi.PieceType.GOLD, "4i", "5h", others("5i")));
        // Three golds: 右, 直, 左.
        assertEquals("５八金直", black(Shogi.PieceType.GOLD, "5i", "5h", others("4i", "6i")));
        assertEquals("５八金右", black(Shogi.PieceType.GOLD, "4i", "5h", others("5i", "6i")));
        assertEquals("５八金左", black(Shogi.PieceType.GOLD, "6i", "5h", others("4i", "5i")));
    }

    @Test
    public void differentMotionsAreToldApartByMotionFirst() {
        // 金 at ４九 goes up, 金 at ６八 moves sideways, 金 at ５七 comes back.
        assertEquals("５八金上", black(Shogi.PieceType.GOLD, "4i", "5h", others("6h")));
        assertEquals("５八金寄", black(Shogi.PieceType.GOLD, "6h", "5h", others("4i")));
        assertEquals("５八金引", black(Shogi.PieceType.GOLD, "5g", "5h", others("4i")));
    }

    @Test
    public void sidewaysFromBothSidesUsesRightAndLeft() {
        assertEquals("５八金右", black(Shogi.PieceType.GOLD, "4h", "5h", others("6h")));
        assertEquals("５八金左", black(Shogi.PieceType.GOLD, "6h", "5h", others("4h")));
    }

    @Test
    public void bothMarksWhenNeitherAloneIsEnough() {
        // 銀 at ４九 and ６九 go up, 銀 at ４七 comes back: ４九 needs 右上.
        assertEquals("５八銀右上", black(Shogi.PieceType.SILVER, "4i", "5h", others("6i", "4g")));
        assertEquals("５八銀引", black(Shogi.PieceType.SILVER, "4g", "5h", others("4i", "6i")));
        assertEquals("５八銀左", black(Shogi.PieceType.SILVER, "6i", "5h", others("4i", "4g")));
    }

    @Test
    public void whiteSeesTheBoardFromTheOtherSide() {
        // 後手の金 at ６一 and ４一 both go "up" (toward rank 九) to ５二; ６一 is on white's right.
        assertEquals("５二金右", white(Shogi.PieceType.GOLD, "6a", "5b", others("4a")));
        assertEquals("５二金左", white(Shogi.PieceType.GOLD, "4a", "5b", others("6a")));
        assertEquals("５二金直", white(Shogi.PieceType.GOLD, "5a", "5b", others("4a")));
        assertEquals("５二金引", white(Shogi.PieceType.GOLD, "5c", "5b", others("4a")));
    }

    @Test
    public void dragonsUseRightAndLeftEvenWhenOneIsStraight() {
        // 竜 at ５九 (straight) and ４九 (diagonal) both go up to ５八.
        assertEquals("５八龍右", black(Shogi.PieceType.PROM_ROOK, "4i", "5h", others("5i")));
        assertEquals("５八龍左", black(Shogi.PieceType.PROM_ROOK, "5i", "5h", others("4i")));
        // On the same file only the motion is left.
        assertEquals("５八龍上", black(Shogi.PieceType.PROM_ROOK, "5i", "5h", others("5g")));
        assertEquals("５八龍引", black(Shogi.PieceType.PROM_ROOK, "5g", "5h", others("5i")));
        // Different motions do not matter for dragons: the Federation's example
        // has ８二竜左 from ９一 (a retreat) and ８二竜右 from ７二 (sideways).
        assertEquals("８二龍左", black(Shogi.PieceType.PROM_ROOK, "9a", "8b", others("7b")));
        assertEquals("８二龍右", black(Shogi.PieceType.PROM_ROOK, "7b", "8b", others("9a")));
        // The same holds for horses.
        assertEquals("５八馬右", black(Shogi.PieceType.PROM_BISHOP, "4i", "5h", others("6g")));
    }

    @Test
    public void decliningAPromotionIsWrittenNarazu() {
        assertEquals("２二銀不成", black(Shogi.PieceType.SILVER, "3c", "2b", NONE));
        assertEquals("２二銀成", MoveNotation.format(Shogi.PieceType.SILVER, sq("3c"), sq("2b"), true,
                Shogi.BLACK, MoveNotation.NO_SQUARE, NONE));
        // Leaving the zone also allows promotion.
        assertEquals("２四飛不成", black(Shogi.PieceType.ROOK, "2c", "2d", NONE));
        // Outside the zone, and for pieces that cannot promote, nothing is added.
        assertEquals("２四歩", black(Shogi.PieceType.PAWN, "2e", "2d", NONE));
        assertEquals("２二金", black(Shogi.PieceType.GOLD, "3c", "2b", NONE));
        assertEquals("２二馬", black(Shogi.PieceType.PROM_BISHOP, "3c", "2b", NONE));
        // White's zone is ranks 七 to 九.
        assertEquals("８八銀不成", white(Shogi.PieceType.SILVER, "7g", "8h", NONE));
    }

    @Test
    public void marksComeBeforePromotion() {
        assertEquals("２二銀右成", MoveNotation.format(Shogi.PieceType.SILVER, sq("1c"), sq("2b"), true,
                Shogi.BLACK, MoveNotation.NO_SQUARE, others("3c")));
    }
}
