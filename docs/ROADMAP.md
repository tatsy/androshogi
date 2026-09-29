# AndroShogi 開発ロードマップ

この文書は、現在の実装に対する**今後の作業計画**だけを管理します。
完了した変更の詳細は Git の履歴と Release / Pull Request を参照してください。
現在確認されている不具合は [KNOWN_ISSUES.md](KNOWN_ISSUES.md) に分離します。

## 現在地

v0.1.0 では、将棋の棋譜をやねうら王 NNUE で検討するための基本機能が一通り成立しました。

- 盤面操作、合法手、成り／不成、持駒、盤反転
- KIF の読み込み、BOD 初期局面、KIF のコピー／共有
- 手動検討と MultiPV 表示
- 棋譜全体の自動解析、キャンセル、進捗表示
- 手数ごとの解析結果、棋譜リスト、勝率グラフ
- 棋譜と解析結果の JSON 保存／復元、棋譜一覧
- エンジン設定、表示設定、ダークモード
- JVM unit test / Android instrumentation test / GitHub Actions
- GitHub Releases からの ABI 別 APK 配布

2026-09-22 時点で、コード見直しで列挙した既知不具合 #1–#15 はすべて解消済みです。

次の目標は **v0.2.0 を、内部構造と配布基盤を固めた公開版にすること**です。
新機能を大量に追加するより、アプリの identity、署名、コード構造、回帰テストを先に安定させます。

---

## v0.2.0

### 1. アプリの identity と配布基盤を確定する

v0.2.0 以降で変えにくい項目を最初に決めます。

- [x] 正式なアプリ identity を反映する
  - [x] 表示名は `AndroShogi` に確定
  - [x] `applicationId` は `org.androshogi` に確定
  - [x] Java / Android の `namespace` は `org.androshogi` に確定
  - [x] Manifest の Application / Activity 参照を現在の package 構造に追従
  - v0.1.0 の `com.example.androshogi` とは Android 上で別アプリとして扱われる
- [x] 正式な release keystore を作成し、v0.2.0 以降は同じ鍵を継続利用する
- [x] GitHub Actions の release build を正式鍵で署名する
- [x] `versionCode` を正式版タグの順番で単調増加させる（`v0.1.0=1`、`v0.2.0=2`。RC は正式版と同じ番号）
- [x] Release ワークフローで release APK の署名と version 情報を確認する
- [ ] 必要性を確認したうえで R8 / minify を検討する

v0.1.0 が debug key 署名のままなら、正式鍵へ切り替えた v0.2.0 は上書きインストールできない可能性がある。
その場合は v0.2.0 の release note で一度アンインストールが必要であることを明記する。

### 2. Java パッケージ構造を整理する

Java / Android の namespace を `org.androshogi` に統一し、UI、棋譜、保存、エンジン、JNI wrapper の責務が package 構造から分かるように整理済み。
現在の構成は次のとおり。

```text
org.androshogi
├── AndroShogiApplication
├── shogi
│   ├── Shogi
│   ├── Board
│   ├── Move
│   ├── Piece
│   ├── LegalMoveList
│   └── PseudoLegalMoveList
├── engine
│   ├── EngineSession
│   ├── EngineInfo
│   └── EngineUpdateListener
├── game
│   ├── GameRecord
│   ├── GameAnalysis
│   └── PositionAnalysis
├── kifu
│   ├── KifParser
│   ├── KifWriter
│   ├── KifFormat
│   ├── BodParser
│   └── MoveNotation
├── storage
│   ├── GameStore
│   ├── GameJson
│   ├── SavedGame
│   └── GameSummary
├── settings
│   └── AppSettings
└── ui
    ├── main
    ├── games
    └── settings
```

依存方向は概ね次を目標にする。

```text
ui
 ↓
game / kifu / engine / storage / settings
 ↓
shogi
```

#### 移動状況

- [x] application package を `org.androshogi` に確定する
- [x] JNI 非依存の model / parser / data クラスを移動する
  - [x] `game` (`GameRecord`, `GameAnalysis`, `PositionAnalysis`)
  - [x] `storage` (`GameStore`, `GameJson`, `SavedGame`, `GameSummary`)
  - [x] `settings` (`AppSettings`)
  - [x] `kifu` (`KifParser`, `KifWriter`, `KifFormat`, `BodParser`, `MoveNotation`)
- [x] engine 関連クラスを移動する
- [x] UI クラスを画面単位に移動する
- [x] JNI wrapper (`Board`, `Move`, `LegalMoveList`, `PseudoLegalMoveList` など) をまとめて移動する
- [x] unit / instrumentation test も同じパッケージ構造へ移動する
- [x] CI で build / JVM unit test / Android instrumentation test が成功することを確認する
- [ ] 実機で起動・棋譜読み込み・検討を確認する

JNI wrapper の package 変更では、`jni.cpp` の関数名にも完全修飾クラス名が埋め込まれる。
現在は `Java_org_androshogi_shogi_Board_...` のような symbol を使用しているため、
今後 JNI wrapper の package を変更する場合も Java 側の package と JNI symbol を同じコミットで更新する。

過度な細分化はしない。まず「UI / engine / game / kifu / storage / settings / shogi」の境界が見えることを優先する。

### 3. `MainActivity` の責務を減らす

現在の `MainActivity` は View の初期化や画面遷移だけでなく、
エンジン制御、自動解析ループ、棋譜と解析結果の状態、保存 ID、非同期 save / load、
KIF の共有入出力まで同時に管理している。
単にファイルが長いことよりも、複数の状態機械が同じ Activity の field と callback で結合していることが保守上の問題になる。

v0.2.0 では全面的な MVVM 化や大規模な architecture 変更は行わず、
**独立した状態管理・進行管理だけを壊しにくい単位で外へ出す**。
UI の表示や Android lifecycle に直接関係する処理は `MainActivity` に残す。

#### 3.1 自動解析ループを `AnalysisController` へ分離する

最初に、現在 `MainActivity.AnalysisRun` が担当している自動解析の状態機械を独立させる。
これは `MainActivity` 分割の中で最も効果が大きく、単体テストもしやすい部分なので最優先とする。

- [x] `AnalysisController` を追加する
  - 探索開始
  - 現在局面から次局面への反復
  - キャンセル / abandon
  - 進捗 (`analyzed / total`)
  - 正常終了 / 中断 / エラーの区別
  - 正常終了した結果だけを対象局面へ保存する判断
- [x] `AnalysisController` は `ProgressBar`、`Toast`、`BoardView` などの View を直接操作しない
- [x] UI に必要な変化は listener / callback で `MainActivity` へ通知する
  - 進捗更新
  - 表示局面の移動要求
  - 完了
  - キャンセル
  - エラー
- [x] `AnalysisController` の JVM unit test を追加する
  - 全局面を正常に解析できる
  - 途中キャンセル時に partial result を保存しない
  - エンジン開始失敗時に終了状態へ遷移する
  - 最終局面で正しく完了する

`EngineSession` 自体は既に process / search の責務を持っているため、
v0.2.0 では新しい `EngineController` を先に導入しない。
`AnalysisController` 分離後も `MainActivity` から `EngineSession` を直接扱う構造で十分かを改めて判断する。

#### 3.2 ゲーム状態を `GameSession` にまとめる

次に、現在ばらばらに保持している棋譜と解析結果、その保存上の identity を1つの状態として扱えるようにする。

対象は概ね次の field。

```text
GameRecord record
GameAnalysis analysis
String gameId
long gameCreatedAt
```

- [x] `GameSession` を導入し、上記状態をまとめる
- [x] `GameSession` は Android View に依存させない
- [x] 次のゲーム状態の整合性ルールを Activity から移す
  - 新規ゲーム生成時の ID / createdAt 更新
  - scratch game の判定
  - 棋譜分岐時の解析結果 truncate
  - 保存用 `SavedGame` snapshot の生成
  - 保存済みゲームを読み込んだ際の状態置換
- [x] `GameSession` の JVM unit test を追加する

`GameSession` は永続化そのものを担当しない。
`GameStore` は storage 層として維持し、ゲーム状態と disk I/O の責務を混ぜない。

#### 3.3 非同期 save / load の調停を整理する

現在 `MainActivity` は `ExecutorService` に加え、
`gameLoadGeneration` と `initialGameLoadPending` を使って非同期 load とユーザー操作の競合も管理している。
これは View の責務ではないため、`GameSession` 分離後に Activity から外す価値がある。

- [x] save / load の非同期調停を小さな helper / coordinator に切り出すか検討する
  - save の順序保証
  - 古い load result の無効化
  - startup load 完了前の誤保存防止
  - game list を開く前に現在状態の保存を完了する処理
- [ ] 既存の `GameStore` は低レベルの保存 API として維持する
- [x] クラス追加の効果が小さいため、v0.2.0 では `MainActivity` に残す

現状の save / load は単一の `ExecutorService`、load 世代番号、起動時の pending 状態で調停している。
これらは Activity の終了判定と UI callback にも接しているため、v0.2.0 では分離せず維持する。
保存順序・古い load の棄却・起動時の誤保存・一覧表示前の保存は引き続き回帰確認する。

この段階は `AnalysisController` と `GameSession` より優先度を下げる。
クラス数を増やすこと自体を目的にしない。

#### 3.4 `MainActivity` に残す責務

v0.2.0 完了時の `MainActivity` は、Android UI の入口として次を中心にする。

- View binding / listener 登録
- menu / dialog / Toast などの UI
- Activity / Intent による画面遷移
- `BoardView` / `EngineView` / move list / graph への表示反映
- `AnalysisController` への start / cancel 指示
- `GameSession` へのユーザー操作の反映
- `EngineSession` の UI レベルの状態通知と手動検討
- Android lifecycle (`onCreate`, `onResume`, `onStop`, `onDestroy`, `onNewIntent`)

逆に、次は可能な限り Activity の外へ移す。

```text
自動解析ループの状態機械
ゲーム状態の整合性管理
保存 ID / createdAt の管理
解析結果がどの局面に属するかの判断
非同期 save / load の競合制御（効果が十分なら）
```

#### 3.5 v0.2.0 では後回しにするもの

次は今回の `MainActivity` 整理の必須条件にしない。

- ViewModel / retained state への全面移行
- `configChanges` の撤廃
- Foreground Service によるバックグラウンド解析
- Kotlin / Jetpack Compose への移行
- `EngineSession` を包む専用 controller の新設
- KIF 入出力 UI の大規模分割

KIF の file I/O / charset 処理は、後段の Storage Access Framework 対応と一緒に整理する方が二度手間が少ない。
現在の `configChanges` による設定変更対応は暫定策として維持し、
Controller / session 分離後に Activity lifecycle から engine state をさらに切り離す必要があるか判断する。

#### 実装順

- [x] `AnalysisController` のテストと切り出し
- [x] `GameSession` の必要 API を整理し、テストとともに導入
- [x] save / load 調停の分離が有効かを再評価し、必要なら切り出す
- [x] `MainActivity` の不要 field / helper / callback を整理する
- [ ] CI と実機で、手動検討・自動解析・キャンセル・棋譜切替・保存／復元を回帰確認する
  - 自動解析・キャンセルは分割後の実機動作で問題なし（2026-09-24）。棋譜切替・保存／復元など、残る操作は引き続き確認する

各段階は小さなコミットに分け、機能変更と構造変更を可能な限り混ぜない。

### 4. エンジンと解析の公開前チェック

- [x] 起動から `readyok` までの実機動作と所要時間を確認する（2026-09-23、初回 `readyok` まで 1,088 ms。実機での1回の測定値）
- [ ] エンジン起動失敗時・予期せぬ終了時のメッセージを確認する
  - [x] 予期せぬ終了時: debug APK の実機確認で、ポップアップと Logcat (`E/EngineSession`) の両方に「エンジンが予期せず終了しました」を確認（2026-09-23）
  - [ ] 起動失敗時: 起動できない場合のメッセージを実機で確認する
- [x] 長い棋譜解析でメモリが増え続けないことを確認する（2026-09-24、実機で166手を1手3秒で解析。提示された約5分間のエンジン TOTAL PSS は342,155–343,034 KBで持続増加なし。実機での1回の確認）
- [x] Activity 終了後にやねうら王プロセスが残らないことを確認する（2026-09-24、解析後にアプリを終了し、`adb shell ps -A -o PID,PPID,ARGS` で `YaneuraOu_NNUE` を含むプロセスが残っていないことを実機確認）
- [x] 旧同梱エンジンの `FV_SCALE` 既定値を実機ログで確認する（2026-09-23、`default 24`）。非同梱版への差し替え後は、使用する評価関数に応じて再確認する
- [ ] 中断された探索結果は保存せず、正常終了した探索結果は時間・depth に関係なく最新結果で上書きする現在の方針を維持する
  - [x] 解析中断時に既存結果の上書きが起こらないことを実機で確認（2026-09-24）
  - [ ] 正常終了時に、解析時間・depth にかかわらず最新結果へ上書きされることを確認する

### 5. v0.2.0 の機能追加

v0.2.0 では大きなデータモデル変更を避け、既存機能につながるものを優先する。

第一候補:

- [x] Storage Access Framework で KIF ファイルを選んで開く
- [x] KIF ファイルとして保存する（.kif は Shift_JIS、.kifu は UTF-8）
- [x] Shift_JIS / UTF-8 の扱いを明示し、round-trip test を追加する

v0.2.0 に含める機能は原則ここまでとし、KI2 / CSA、棋譜分岐などは後回しにする。

### 6. v0.2.0 リリース判定

- [ ] JVM unit test がすべて成功
- [ ] API 34 emulator の instrumentation test が成功
- [ ] arm64-v8a 実機で smoke test
- [ ] 起動、盤面操作、KIF 読み込み、共有受け取り、手動検討、棋譜解析、保存／復元、ダークモードを確認
- [ ] release APK の署名・versionCode・versionName を確認
- [x] 再配布条件を確認できない水匠5の評価関数を含む旧バイナリを、評価関数を含まないやねうら王通常版に差し替える（作業ブランチ `feat/external-nnue-eval`）
- [x] 利用者が取得した評価関数を設定画面から選択・配置し、`EvalDir` を設定できるようにする（CI と実機での動作確認は別項目）
- [x] 評価関数設定後、初回起動・検討・解析とエラー表示を CI と実機で確認する（2026-09-27、実機での確認をユーザーが報告）
- [x] KIF / KIFU の拡張子と保存先の記憶を実機で確認する（2026-09-27、ユーザー報告）
- [x] Release の説明に評価関数の準備・設定、旧版からの移行、ソースへのリンクを追加する
- [x] 評価関数同梱の旧 v0.1.0 系 APK を Releases から取り下げる（2026-09-27、ユーザー報告。Releases に APK が残っていないことを確認）
- [ ] 旧評価関数同梱バイナリを Git 履歴から除去し、旧タグを整理する。現行ワークフローは `v0.1.0` タグを数えて `versionCode=2` を割り当てるため、タグを削除・付け替える場合は採番方式を同時に見直す
- [ ] v0.2.0-rc1 を作り実機確認
- [ ] v0.2.0 を公開

---

## v0.3.0 以降の候補

次の項目は重要だが、v0.2.0 の release blocker にはしない。

- Activity 再生成に耐える ViewModel / retained state への移行
- Foreground Service によるバックグラウンド棋譜解析
- KIF の開始日時、ヘッダコメント、終局結果を含む忠実な round-trip
- KI2 / CSA の入出力
- コメントの表示・編集
- 棋譜分岐
- 読み筋の盤上再生
- 常時検討モード
- 詰み探索
- 座標変換の共通化
- エンジンバイナリの再現可能なビルド手順とバージョン固定
- 大容量バイナリの管理方法の見直し
- Kotlin / Jetpack Compose の段階的導入（必要になった場合のみ）

特に棋譜分岐は `GameRecord` の線形構造を変えるため、独立した設計タスクとして扱う。

---

## 開発上の原則

- `dev` で小さな単位に変更し、CI が通る状態を保つ
- correctness / data loss / crash を機能追加より優先する
- JNI resource は `finalize()` に頼らず明示的に解放する
- disk I/O と process launch は UI thread で行わない
- stopped search と normally completed search を区別する
- UI 表示の都合で model / parser に Android dependency を持ち込まない
- JNI wrapper の package 変更は JNI symbol と必ず同時に行う
- 既知の不具合は [KNOWN_ISSUES.md](KNOWN_ISSUES.md) に追加し、解消したら履歴を残し続けず Git history に任せる
