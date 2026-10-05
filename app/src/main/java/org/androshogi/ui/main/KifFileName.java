package org.androshogi.ui.main;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A document-picker suggestion based on the game's creation time and player names. */
final class KifFileName {
    // Even four-byte UTF-8 characters leave the whole filename below 255 bytes.
    private static final int MAX_NAME_CODE_POINTS = 24;

    private KifFileName() {}

    static String baseName(long createdAt, String blackName, String whiteName) {
        String stamp = new SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(new Date(createdAt));
        return stamp + "_" + safeName(blackName, "先手") + "_vs_" + safeName(whiteName, "後手");
    }

    private static String safeName(String input, String fallback) {
        if (input == null) return fallback;
        int start = 0;
        int end = input.length();
        while (start < end && isSpace(input.charAt(start))) start++;
        while (end > start && isSpace(input.charAt(end - 1))) end--;
        if (start == end) return fallback;
        StringBuilder name = new StringBuilder();
        for (int offset = start, count = 0; offset < end && count < MAX_NAME_CODE_POINTS; count++) {
            int ch = input.codePointAt(offset);
            offset += Character.charCount(ch);
            if (Character.isISOControl(ch) || Character.isWhitespace(ch) || Character.isSpaceChar(ch)
                    || ch == '-' || "/\\:*?\"<>|".indexOf(ch) >= 0) {
                name.append('_');
            } else {
                name.appendCodePoint(ch);
            }
        }
        return name.toString();
    }

    private static boolean isSpace(char ch) {
        return Character.isWhitespace(ch) || Character.isSpaceChar(ch);
    }
}
