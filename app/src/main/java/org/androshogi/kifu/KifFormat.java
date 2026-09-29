package org.androshogi.kifu;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class KifFormat {
    public static final String[] KIFU_TO_SQUARE_NAMES = {
            "１一", "１二", "１三", "１四", "１五", "１六", "１七", "１八", "１九",
            "２一", "２二", "２三", "２四", "２五", "２六", "２七", "２八", "２九",
            "３一", "３二", "３三", "３四", "３五", "３六", "３七", "３八", "３九",
            "４一", "４二", "４三", "４四", "４五", "４六", "４七", "４八", "４九",
            "５一", "５二", "５三", "５四", "５五", "５六", "５七", "５八", "５九",
            "６一", "６二", "６三", "６四", "６五", "６六", "６七", "６八", "６九",
            "７一", "７二", "７三", "７四", "７五", "７六", "７七", "７八", "７九",
            "８一", "８二", "８三", "８四", "８五", "８六", "８七", "８八", "８九",
            "９一", "９二", "９三", "９四", "９五", "９六", "９七", "９八", "９九",
    };

    public static final String[] KIFU_FROM_SQUARE_NAMES = {
            "11", "12", "13", "14", "15", "16", "17", "18", "19",
            "21", "22", "23", "24", "25", "26", "27", "28", "29",
            "31", "32", "33", "34", "35", "36", "37", "38", "39",
            "41", "42", "43", "44", "45", "46", "47", "48", "49",
            "51", "52", "53", "54", "55", "56", "57", "58", "59",
            "61", "62", "63", "64", "65", "66", "67", "68", "69",
            "71", "72", "73", "74", "75", "76", "77", "78", "79",
            "81", "82", "83", "84", "85", "86", "87", "88", "89",
            "91", "92", "93", "94", "95", "96", "97", "98", "99",
    };

    public static final String[] PIECE_BOD_SYMBOLS = {
            " ・", " 歩", " 香", " 桂", " 銀", " 角", " 飛", " 金",
            " 玉", " と", " 杏", " 圭", " 全", " 馬", " 龍",
            "",
            " ・", "v歩", "v香", "v桂", "v銀", "v角", "v飛", "v金",
            "v玉", "vと", "v杏", "v圭", "v全", "v馬", "v龍"
    };

    public static final Pattern MOVE_RE = Pattern.compile("\\A *[0-9]+\\s+(中断|投了|入玉勝ち|持将棋|千日手|詰み|切れ負け|反則勝ち|反則負け|(([１２３４５６７８９])([零一二三四五六七八九])|同[ 　]*)([歩香桂銀金角飛玉と杏圭全馬龍])(打|((?:成|不成)?)\\(([0-9])([0-9])\\)))\\s*(\\( *([:0-9]+)/([:0-9]+)\\))?.*\\Z");

    public static Map<String, String> HANDYCAP_SFENS = new HashMap<>() {
        {
            put("平手", "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1");
            put("香落ち", "lnsgkgsn1/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("右香落ち", "1nsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("角落ち", "lnsgkgsnl/1r7/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("飛車落ち", "lnsgkgsnl/7b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("飛香落ち", "lnsgkgsn1/7b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("二枚落ち", "lnsgkgsnl/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("三枚落ち", "lnsgkgsn1/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("四枚落ち", "1nsgkgsn1/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("五枚落ち", "2sgkgsn1/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("左五枚落ち", "1nsgkgs2/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("六枚落ち", "2sgkgs2/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("八枚落ち", "3gkg3/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("十枚落ち", "4k4/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1");
            put("その他", null);
        }
    };

    // Groups: 1 = move count, 2 = whole result, 3 = winning side (null for 千日手/持将棋/中断), 4 = kind of win
    public static final Pattern RESULT_RE = Pattern.compile("　*まで、?(\\d+)手で(([先下後上])手の(勝ち|入玉勝ち|反則勝ち|反則負け)|千日手|持将棋|中断)");
}
