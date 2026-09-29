package org.androshogi.kifu;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Encodes KIF documents without silently replacing characters. */
public final class KifTextCodec {
    public static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");

    private KifTextCodec() {}

    /** Imported files may be UTF-8 (.kifu) or Shift_JIS (.kif), regardless of MIME type. */
    public static String decode(byte[] data) throws CharacterCodingException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return SHIFT_JIS.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        }
    }

    public static byte[] encode(String text, Charset charset) throws CharacterCodingException {
        ByteBuffer buffer = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text));
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }
}
