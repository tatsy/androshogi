package org.androshogi.kifu;

import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;

import android.util.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;


public class KifParser {
    private static final String TAG = "KifParser";

    /** Thrown when the KIF text cannot be interpreted. Unchecked so that callers of
     *  the static factories are not forced to declare it, but MainActivity catches it. */
    public static class KifParseException extends RuntimeException {
        private final int lineNo;

        public KifParseException(String message) {
            super(message);
            this.lineNo = -1;
        }

        public KifParseException(int lineNo, String line, Throwable cause) {
            super(String.format(Locale.JAPANESE, "%d行目を解釈できません: 「%s」 (%s)",
                    lineNo, line, cause.getMessage()), cause);
            this.lineNo = lineNo;
        }

        /** 1-based line number the error was detected on, or -1 if unknown. */
        public int getLineNo() {
            return lineNo;
        }
    }

    private Date startTime;
    private String[] names = {"先手", "後手"};
    private String sfen = "";
    private Map<String, String> varInfo;
    private String comment;
    private List<Integer> moves;
    private List<Integer> times;
    private List<String> comments;
    private Shogi.GameResult gameResult;
    private String endGame;

    public KifParser() {
    }

    public static KifParser parseFile(String fileName) {
        /*
         * Parses a KIF format Shogi game notation file.
         *   :param path: Path to the file containing the KIF notation.
         *   :return: An instance of the Parser class containing all the extracted information.
         *   :raises KifFormat.ParserException: In the case of a parse error.
         */
        Path path = Paths.get(fileName);
        // Path.endsWith() compares path elements, not string suffixes.
        String encoding = fileName.endsWith(".kifu") ? "UTF-8" : "Shift_JIS";

        String content;
        try {
            content = String.join("\n", Files.readAllLines(path, Charset.forName(encoding)));
        } catch (IOException e) {
            throw new KifParseException("Cannot read " + fileName + ": " + e.getMessage());
        }
        return KifParser.parse(content);
    }

    public String blackName() {
        return names[Shogi.BLACK];
    }

    public String whiteName() {
        return names[Shogi.WHITE];
    }

    public String getSFEN() { return sfen; }
    public List<Integer> getMoves() { return moves; }
    /** Seconds spent on each move, aligned with {@link #getMoves()}; entries may be missing at the end. */
    public List<Integer> getTimes() { return times; }
    /** Comment after each move, aligned with {@link #getMoves()}; null where a move has none. */
    public List<String> getComments() { return comments; }
    /** Result from the まで〜 line, or null if the record has none. */
    public Shogi.GameResult getGameResult() { return gameResult; }

    private static String parseBod(List<String> rows,
                                   Map<Shogi.PieceType, Integer> blackHand,
                                   Map<Shogi.PieceType, Integer> whiteHand,
                                   boolean whiteToMove) {
        try {
            return BodParser.toSfen(rows, blackHand, whiteHand, whiteToMove);
        } catch (IllegalArgumentException e) {
            throw new KifParseException("盤面図を解釈できません: " + e.getMessage());
        }
    }

    private static Map<Shogi.PieceType, Integer> parsePiecesInHand(String target) {
        /*
         * Parses pieces in hand from a given string.
         *   :param target: String containing the description of the pieces in hand.
         *   :return: A dictionary representing the pieces in hand.
         *   :raises KifFormat.ParserException: In the case of a parse error.
         */
        Map<Shogi.PieceType, Integer> result = new HashMap<>();
        if (target.equals("なし")) {
            return result;
        }

        String[] items = target.split("　");
        for (String item: items) {
            item = item.trim();
            if (item.isEmpty()) {
                continue;
            }
            if (item.length() > 3) {
                throw new IllegalArgumentException(String.format("Invalid pieces in hand: %s", item));
            }
            Shogi.PieceType key = Shogi.PieceType.fromKanji(item.substring(0, 1));
            if (key == Shogi.PieceType.NONE) {
                throw new IllegalArgumentException(String.format("Unknown piece in hand: %s", item));
            }
            // "歩三" -> 3, "歩十八" -> 18, "角" -> 1
            int value = item.length() == 1 ? 1 : Shogi.KanjiNumber.parseMultiDigit(item.substring(1));
            result.put(key, value);
        }
        return result;
    }

    private static Object[] parseMoveString(String line, Board board) {
        /*
         * Parses a string of moves and applies them to a given board.
         *   :param line: String containing the moves in Japanese Shogi notation.
         *   :param board: Board object to apply the moves to.
         *   :return: A list of moves parsed from the line.
         */

        // Normalize king/promoted kanji
        line = line.replace("王", "玉");
        line = line.replace("竜", "龍");
        line = line.replace("成銀", "全");
        line = line.replace("成桂", "圭");
        line = line.replace("成香", "杏");

        Matcher m = KifFormat.MOVE_RE.matcher(line);
        int time = 0;
        if (m.find()) {
            // 経過時間を秒で取得
            String timeStr = m.group(11);
            if (timeStr != null) {
                for (String t : timeStr.split(":")) {
                    time = time * 60 + Integer.parseInt(t);
                }
            }

            if (m.groupCount() >= 1) {
                String result = m.group(1);
                if (result == null) {
                    throw new RuntimeException("Game result hasn't been detected");
                }

                if (result.equals("入玉勝ち") || result.equals("中断") || result.equals("投了") ||
                    result.equals("持将棋") || result.equals("千日手") || result.equals("詰み") ||
                    result.equals("切れ負け") || result.equals("反則勝ち") || result.equals("反則負け")) {
                    // 終局
                    return new Object[]{null, time, m.group(1)};
                } else {
                    // 終局でない
                    Shogi.PieceType pieceType = Shogi.PieceType.fromKanji(m.group(5));
                    String douText = m.group(2);
                    if (douText == null) {
                        throw new RuntimeException(String.format("Unknown dou flag: %s", m.group(2)));
                    }

                    // 駒が動く先の座標を取得
                    int squareTo = 0;
                    if (douText.startsWith("同")) {
                        squareTo = Move.dest(board.peek());
                    } else {
                        Integer toField = Shogi.ZenkakuNumber.parse(m.group(3));
                        Integer toRank = Shogi.KanjiNumber.parse(m.group(4));
                        if (toField != null && toRank != null) {
                            squareTo = (toRank - 1) + (toField - 1) * 9;
                        } else {
                            throw new RuntimeException(String.format("Invalid destination coordinate: %s, %s", m.group(3), m.group(4)));
                        }
                    }

                    // 着手の処理
                    if (m.group(6).equals("打") || (m.group(8).equals("0") && m.group(9).equals("0"))) {
                        // 持駒を打つ
                        return new Object[] {board.getDropMove(squareTo, pieceType),time, null};
                    } else {
                        // 盤上の駒を動かす
                        int from_field = Integer.parseInt(m.group(8)) - 1;
                        int from_rank = Integer.parseInt(m.group(9)) - 1;
                        int squareFrom = from_rank + from_field * 9;

                        boolean promotion = m.group(7).equals("成");
                        return new Object[] {board.getMove(squareFrom, squareTo, promotion), time, null};
                    }
                }
            }
        }
        return new Object[] {null, null, null};
    }


    public static KifParser parse(String kifStr) {
        /*
         * Parses a KIF formatted string into a Parser object.
         *   :param kif_str: The KIF formatted string representing the Shogi game.
         *   :return: An instance of the Parser class containing all the extracted information.
         *   :raises KifFormat.ParserException: In the case of a parse error.
         */

        int lineNo = 0;
        Date startTime = null;
        String[] names = {"先手", "後手"};
        Map<Integer, Map<Shogi.PieceType, Integer>> piecesInHand = new HashMap<>();
        piecesInHand.put(Shogi.BLACK, new HashMap<>());
        piecesInHand.put(Shogi.WHITE, new HashMap<>());

        String sfen = Shogi.STARTING_SFEN;
        Map<String, String> varInfo = new HashMap<>();
        List<String> headerComments = new ArrayList<>();
        List<Integer> moves = new ArrayList<>();
        List<Integer> times = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        Shogi.GameResult win = null;
        String endGame = null;
        boolean movesFinished = false;
        Board board = new Board();
        try {
        // A board diagram (BOD) gives the start position when there is no
        // handicap name. Its rows and the 持駒 lines are collected here and
        // turned into the SFEN once the first move needs the board.
        List<String> bodRows = new ArrayList<>();
        boolean bodWhiteToMove = false;
        // Line number and text of 手合割：その他, which promises a diagram; -1 when absent.
        int bodPromisedAt = -1;
        String bodPromiseLine = null;

        for (String rawLine : kifStr.split("\\r?\\n")) {
            lineNo++;
            final String line = rawLine.trim();
            try {

                // 空行なら何もしない
                if (line.isEmpty()) {
                    continue;
                }

                if (BodParser.isBoardRow(line)) {
                    // 盤面図の行
                    bodRows.add(line);
                } else if (line.equals("後手番") || line.equals("上手番")) {
                    bodWhiteToMove = true;
                } else if (line.equals("先手番") || line.equals("下手番")) {
                    bodWhiteToMove = false;
                } else if (line.startsWith("*")) {
                    // コメント行の処理
                    if (!moves.isEmpty()) {
                        while (moves.size() - comments.size() > 1) {
                            comments.add(null);
                        }
                        String comment = line.startsWith("**") ? line.substring(2) : line.substring(1);
                        if (comments.size() == moves.size()) {
                            comments.set(comments.size() - 1, comments.get(comments.size() - 1) + "\n" + comment);
                        } else {
                            comments.add(comment);
                        }
                    } else {
                        headerComments.add(line.substring(1));
                    }
                } else if (line.startsWith("変化：")) {
                    // Variations are not represented yet. Keep the main line intact.
                    break;
                } else if (line.contains("：")) {
                    // ヘッダ情報のキーと値のペアをパースする
                    String[] keyValue = line.split("：", 2);
                    String key = keyValue[Shogi.BLACK].strip();
                    String value = keyValue[Shogi.WHITE].strip();

                    if (key.equals("開始日時")) {
                        // 対局開始時間を日本時間として取得
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);
                        try {
                            startTime = dateFormat.parse(value);
                        } catch (ParseException unused) {
                            dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN);
                            try {
                                startTime = dateFormat.parse(value);
                            } catch (ParseException e) {
                                Log.e(TAG, e.toString(), e);
                            }
                        }
                    } else if (key.equals("先手") || key.equals("下手")) {
                        // 先手番の棋士名
                        names[Shogi.BLACK] = value;
                    } else if (key.equals("後手") || key.equals("上手")) {
                        // 後手番の棋士名
                        names[Shogi.WHITE] = value;
                    } else if (key.equals("先手の持駒") || key.equals("下手の持駒")) {
                        // 先手の持駒
                        piecesInHand.put(Shogi.BLACK, KifParser.parsePiecesInHand(value));
                    } else if (key.equals("後手の持駒") || key.equals("上手の持駒")) {
                        // 後手の持駒
                        piecesInHand.put(Shogi.WHITE, KifParser.parsePiecesInHand(value));
                    } else if (key.equals("手合割")) {
                        // 手合いに応じた初期局面をセットする。「その他」は盤面図が続く
                        if (value.equals("その他")) {
                            bodPromisedAt = lineNo;
                            bodPromiseLine = line;
                        } else {
                            sfen = KifFormat.HANDYCAP_SFENS.get(value);
                            if (sfen == null) {
                                throw new RuntimeException("Unknown handy cap type!");
                            }
                            board.setSFEN(sfen);
                        }
                    } else {
                        varInfo.put(key, value);
                    }
                } else {
                    // Move lines start with the move number. The 持駒 and 後手番 lines
                    // follow the diagram, so it is only complete once the moves begin.
                    boolean moveLine = line.charAt(0) >= '0' && line.charAt(0) <= '9';
                    if (moveLine && !bodRows.isEmpty() && moves.isEmpty()) {
                        // The header is over; the diagram decides the start position.
                        sfen = parseBod(bodRows, piecesInHand.get(Shogi.BLACK),
                                piecesInHand.get(Shogi.WHITE), bodWhiteToMove);
                        board.setSFEN(sfen);
                        bodRows.clear();
                        bodPromisedAt = -1;
                    }
                    Object[] moveData = KifParser.parseMoveString(line, board);
                    if (moveData[2] != null) {
                        // Numbered terminal lines (投了, 入玉勝ち, ...) end the main record.
                        // Keep scanning non-move lines so a following "まで..." line can set the result.
                        movesFinished = true;
                        continue;
                    }
                    if (moveData[0] != null) {
                        if (movesFinished) {
                            throw new IllegalArgumentException("Move found after game result");
                        }
                        int move = (int)moveData[0];
                        if (move == Shogi.MOVE_NONE || !board.isLegal(move)) {
                            throw new IllegalArgumentException("Illegal move");
                        }
                        moves.add(move);
                        board.push(move);

                        if (moveData[1] != null) {
                            int time = (int)moveData[1];
                            times.add(time);
                        }
                    } else {
                        Matcher m = KifFormat.RESULT_RE.matcher(line);
                        if (m.find()) {
                            String winSideStr = m.group(3);
                            if (winSideStr == null) {
                                // 千日手 / 持将棋 / 中断: no winning side in the text
                                if ("中断".equals(m.group(2))) {
                                    win = null;
                                    endGame = "%CHUDAN";
                                } else {
                                    win = Shogi.GameResult.DRAW;
                                    endGame = "%SENNICHITE";
                                }
                            } else if (winSideStr.equals("先") || winSideStr.equals("下")) {
                                if (m.group(4).equals("反則負け")) {
                                    win = Shogi.GameResult.WHITE_WIN;
                                    endGame = "@ILLEGAL_MOVE";
                                } else {
                                    win = Shogi.GameResult.BLACK_WIN;
                                    if (m.group(4).equals("反則勝ち")) {
                                        endGame = "%+ILLEGAL_ACTION";
                                    } else {
                                        endGame = m.group(4).equals("入玉勝ち") ? "%KACHI" : "%TORYO";
                                    }
                                }
                            } else if (winSideStr.equals("後") || winSideStr.equals("上")) {
                                if (m.group(4).equals("反則負け")) {
                                    win = Shogi.GameResult.BLACK_WIN;
                                    endGame = "%ILLEGAL_MOVE";
                                } else {
                                    win = Shogi.GameResult.WHITE_WIN;
                                    if (m.group(4).equals("反則勝ち")) {
                                        endGame = "%-ILLEGAL_ACTION";
                                    } else {
                                        endGame = m.group(4).equals("入玉勝ち") ? "%KACHI" : "%TORYO";
                                    }
                                }
                            }

                            // 終了のテキストが見つかったら読み込み終了
                            break;
                        }
                        if (moveLine) {
                            throw new IllegalArgumentException("Unknown move notation");
                        }
                    }
                }
            } catch (KifParseException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new KifParseException(lineNo, line, e);
            }
        }
        if (!bodRows.isEmpty()) {
            // A diagram with no moves after it.
            sfen = parseBod(bodRows, piecesInHand.get(Shogi.BLACK),
                    piecesInHand.get(Shogi.WHITE), bodWhiteToMove);
        } else if (bodPromisedAt > 0) {
            // 手合割：その他 without the diagram it announces: the start position is unknown.
            throw new KifParseException(bodPromisedAt, bodPromiseLine,
                    new RuntimeException("手合割「その他」には盤面図が必要です"));
        }
        Log.d(TAG, String.format("%d lines of data is parsed", lineNo));

        KifParser parser = new KifParser();
        parser.startTime = startTime;
        parser.names = names;
        parser.sfen = sfen;
        parser.varInfo = varInfo;
        parser.comment = String.join("\n", headerComments);
        parser.moves = moves;
        parser.times = times;
        parser.comments = comments;
        parser.gameResult = win;
        parser.endGame = endGame;

        return parser;
        } finally {
            board.cleanup();
        }
    }

    public void secToTime(int seconds) {
        int h = seconds / (60 * 60);
        int m_ = seconds % (60 * 60);
        int m = m_ / 60;
        int s = m_ % 60;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR, h);
        c.set(Calendar.MINUTE, m);
        c.set(Calendar.SECOND, s);
    }

    /*
    def move_to_kif(move: int, prev_move: Optional[int] = None) -> str:
            """Convert a given move to Japanese KIF notation.

    :param move: An integer representing the move.
    :param board: A Board object representing the current state of the game.
    :return: A string representing the move in KIF notation.
    """
    to_sq = cshogi.move_to(move)
    move_to = KIFU_TO_SQUARE_NAMES[to_sq]
            if prev_move:
            if cshogi.move_to(prev_move) == to_sq:
    move_to = "同　"
            if not cshogi.move_is_drop(move):
    from_sq = cshogi.move_from(move)
    move_piece = cshogi.PIECE_JAPANESE_SYMBOLS[cshogi.move_from_piece_type(move)]
            if cshogi.move_is_promotion(move):
            return '{}{}成({})'.format(
            move_to,
            move_piece,
            KIFU_FROM_SQUARE_NAMES[from_sq],
            )
        else:
                return '{}{}({})'.format(
            move_to,
            move_piece,
            KIFU_FROM_SQUARE_NAMES[from_sq],
            )
    else:
    move_piece = cshogi.HAND_PIECE_JAPANESE_SYMBOLS[cshogi.move_drop_hand_piece(move)]
            return '{}{}打'.format(
            move_to,
            move_piece
            )

    def board_to_bod(board: Board) -> str:
            """Convert a given board to a Board Diagram (BOD) representation.

    :param board: A Board object representing the current state of the game.
    :return: A string representing the Board Diagram (BOD) of the game.
    """
    def hand_pieces_str(color):
            if any(board.pieces_in_hand[color]):
    str_list = []
            for symbol, n in zip(reversed(cshogi.HAND_PIECE_JAPANESE_SYMBOLS), reversed(board.pieces_in_hand[color])):
            if n > 1:
            str_list.append(symbol + cshogi.NUMBER_JAPANESE_KANJI_SYMBOLS[n])
    elif n == 1:
            str_list.append(symbol)
            return '　'.join(str_list)
        else:
                return 'なし'

    str_list = [
            '後手の持駒：' + hand_pieces_str(cshogi.WHITE),
        '  ９ ８ ７ ６ ５ ４ ３ ２ １',
                '+---------------------------+'
                ]
                str_list.extend(
                ['|' + ''.join([PIECE_BOD_SYMBOLS[board.piece(f * 9 + r)] for f in reversed(range(9))]) + '|' + cshogi.NUMBER_JAPANESE_KANJI_SYMBOLS[r + 1] for r in range(9)]
            )
            str_list.append('+---------------------------+')
            str_list.append('先手の持駒：' + hand_pieces_str(cshogi.BLACK))
            if board.turn == cshogi.WHITE:
            str_list.append('後手番')

            return '\n'.join(str_list)

    def move_to_bod(move: int, board: Board) -> str:
            """Convert a given move to a Board Diagram (BOD) representation.

    :param move: An integer representing a specific move.
    :param board: A Board object representing the current state of the Shogi game.
    :return: A string representing the move in Board Diagram (BOD) format.
    """
            import cshogi.KI2
            move_str = cshogi.KI2.move_to_ki2(move, board)
    if move_str[1] == '同':
    to_sq = cshogi.move_to(move)
            return move_str[0] + KIFU_TO_SQUARE_NAMES[to_sq] + '同' + move_str[3:]
            else:
            return move_str

    class Exporter:
            """A class to handle the exporting of a game to KIF format.

    :param path: Optional path to the file where the KIF formatted game will be written. If None, no file is opened initially.
    """

    def __init__(self, path: Optional[str] = None):
            if path:
            self.open(path)
            else:
    self.kifu = None

    def open(self, path: str):
            """Open a file for writing the KIF formatted game.

        :param path: Path to the file.
        """
    _, ext = os.path.splitext(path)
    enc = 'utf-8' if ext == '.kifu' else 'cp932'
    self.kifu = open(path, 'w', encoding=enc)
    self.prev_move = None
    self.move_number = 1

    def close(self):
            """Close the file."""
            self.kifu.close()

    def header(self, names: List[str], starttime: Optional[datetime] = None, handicap: Optional[Union[str, Board]] = None):
            """Write the header information to the file.

        :param names: List of player names.
        :param starttime: Start time of the game, defaults to current time.
        :param handicap: Handicap settings for the game.
        """
            if starttime is None:
    starttime = datetime.now()
            self.kifu.write('開始日時：' + starttime.strftime('%Y/%m/%d %H:%M:%S\n'))
            if handicap is None:
            self.kifu.write('手合割：平手\n')
    elif type(handicap) is Board or type(handicap) is str and handicap[:5] == 'sfen ':
            if type(handicap) is Board:
    board = handicap
            else:
    board = Board(sfen=handicap[5:])
            self.kifu.write(board_to_bod(board) + '\n')
            else:
            self.kifu.write('手合割：' + handicap + '\n')
            self.kifu.write('先手：' + names[0] + '\n')
            self.kifu.write('後手：' + names[1] + '\n')
            self.kifu.write('手数----指手---------消費時間--\n')

    def move(self, move: int, sec: int = 0, sec_sum: int = 0):
            """Record a move in the game.

        :param move: The move to record.
        :param sec: Seconds spent on the move.
        :param sec_sum: Total seconds spent so far.
        """
    m, s = divmod(math.ceil(sec), 60)
    h_sum, m_sum, s_sum = sec_to_time(sec_sum)

        if cshogi.move_is_drop(move):
    padding = '    '
    elif cshogi.move_is_promotion(move):
    padding = ''
            else:
    padding = '  '
    move_str = move_to_kif(move, self.prev_move) + padding

        self.kifu.write('{:>4} {}      ({:>2}:{:02}/{:02}:{:02}:{:02})\n'.format(
            self.move_number,
            move_str,
            m, s,
            h_sum, m_sum, s_sum))

    self.move_number += 1
    self.prev_move = move

    def end(self, reason: str, sec: int = 0, sec_sum: int = 0):
            """Record the end of the game.

        :param reason: The reason for the end of the game  (e.g., resign, sennichite).
        :param sec: Seconds spent on the last move.
        :param sec_sum: Total seconds spent during the game.
        """
    m, s = divmod(math.ceil(sec), 60)
    h_sum, m_sum, s_sum = sec_to_time(sec_sum)

        if reason == 'resign':
    move_str = '投了        '
    elif reason == 'win':
    move_str = '入玉宣言    '
    elif reason == 'draw':
    move_str = '持将棋      '
    elif reason == 'sennichite':
    move_str = '千日手      '
    elif reason == 'illegal_win':
    move_str = '反則勝ち    '
    elif reason == 'illegal_lose':
    move_str = '反則負け    '

            self.kifu.write('{:>4} {}      ({:>2}:{:02}/{:02}:{:02}:{:02})\n'.format(
            self.move_number,
            move_str,
            m, s,
            h_sum, m_sum, s_sum))

            # 結果出力
        if reason == 'resign':
            self.kifu.write('まで{}手で{}の勝ち\n'.format(self.move_number - 1, '先手' if self.move_number % 2 == 0 else '後手'))
    elif reason == 'draw':
            self.kifu.write('まで{}手で持将棋\n'.format(self.move_number + 1))
    elif reason == 'win':
            self.kifu.write('まで{}手で入玉宣言\n'.format(self.move_number - 1))
    elif reason == 'sennichite':
            self.kifu.write('まで{}手で千日手\n'.format(self.move_number - 1))
    elif reason == 'illegal_win':
            self.kifu.write('まで{}手で{}の反則勝ち\n'.format(self.move_number - 1, '先手' if self.move_number % 2 == 0 else '後手'))
    elif reason == 'illegal_lose':
            self.kifu.write('まで{}手で{}の反則負け\n'.format(self.move_number - 1, '先手' if self.move_number % 2 == 0 else '後手'))

    def info(self, info):
            """Record additional information related to the game.

        :param info: A string containing additional information.
        """
    turn = self.move_number % 2
    items = info.split(' ')
    comment = '**対局'
    i = 1
            while i < len(items):
            if items[i] == 'time':
    i += 1
    m, s = divmod(int(items[i]) / 1000, 60)
    s_str = '{:.1f}'.format(s)
                if s_str[1:2] == '.':
    s_str = '0' + s_str
    comment += ' 時間 {:>02}:{}'.format(int(m), s_str)
    elif items[i] == 'depth':
    i += 1
    comment += ' 深さ {}'.format(items[i])
    elif items[i] == 'nodes':
    i += 1
    comment += ' ノード数 {}'.format(items[i])
    elif items[i] == 'score':
    i += 1
            if items[i] == 'cp':
    i += 1
    comment += ' 評価値 {}'.format(items[i] if turn == cshogi.BLACK else -int(items[i]))
    elif items[i] == 'mate':
    i += 1
            if items[i][0:1] == '+':
    comment += ' +詰' if turn == cshogi.BLACK else ' -詰'
            else:
    comment += ' -詰' if turn == cshogi.BLACK else ' +詰'
    comment += str(items[i][1:])
            else:
    i += 1
            self.kifu.write(comment + '\n')

     */
}
