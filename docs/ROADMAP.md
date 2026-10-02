# AndroShogi 開発ロードマップ

この文書は、公開済みの実装状況と次のリリースに向けた作業計画を管理します。
旧リポジトリでの作業経緯は、その履歴と Release / Pull Request を参照してください。
現在確認されている不具合は [KNOWN_ISSUES.md](KNOWN_ISSUES.md) に分離します。

## 現在地

**2026-09-30 に v0.1.0（versionCode 1）を正式公開しました。**
リポジトリは public で、[v0.1.0 Release](https://github.com/tatsy/androshogi/releases/tag/v0.1.0) から
`arm64-v8a` / `x86_64` の署名済み APK を配布しています。
v0.2.0 に向けた開発は、`main` を起点に作成した `dev` ブランチで進めています。
現在のコードには、将棋の棋譜をやねうら王 NNUE で検討するための基本機能があります。
評価関数は APK に同梱せず、利用者が設定画面から `nn.bin` を選択します。

- 盤面操作、合法手、成り／不成、持駒、盤反転
- KIF の読み込み／保存（各手のコメント・消費時間を含む）、BOD 初期局面、KIF のコピー／共有
- 手動検討と MultiPV 表示
- 棋譜全体の自動解析、キャンセル、進捗表示
- 手数ごとの解析結果、棋譜リスト、勝率グラフ
- 棋譜と解析結果の JSON 保存／復元、棋譜一覧
- エンジン設定、表示設定、ダークモード
- JVM unit test / Android instrumentation test / GitHub Actions
- GitHub Releases からの ABI 別 APK 配布

旧リポジトリで列挙した既知不具合 #1–#15 は修正済みです。
v0.1.0 の Release ワークフローと JVM unit test / API 34 instrumentation test は成功し、
最終調整後の実機動作に問題がないことをユーザーが確認しました。
個別の異常系・境界条件について確認記録が残っていないものは、次の開発で回帰確認します。

**2026-10-01 時点の `dev` では、基本的な分岐操作とコメント閲覧も実装済みです。**
分岐の追加・選択・本譜への復帰、分岐点の複数矢印、局面ごとのコメント表示に対応しています。
プレイヤー名の先後記号とメニューの整理も反映しました。
普段の検討に必要な基本機能が揃い、残る主な実装は解析結果の KIF コメント出力です。
実装済みの項目でも、追加 UI や境界条件の実機確認が残るものは以下で区別します。

---

## v0.2.0 実装状況とリリース準備

v0.1.0 の基本機能と保存済みデータの互換性を維持しながら、小さな単位で改善します。
分岐の内部構造への移行と基本 UI、コメント閲覧は完了しました。
v0.2.0 の主要範囲は、基本的な分岐操作、コメント閲覧、解析結果を含む KIF 出力と回帰確認です。
分岐の高度な編集・外部形式での保存やその他の新機能は、後段の候補として残します。

### 作業基盤

- [x] 公開済み v0.1.0 と現在の `main` を確認する
- [x] 現在の `main` から `dev` を作成する
- [x] v0.1.0 の公開完了と v0.2.0 の準備方針を ROADMAP に反映する
- [x] v0.2.0 の主要範囲を基本的な分岐操作・コメント閲覧・解析結果の KIF 出力に整理する
- [x] workflow dispatch の APK 配布を未公開の Draft Release に変更する
  - 手動ビルドの識別名は `build-YYYYMMDD-<コミット先頭6桁>`。日付は日本時間
  - 正式版・RC のタグによる公開と、手動ビルドの Draft 運用を分ける

開発は `dev` に小さなコミットを積み、CI と必要な実機確認を済ませてから PR で `main` に取り込みます。
公開済みの `v0.1.0` タグは固定し、以後の修正は新しいコミットとタグで配布します。
正式版の `versionCode` は既存の Release ワークフローがタグ順に採番します。
現在の正式版タグ構成では、次を `v0.2.0` とした場合は `versionCode 2` になります。
RC は対応する正式版と同じ番号を使います。

### 分岐機能の段階的な導入

- [x] 棋譜を局面 ID・親子関係・本譜の継続手を持つ木構造へ移行する
  - `GameNode` を正本とし、`GameRecord` の既存の手数 API は選択中の経路を参照する
  - 本譜の継続手と選択中の継続手を分離し、削除済み局面の ID は再利用しない
- [x] 解析結果を局面 ID に紐付け、非同期結果の保存前に探索開始時の ID を照合する
  - 画面は従来どおり手数で参照できる。棋譜切替時も画面が保持する `GameAnalysis` の参照を維持する
- [x] 木全体・選択経路・解析結果を JSON format 2 で保存し、v0.1.0 の format 1 を読み込む
  - 手順・消費時間・コメント・現在位置・対局者・解析結果・保存 ID・日時を引き継ぐ
  - バックグラウンド保存用コピーは、非選択の分岐を含めてライブの棋譜から独立させる
- [x] 別の手を分岐として追加し、分岐選択と本譜への復帰を行う UI を追加する
  - 最初は棋譜リスト・グラフ・自動解析を選択中の経路に限定する
  - 経路切替時は同じ手数でも局面が違うため、初期局面から盤面を再構築する
- [x] 分岐点の次の手を、選択中の継続手は青、ほかの継続手は緑の複数矢印で表示する
- [x] 「本譜に戻る」で、本譜の最終手ではなく最後の共通局面へ戻る
- 分岐の削除・本譜への昇格・読み筋からの追加・分岐を保持する外部形式の入出力は後続候補とする

途中で別の手を指すと、元の手順と解析結果を残して分岐を追加します。同じ手は既存の分岐を再利用します。
棋譜リストの「分岐あり」の局面に戻り、メニューの「分岐を選択」で次の手を選びます。
分岐点では、選択中の継続手を青、ほかの継続手を緑の矢印で表示します。
「本譜に戻る」は本譜と分岐の最後の共通局面へ戻ります（分岐点より前にいる場合は現在位置を維持）。
分岐と解析結果は自動保存されます。KIF の読み込みは一本の手順、出力は選択中の手順だけを扱います。
分岐の基本操作は実機で問題なしとの報告があります。
複数矢印と分岐元への復帰は CI の画面テストで確認済みですが、個別の実機確認は未記録です。
format 2 は旧アプリでは読めません。新アプリは format 1 を読み込み、次の保存時に format 2 へ移行します。

### コメント表示と KIF 入出力

- [x] 各手のコメントと消費時間を KIF から読み込み、KIF 保存時に出力する
- [x] UTF-8 / Shift_JIS を通したコメント・消費時間の基本的な往復を CI で回帰確認する
- [x] 下部タブの左端に「コメント」を追加し、表示局面のコメントを閲覧できるようにする
  - 手の移動・分岐選択・棋譜切替に追従し、複数行・長文のスクロール・選択とコピーに対応する
  - コメントがない局面には空状態を表示する。初期選択は従来どおり「読み筋」
- [x] コメントの複数行表示・空状態・長文スクロール・手の移動・分岐と棋譜の切替を画面テストで確認する
- [ ] コメント表示と長文スクロールを実機で確認する
- [ ] 複数行コメント・消費時間を含む KIF の保存／再読み込みを実機で確認する
- [ ] KIF 保存時に評価値・読み筋・解析条件をコメントとして出力し、再保存時の重複を防ぐ
  - 元のコメントを保持し、KIF を再読み込みしたときに解析コメントをコメントタブで閲覧できることを確認する

まずは各手に保存されたコメントの閲覧に限定し、編集は別の変更で検討します。
KIF は互換性を優先して選択中の一本の手順を出力し、分岐の入出力は後回しにします。
開始局面に付く KIF ヘッダコメントの取り込みは、後段の忠実な round-trip の検討項目に残します。

### 画面と操作の調整

- [x] 下部の4タブを「コメント / 読み筋 / 棋譜 / グラフ」の順とし、画面幅に合わせて配置する
- [x] 先手名に `☗`、後手名に `☖` を付け、盤反転時も名前と記号を一緒に入れ替える
- [x] ハンバーガーメニューを区切り線付きのグループに整理する
  - 棋譜管理 → KIF 入出力 → 局面操作 → 棋譜解析 → 設定
  - KIF 入出力は、開く → 貼り付け → 保存 → コピー → 共有の順
- [x] 既存の画面テストを、画面外のメニュー項目へスクロールしてから操作するように更新する
- [ ] プレイヤー名の先後記号、盤反転、メニューの表示順とスクロールを実機で確認する

### エンジンと読み筋

- [x] 同梱する HalfKP 256×2-32-32 / 512×2-8-64 / 768×2-16-64 の選択を設定に追加する
  - 評価関数と FV_SCALE は構造ごとに保持し、従来のファイル・設定は256構造用として引き継ぐ
  - 構造の変更後、メイン画面へ戻ったときに実行中の検討・解析を終了してエンジンを再起動する
  - 512・768構造の FV_SCALE 初期値は40。評価関数の配布元の案内に合わせて調整する
- [ ] Háo・振電3・AobaNNUE それぞれの読み込み・検討・切替・再起動後の設定保持を実機で確認する
- [x] エンジンの `ConsiderationMode` を `true` に設定する
- [x] 各候補の読み筋表示を最大5手から最大10手に増やす
  - エンジンが返した PV が短い場合は、その手数まで表示する
- [ ] 手動検討・棋譜解析での読み筋表示を実機で確認する

### 回帰確認と保守

- [ ] エンジンを起動できない場合のメッセージと、操作可能な状態へ戻ることを実機で確認する
  - 予期せぬプロセス終了時のメッセージは v0.1.0 準備中に確認済み。起動失敗とは別に記録する
- [ ] 長時間・深い探索の結果がある局面を短時間で再解析し、正常終了した最新結果で上書きされることを確認する
  - 中断時に既存結果を上書きしないことは確認済み。正常終了時の比較条件を明示して確認する
- [ ] 保存／復元の回帰確認で、起動直後の操作、棋譜切替、解析中断後の再起動を確認する
  - save の順序保証、古い load result の棄却、startup load 前の誤保存防止を維持する
- [x] AGP / Gradle を更新し、ローカル・CI・手動 Release ビルドを確認する
  - 現在の `dev` は AGP `9.4.1` / Gradle `9.8.0` / JDK `17` / Build Tools `36.0.0`
  - Gradle daemon の JVM 条件は `gradle/gradle-daemon-jvm.properties` で JDK 17 に固定する
  - ローカルのビルド・Java テスト・Android テスト成功と、更新後 APK の実機での主要動作をユーザーが確認（2026-10-01）
- [ ] AndroidX 更新や SDK レベル変更の必要性を個別に判断する
  - 現在は `minSdk 29`、`compileSdk 34`、`targetSdk 34`。Build Tools 更新と API 35 以降への SDK レベル引き上げは別に扱う
- [ ] 必要性を確認したうえで R8 / minify を検討する
  - v0.1.0 は無効のまま公開。導入する場合は JNI 呼び出しと署名済み APK の動作を確認する

これらは未修正の不具合を意味しません。既知不具合を確認した場合は
[KNOWN_ISSUES.md](KNOWN_ISSUES.md) に再現手順を記録します。

### v0.2.0 リリースまでの確認

- [x] 実装コミット `3190272` の build / JVM unit test / API 34 instrumentation test が成功する
  - [Android CI](https://github.com/tatsy/androshogi/actions/runs/36867122841)（2026-10-01）。分岐・コメント表示・メニューのスクロール操作を含む
- [x] 新しいビルド環境で、workflow dispatch の署名済み APK 作成が成功する
  - [Release](https://github.com/tatsy/androshogi/actions/runs/36852442048)（2026-10-01、`f5c6fe0`）。コメント UI 等の直近の変更を含む最終版 APK の確認とは分ける
- [ ] 解析結果の KIF コメント出力を実装し、保存／再読み込みを確認する
- [ ] 追加 UI と分岐・保存／復元について、上記の実機確認と回帰確認をまとめて実施する
- [ ] `dev` を PR で `main` に取り込み、最終ビルドの署名・version 情報を確認する
- [ ] `v0.2.0-rc1` を実機で確認し、`v0.2.0`（versionCode 2）を公開する

---

## v0.1.0 実装・リリース記録

以下は v0.1.0 で実施した整理と、その設計上の判断です。

### 1. アプリの identity と配布基盤を確定する

v0.1.0 の公開にあたり、アプリ ID、署名、採番を確定しました。

- [x] 正式なアプリ identity を反映する
  - [x] 表示名は `AndroShogi` に確定
  - [x] `applicationId` は `org.androshogi` に確定
  - [x] Java / Android の `namespace` は `org.androshogi` に確定
  - [x] Manifest の Application / Activity 参照を現在の package 構造に追従
- [x] 正式な release keystore を作成し、v0.1.0 以降は同じ鍵を継続利用する
- [x] GitHub Actions の release build を正式鍵で署名する
- [x] `versionCode` を正式版タグの順番で単調増加させる（`v0.1.0=1`、`v0.2.0=2`。RC は正式版と同じ番号）
- [x] Release ワークフローで release APK の署名と version 情報を確認する

R8 / minify は v0.1.0 では無効とし、必要性の検討は v0.2.0 の保守項目へ移しました。

旧テスト版の `org.androshogi` が versionCode 2 で端末に入っている場合、
versionCode 1 の新しい v0.1.0 は上書きできない。必要なデータを退避してから旧版をアンインストールする。

### 2. Java パッケージ構造を整理する

Java / Android の namespace を `org.androshogi` に統一し、UI、棋譜、保存、エンジン、JNI wrapper の責務が package 構造から分かるように整理済み。
v0.1.0 公開時の主な構成は次のとおり。

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
│   ├── AnalysisController
│   ├── EngineSession
│   ├── EngineInfo
│   └── EngineUpdateListener
├── game
│   ├── GameSession
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
- [x] 実機で起動・棋譜読み込み・検討を確認する（最終調整後、ユーザー報告）

JNI wrapper の package 変更では、`jni.cpp` の関数名にも完全修飾クラス名が埋め込まれる。
現在は `Java_org_androshogi_shogi_Board_...` のような symbol を使用しているため、
今後 JNI wrapper の package を変更する場合も Java 側の package と JNI symbol を同じコミットで更新する。

過度な細分化はしない。まず「UI / engine / game / kifu / storage / settings / shogi」の境界が見えることを優先する。

### 3. `MainActivity` の責務を減らす

分割前の `MainActivity` は View の初期化や画面遷移だけでなく、
エンジン制御、自動解析ループ、棋譜と解析結果の状態、保存 ID、非同期 save / load、
KIF の共有入出力まで同時に管理していました。
単にファイルが長いことよりも、複数の状態機械が同じ Activity の field と callback で結合していることが保守上の問題になる。

v0.1.0 では全面的な MVVM 化や大規模な architecture 変更は行わず、
**独立した状態管理・進行管理を外へ出しました**。
UI の表示や Android lifecycle に直接関係する処理は `MainActivity` に残しています。

#### 3.1 自動解析ループを `AnalysisController` へ分離する

最初に、`MainActivity.AnalysisRun` が担当していた自動解析の状態機械を独立させました。
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
v0.1.0 では新しい `EngineController` を先に導入しない。
`AnalysisController` 分離後も `MainActivity` から `EngineSession` を直接扱う構造で十分かを改めて判断する。

#### 3.2 ゲーム状態を `GameSession` にまとめる

次に、ばらばらに保持していた棋譜と解析結果、その保存上の identity を1つの状態として扱えるようにしました。

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
- [x] 既存の `GameStore` は低レベルの保存 API として維持する
- [x] クラス追加の効果が小さいため、v0.1.0 では `MainActivity` に残す

現状の save / load は単一の `ExecutorService`、load 世代番号、起動時の pending 状態で調停している。
これらは Activity の終了判定と UI callback にも接しているため、v0.1.0 では分離せず維持する。
保存順序・古い load の棄却・起動時の誤保存・一覧表示前の保存は引き続き回帰確認する。

この段階は `AnalysisController` と `GameSession` より優先度を下げる。
クラス数を増やすこと自体を目的にしない。

#### 3.4 `MainActivity` に残す責務

v0.1.0 公開時の `MainActivity` は、Android UI の入口として次を中心にする。

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

#### 3.5 v0.1.0 では後回しにするもの

次は今回の `MainActivity` 整理の必須条件にしない。

- ViewModel / retained state への全面移行
- `configChanges` の撤廃
- Foreground Service によるバックグラウンド解析
- Kotlin / Jetpack Compose への移行
- `EngineSession` を包む専用 controller の新設
- KIF 入出力 UI の大規模分割

KIF の file I/O / charset 処理は Storage Access Framework 対応とともに整備済みです。
入出力 UI の追加分割は、その後の機能追加に応じて必要性を判断します。
現在の `configChanges` による設定変更対応は暫定策として維持し、
Controller / session 分離後に Activity lifecycle から engine state をさらに切り離す必要があるか判断する。

#### 実装順

- [x] `AnalysisController` のテストと切り出し
- [x] `GameSession` の必要 API を整理し、テストとともに導入
- [x] save / load 調停の分離が有効かを再評価し、必要なら切り出す
- [x] `MainActivity` の不要 field / helper / callback を整理する
- [x] CI と実機で、手動検討・自動解析・キャンセル・棋譜切替・保存／復元を回帰確認する
  - 自動解析・キャンセルは分割後の実機動作で問題なし（2026-09-24）。最終調整後の動作確認もユーザーが報告（2026-09-30）

各段階は小さなコミットに分け、機能変更と構造変更を可能な限り混ぜない。

### 4. エンジンと解析の公開前チェック

- [x] 起動から `readyok` までの実機動作と所要時間を確認する（2026-09-23、初回 `readyok` まで 1,088 ms。実機での1回の測定値）
- [x] 予期せぬ終了時: debug APK の実機確認で、ポップアップと Logcat (`E/EngineSession`) の両方に「エンジンが予期せず終了しました」を確認（2026-09-23）
- 起動失敗時の個別の実機確認は、v0.2.0 の回帰確認項目に引き継ぐ
- [x] 長い棋譜解析でメモリが増え続けないことを確認する（2026-09-24、実機で166手を1手3秒で解析。提示された約5分間のエンジン TOTAL PSS は342,155–343,034 KBで持続増加なし。実機での1回の確認）
- [x] Activity 終了後にやねうら王プロセスが残らないことを確認する（2026-09-24、解析後にアプリを終了し、`adb shell ps -A -o PID,PPID,ARGS` で `YaneuraOu_NNUE` を含むプロセスが残っていないことを実機確認）
- [x] 評価関数非同梱版の `FV_SCALE` 既定値 `16` を実機ログで確認する（2026-09-26）。評価関数に合わせて設定できるようにする
- 中断された探索結果は保存せず、正常終了した探索結果は時間・depth に関係なく最新結果で上書きする方針を維持する
  - [x] 解析中断時に既存結果の上書きが起こらないことを実機で確認（2026-09-24）
  - 正常終了時の探索時間・depth の比較条件を明示した確認は、v0.2.0 の回帰確認項目に引き継ぐ

### 5. v0.1.0 に含む機能

v0.1.0 では大きなデータモデル変更を避け、既存機能につながるものを優先する。

第一候補:

- [x] Storage Access Framework で KIF ファイルを選んで開く
- [x] KIF ファイルとして保存する（.kif は Shift_JIS、.kifu は UTF-8）
- [x] Shift_JIS / UTF-8 の扱いを明示し、round-trip test を追加する

v0.1.0 に含める棋譜形式の追加はここまでとし、KI2 / CSA、棋譜分岐などは後回しにする。

### 6. v0.1.0 リリース判定

CI / Release は GitHub Actions の成功結果、実機の最終確認はユーザー報告に基づきます。

- [x] JVM unit test がすべて成功
- [x] API 34 emulator の instrumentation test が成功
- [x] arm64-v8a 実機で smoke test
- [x] 起動、盤面操作、KIF 読み込み、共有受け取り、手動検討、棋譜解析、保存／復元、ダークモードを確認
- [x] release APK の署名・versionCode・versionName を確認
- [x] 再配布条件を確認できない水匠5の評価関数を含む旧バイナリを、評価関数を含まないやねうら王通常版に差し替える
- [x] 利用者が取得した評価関数を設定画面から選択・配置し、`EvalDir` を設定できるようにする（CI と実機での動作確認は別項目）
- [x] 評価関数設定後、初回起動・検討・解析とエラー表示を CI と実機で確認する（2026-09-27、実機での確認をユーザーが報告）
- [x] KIF / KIFU の拡張子と保存先の記憶を実機で確認する（2026-09-27、ユーザー報告）
- [x] Release の説明に評価関数の準備・設定、旧テスト版からの移行、ソースへのリンクを追加する
- [x] orphan の単一開始コミットに切り替え、旧タグを持ち込まず `v0.1.0=1` から採番する
- [x] 新リポジトリにリリース署名用の Actions Secrets を設定する
- [x] `v0.1.0-rc1` を作り実機確認
- [x] `v0.1.0` を公開（2026-09-30、両 ABI の APK を掲載）
- [x] リポジトリを public に変更し、ソースと Release を一般公開する

---

## 今後の候補（v0.2.0 の主要範囲外）

以下は今後の機能追加・構造改善の候補です。現在の v0.2.0 の主要範囲には含めず、
利用上の効果と変更範囲を検討してリリースごとに選びます。

- Activity 再生成に耐える ViewModel / retained state への移行
- Foreground Service によるバックグラウンド棋譜解析
- KIF の開始日時、ヘッダコメント、終局結果を含む忠実な round-trip
- KI2 / CSA の入出力
- コメントの編集（各手のコメント表示は実装済み）
- 分岐の編集（削除・本譜への昇格・読み筋からの追加）と外部形式での保存
- 読み筋の盤上再生
- 常時検討モード
- 詰み探索
- 座標変換の共通化
- エンジンバイナリの再現可能なビルド手順とバージョン固定
- 大容量バイナリの管理方法の見直し
- Kotlin / Jetpack Compose の段階的導入（必要になった場合のみ）

基本的な分岐機能と木構造への移行は実装済みです。
高度な分岐編集や外部形式での保存は、局面 ID・解析結果・保存済みデータの互換性を維持する設計を個別に検討します。

---

## 開発上の原則

- `dev` で小さな単位に変更し、CI が通る状態を保つ。確認後は PR で `main` に取り込む
- correctness / data loss / crash を機能追加より優先する
- JNI resource は `finalize()` に頼らず明示的に解放する
- disk I/O と process launch は UI thread で行わない
- stopped search と normally completed search を区別する
- UI 表示の都合で model / parser に Android dependency を持ち込まない
- JNI wrapper の package 変更は JNI symbol と必ず同時に行う
- 既知の不具合は [KNOWN_ISSUES.md](KNOWN_ISSUES.md) に追加し、解消したら履歴を残し続けず Git history に任せる
