package org.androshogi.ui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KifFileNameTest {
    @Test public void eventNameLeadsTheSuggestedFileName() {
        assertEquals("竜王戦_藤井聡太_佐々木勇気",
                KifFileName.baseName(0, "竜王戦", "藤井聡太", "佐々木勇気"));
    }

    @Test public void unsafeCharactersAreReplaced() {
        assertEquals("王将_戦_先_手_後_手",
                KifFileName.baseName(0, " 王将/戦 ", "先 手", "後-手"));
    }

    @Test public void absentEventKeepsTheTimestampFallback() {
        String actual = KifFileName.baseName(0, "　", "先手", "後手");
        assertTrue(actual.matches("\\d{6}_\\d{6}_先手_vs_後手"));
    }
}
