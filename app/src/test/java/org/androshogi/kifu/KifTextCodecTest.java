package org.androshogi.kifu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

public class KifTextCodecTest {
    private static final String SAMPLE = "手合割：平手\n先手：山田太郎\n後手：佐藤花子\n";

    @Test
    public void utf8AndShiftJisRoundTripWithoutLosingJapaneseNames() throws Exception {
        assertEquals(SAMPLE, KifTextCodec.decode(
                KifTextCodec.encode(SAMPLE, StandardCharsets.UTF_8)));
        assertEquals(SAMPLE, KifTextCodec.decode(
                KifTextCodec.encode(SAMPLE, KifTextCodec.SHIFT_JIS)));
    }

    @Test
    public void shiftJisRefusesUnsupportedCharacters() {
        assertThrows(CharacterCodingException.class,
                () -> KifTextCodec.encode("先手：😀", KifTextCodec.SHIFT_JIS));
    }

    @Test
    public void invalidBytesAreNotSilentlyReplaced() {
        assertThrows(CharacterCodingException.class,
                () -> KifTextCodec.decode(new byte[] {(byte) 0xff}));
    }
}
