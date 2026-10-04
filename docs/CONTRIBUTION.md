# AndroShogi 開発ガイド

開発者向けの構成・ビルド・テスト・リリース手順をまとめます。
利用者向けの使い方は [README](../README.md)、リリース範囲と残作業は
[ROADMAP.md](ROADMAP.md)、確認された不具合は [KNOWN_ISSUES.md](KNOWN_ISSUES.md) を参照してください。

## 開発の進め方

`dev` に小さな単位で変更し、CI と必要な実機確認を済ませてから PR で `main` に取り込みます。
公開済みの正式タグは付け替えません。機能変更と内部構造の変更は、可能な限り別のコミットにします。

- データ消失・クラッシュ・計算の誤りを、機能追加より優先する
- JNI のリソースは `finalize()` に頼らず、`cleanup()` を明示的に呼ぶ
- ディスク I/O とプロセス起動は UI スレッドで行わない
- データモデルや純粋な整形処理に Android View への依存を持ち込まない
- JNI wrapper のパッケージ変更は、C++ 側の JNI シンボルと同時に行う
- 未実装の機能は ROADMAP、再現した不具合は KNOWN_ISSUES に記録する。修正済みの詳細は Git 履歴に残す

## ビルド環境

現在の設定は `app/build.gradle`、`gradle/libs.versions.toml`、Gradle wrapper を正本とします。

| ツール・設定 | バージョン |
| --- | --- |
| JDK | 17（Gradle daemon も `gradle/gradle-daemon-jvm.properties` で固定） |
| Android SDK Platform / compileSdk / targetSdk | 34 |
| Android SDK Build Tools | 36.0.0 |
| 最低 Android API / minSdk | 29 |
| NDK | 27.2.12479018 |
| CMake | 3.18 以上（CI は SDK の3.22.1を使用） |
| Gradle | 9.8.0（`gradlew` が取得） |
| Android Gradle Plugin | 9.4.1 |

Android Studio ではリポジトリのルートを開きます。
`local.properties` に `sdk.dir` が必要です（Studio が自動生成します）。
通常のアプリビルドは同梱済みのエンジンを使うため、やねうら王の再ビルドは不要です。

```sh
./gradlew assembleDebug
```

対応 ABI は `arm64-v8a` と `x86_64` です。`-PtargetAbi` を指定しない場合は両方を含む APK になります。
ABI を限定する場合は、例えば次のように指定します。

```sh
./gradlew -PtargetAbi=arm64-v8a assembleDebug
```

通常版のアプリ ID は `org.androshogi`、debug 版は `org.androshogi.debug` です。
別アプリとして共存できますが、棋譜・評価関数・設定は共有しません。
ローカルビルドの既定値は `versionName=dev`、`versionCode=1` です。
公式ビルドは Actions が `-PappVersionName` と `-PappVersionCode` を渡します。

## テストと動作確認

```sh
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

JNI に依存しないモデル・整形処理は `app/src/test`、盤面・合法手・KIF パーサ・画面操作は
`app/src/androidTest` で確認します。後者には起動したエミュレーターまたは実機が必要です。

[Android CI](../.github/workflows/android.yml) は `main` / `dev` への push と PR で実行され、
debug APK のビルド・JVM Unit Test と、API 34 / x86_64 エミュレーターの Android Test を行います。
APK と Android Test のレポートは Actions のアーティファクトから取得できます。
エンジンと実際の評価関数の組み合わせは、実機でも確認してください。

リリース候補では次をまとめて確認し、未確認と失敗を区別して ROADMAP に記録します。

- KIF の読み込み・貼り付け・共有受け取り、Shift_JIS / UTF-8 保存、コメントと消費時間の再読み込み
- 分岐の追加・選択・本譜への復帰、棋譜切替、アプリ再起動後の分岐と解析結果の復元
- エンジンごとの評価関数の読み込み、切り替え後・再起動後の設定保持、手動検討と棋譜解析
- 棋譜解析の終了・中断・再解析、解析用 MultiPV と手動検討用 MultiPV の分離
- 解析コメントの出力オン／オフ、元コメントの保持、再読み込みした解析コメントへの追記
- 評価関数未設定・エンジン起動失敗・予期せぬ終了時の表示と、操作可能な状態への復帰
- 盤反転、長文コメント、メニューのスクロール、テーマ、最終 APK の署名・バージョン

## アプリの構成

Java の名前空間は `org.androshogi` です。局面管理と合法手生成は cshogi を JNI で呼び出し、
思考エンジンは別プロセスとして起動して USI で対話します。

| パッケージ | 主な責務・クラス |
| --- | --- |
| `shogi` | JNI wrapper。`Board`、`Move`、合法手の列挙 |
| `engine` | USI 通信・プロセス管理の `EngineSession`、自動解析の `AnalysisController`、`EngineKind`、評価関数の取り込み |
| `game` | 棋譜の `GameRecord` / `GameNode`、結果の `GameAnalysis` / `PositionAnalysis`、ゲーム状態の `GameSession` |
| `kifu` | KIF の読み書き、文字コード、指し手表記、解析コメントの整形 |
| `storage` | JSON の保存・読み込み、保存済み棋譜の一覧 |
| `settings` | 設定の保持、テーマ・エンジン設定 |
| `ui` | 盤面・読み筋・棋譜・グラフ、Activity、画面遷移・ダイアログ |

`AnalysisController` は進行と中断・終了の状態管理を行い、View を直接操作しません。
`GameSession` は棋譜・解析結果・保存 ID・作成日時をまとめます。永続化は storage 層の責務です。
`MainActivity` は画面とこれらのクラスを接続し、設定反映、保存／読み込みの非同期調停を行います。
保存／読み込みは単一 Executor、読み込みの世代番号、起動時の読み込み待ち状態で調停します。
全面的な MVVM 化やクラス追加は、具体的な保守上の効果を確認してから行います。

### 棋譜と解析結果の互換性

- `GameNode` の局面 ID・親子関係を木構造の正本とし、`GameRecord` の手数 API は選択中の経路を参照する
- 本譜の継続手と選択中の継続手を分離し、削除済みの局面 ID を再利用しない
- 解析結果は局面 ID に紐付ける。非同期結果は探索開始時の棋譜・局面 ID を照合してから保存する
- JSON format 2 は木全体・選択経路・現在位置・コメント・消費時間・解析結果を保存する
- v0.1.0 の format 1 は読み込める。次の保存で format 2 へ移行するため、旧アプリではそのデータを読めない
- バックグラウンド保存用コピーは、非選択の分岐も含めてライブの棋譜から独立させる
- 正常終了した探索結果は、時間や深さに関係なく最新結果を保存する。中断した局面の途中結果で既存結果を上書きしない

KIF は選択中の一本の手順だけを入出力します。開始局面のコメントや終局結果を含む完全な情報保持は今後の課題です。
解析コメントは出力専用で、既存コメントの検出・置換・重複除去は行いません。
評価値は全候補で先手基準とし、詰みは通常の数値評価と分けて表記します。
エンジン名・思考時間は現在の解析結果に保存されていないため、出力には含めません。

### 表示と画像

Material3 の DayNight テーマを使用します。画面部品の色はテーマ属性で指定し、ハードコードを避けてください。
盤・駒・矢印の色はテーマに依存しません。

駒画像は `app/src/python/koma.py` で生成し、フォントに同ディレクトリの Yuji Mai を使います。

```sh
cd app/src/python
pip install pillow aggdraw opencv-python numpy
python koma.py
```

## 思考エンジンの再ビルド

現行の同梱バイナリは [yaneurao/YaneuraOu](https://github.com/yaneurao/YaneuraOu) の
サブモジュール `third_party/YaneuraOu` からビルドしたものです。
固定コミットは `c1b80eaa09fe13d5f12b1599d1ae4d53c224de30`（9.80git）です。
サブモジュールを更新する場合は、パッチの適用と各構造の評価値を再確認してください。

```sh
git submodule update --init --recursive
ANDROID_SDK_ROOT=/path/to/Android/sdk bash tools/build_yaneuraou.sh
```

NDK は既定で `27.2.12479018`、対象 API は `android-29` です。
変更する場合は `NDK_VERSION` と `APP_PLATFORM` を環境変数で指定します。
スクリプトは Python 3 と `ndk-build` を使い、次の3構造を両 ABI 向けに生成します。

| NNUE 構造 | EngineKind の初期 FV_SCALE |
| --- | --- |
| `halfkp_256x2_32_32` | 16 |
| `halfkp_512x2_8_64` | 40 |
| `halfkp_768x2_16_64` | 40 |

構造ヘッダを生成し、`patches/yaneuraou/0001-fix-android-neon.patch` を適用してビルドします。
ビルド終了時にパッチと生成ヘッダを戻します。構造ごとの作業出力は `build/yaneuraou/`、
アプリへコピーする実行ファイルは次の場所です。

```text
app/src/main/jniLibs/<abi>/libYaneuraOu_NNUE_<NNUE構造>_<abi>.so
```

Android 用パッチの `-DUSE_NEON` は値1です。現行ソースで通常の NEON 経路に `USE_NEON=8` を指定した際、
重みの配置と計算処理の不整合による評価値異常が発生したため、この設定を維持します。
再ビルド後は同じ評価関数・FV_SCALE・局面で評価値を確認してください。
評価関数は埋め込まず、利用者の `nn.bin` を `EvalDir` で指定します。

エンジンは実行ファイルですが、`nativeLibraryDir` 配下へ展開して起動するため `.so` の名前で配置します。
`extractNativeLibs` と `useLegacyPackaging` はこの起動方式に必要です。
`EngineKind.executableName()` とファイル名は一致させてください。

評価関数の取り込み先は、256構造では従来互換の `filesDir/eval/nn.bin`、
512・768構造では `filesDir/eval/<NNUE構造>/nn.bin` です。
一時ファイルへコピーした後に置き換え、取り込み失敗時は既存ファイルを保持します。
構造や評価関数の変更はメイン画面復帰時にエンジンを再起動して反映します。

## リリース手順

[Release workflow](../.github/workflows/release.yml) が ABI ごとの署名済み APK を作成します。
タグによる公開は、そのコミットが `main` に含まれる場合だけ許可されます。
`v0.2.0-rc1` などの RC は pre-release、`v0.2.0` は正式 Release になります。

1. ROADMAP のリリース前確認を済ませ、PR で `dev` を `main` に取り込みます。
2. 最終コミットの Android CI を確認します。
3. `main` 上に RC タグを作成し、Release workflow と実機確認を行います。
4. 修正があれば同じ手順で取り込み、新しい RC タグを作成します。
5. 確認したコミットに正式タグを作成し、公開後の APK と Release の説明を確認します。

タグ作成の例です。`main` にリリース内容が取り込まれた後に実行します。

```sh
git switch main
git pull --ff-only origin main
git tag v0.2.0-rc1
git push origin v0.2.0-rc1
```

正式公開時は同じ手順で `v0.2.0` を使います。公開済みのタグを削除・付け替えず、正式版は昇順に作成します。
`versionCode` は正式タグ `vX.Y.Z` のバージョン順に1から採番し、`v0.1.0=1`、`v0.2.0=2` です。
RC は対応する正式版と同じ番号、手動ビルドは次の正式版の番号を使います。

### 公開前の文書確認

- README の機能・設定・対応形式を、公開するコミットに合わせる。公開時にはリリース準備中の記載を更新する
- THIRD_PARTY_NOTICES の出典・ファイル名と、アプリ内の `res/raw/third_party_notices.txt` を同期する
- Release workflow の配布説明を README と合わせ、同梱エンジンのビルド情報はこの CONTRIBUTION へリンクする
- 最終バイナリのソースが取得できること、評価関数を同梱していないことを確認する

### 手動ビルドと署名

タグを作らずに端末で試す場合は、Actions の `Release` を対象ブランチで手動実行します。
日本時間の日付とコミット先頭6桁を使う `build-YYYYMMDD-<sha6>` の Draft Release に、通常版と debug 版の APK を添付します。
任意のブランチから実行でき、Draft は書き込み権限のあるアカウントで取得します。
同じ日・同じコミットの再実行は同じ Draft を置き換えます。ソースへのリンクはコミットを使います。

正式鍵を使うローカルビルドでは `ANDROID_RELEASE_KEYSTORE` と `ANDROID_RELEASE_PASSWORD` を設定します。
鍵やパスワードはコミットしません。CI は Secrets の `KEY_BASE64`・`KEY_SHA256`・`KEY_PASS` を使用します。

```sh
./gradlew -PtargetAbi=arm64-v8a -PappVersionName=v0.2.0 -PappVersionCode=2 assembleRelease
./gradlew -PtargetAbi=x86_64 -PappVersionName=v0.2.0 -PappVersionCode=2 assembleRelease
```

配布ファイル名は `androshogi-<tag>-<abi>.apk`、手動ビルドの debug 版は末尾が `-debug.apk` です。
通常版は正式鍵、debug 版はデバッグ鍵で署名します。現在はどちらも minify を無効にしています。
CI は APK に含まれる ABI、アプリ ID、versionName / versionCode、通常版の署名証明書を確認します。

公開済み v0.1.0 と v0.2.0 のアプリ ID はともに `org.androshogi` です。
同じ正式鍵と増加する versionCode により通常の上書き更新を行います。
それ以前のテスト版はアプリ ID・署名・versionCode が異なる場合があります。
上書きできない場合は必要な棋譜を退避してから旧版を削除してください。

## 保守候補

以下は v0.2.0 公開の必須作業ではなく、必要性と効果を確認してから着手します。

- AndroidX の更新、compileSdk / targetSdk の引き上げ
- R8 / minify の導入と JNI・署名済み APK の動作確認
- ViewModel 等による Activity 再生成への対応、`configChanges` 依存の見直し
- Kotlin / Compose の導入、UI と状態管理の追加分離
- やねうら王のビルド再現性・バイナリのチェックサム・大容量ファイル管理の整備
- 座標変換の共通化

v0.1.0 までの設計変更・検証ログの詳細は Git 履歴を参照してください。
