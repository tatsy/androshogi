# AndroShogi

将棋の棋譜を AI（やねうら王 NNUE）で検討するための Android アプリです。
盤面を操作しながら候補手と読み筋を確認したり、貼り付けた KIF 棋譜を
手順に沿って検討したりすることを目的にしています（「将棋処」のような使い方を想定）。

開発の現状・課題・今後の計画は [`docs/ROADMAP.md`](docs/ROADMAP.md) を、
未修正の不具合は [`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md) を参照してください。

## 主な機能

- 盤面の表示と操作（合法手のみ、成り／不成の選択、持ち駒の打ち込み）
- 手を戻す／進める、開始局面・終了局面へのジャンプ、盤の上下反転
- クリップボードからの KIF 棋譜の読み込み（平手・駒落ち）。端末からの
  .kif/.kifu ファイル選択や、他アプリから共有された KIF テキスト／ファイル
  （Shift_JIS／UTF-8）にも対応
- 表示中の棋譜を KIF としてコピー・共有、またはファイルに保存（.kif は Shift_JIS、.kifu は UTF-8）。
  初回保存時に Documents/AndroShogi などのフォルダーを作成・選択すると、次回からその場所を保存画面の初期位置に指定する（端末のファイル選択画面によっては反映されない場合がある）
- やねうら王による検討。候補手を盤上の矢印で、読み筋をテキストで表示
- 棋譜全体の自動解析。結果は局面ごとに保持し、手を戻す／進めると再表示
- 「新規」で初期局面に戻す
- 設定: 思考時間、候補手の数、スレッド数、ハッシュサイズ、FV_SCALE、評価値の視点。
  アプリ内でライセンスを表示

## 構成

局面の管理と合法手生成は cshogi を JNI 経由で呼び出し、思考はやねうら王を
別プロセスとして起動して USI プロトコルで対話します。Android では
`nativeLibraryDir` 配下のファイルしか実行できないため、エンジンは
`libYaneuraOu_NNUE_<abi>.so` という名前で `jniLibs` に置いています
（`extractNativeLibs` と `useLegacyPackaging` を有効にしているのはそのためです）。

## ビルド

### 必要なもの:

| ツール | バージョン |
| --- | --- |
| JDK | 17 |
| Android SDK Platform | 34 |
| Android SDK Build-Tools | 34.0.0 |
| NDK | 27.2.12479018 |
| CMake | 3.18 以上（SDK 同梱の 3.22.1 で可） |
| Gradle | 8.9（`gradlew` が取得） |
| Android Gradle Plugin | 8.7.2 |

### コマンド

```sh
./gradlew assembleDebug
```

Android Studio で開く場合は `File > Open` でリポジトリのルートを選択してください。
`local.properties` に `sdk.dir` が必要です（Studio が自動生成します）。

対応 ABI は `arm64-v8a`（実機）と `x86_64`（エミュレータ）です。
minSdk は 29 です。

## テスト

```sh
./gradlew testDebugUnitTest          # JVM 単体テスト
./gradlew connectedDebugAndroidTest  # エミュレータ／実機での計測テスト
```

局面クラスが JNI に依存するため、`KifParser` などのテストは現状 `androidTest` 側に
置く必要があります（`docs/ROADMAP.md` Phase 1 参照）。

## テーマ

Material3 の DayNight テーマを使い、部品の色はテーマ属性（`?attr/colorSurfaceVariant` など）
で指定しています。盤・駒・矢印はテーマに関係なく同じ色です。設定 → 表示 → テーマで
「システムに従う／ライト／ダーク」を選べます（`AppSettings.applyTheme`）。
新しい画面部品を足すときは色をハードコードせず、テーマ属性を使ってください。

## ライセンス

GNU General Public License v3.0（[`LICENSE`](LICENSE)）。
同梱・派生している第三者ソフトウェアについては
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) を参照してください。
