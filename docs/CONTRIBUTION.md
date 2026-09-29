# How to contribute

開発の現状・課題・今後の計画は [`docs/ROADMAP.md`](docs/ROADMAP.md) を、
未修正の不具合は [`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md) を参照してください。

## リリース

ビルド済みの APK は [Releases](../../releases) からダウンロードできます。
エンジンは ABI あたり約 1 MB です。APK は ABI ごとに分割しています。

| ファイル | 対象 |
| --- | --- |
| `androshogi-<tag>-arm64-v8a.apk` | 実機 |
| `androshogi-<tag>-x86_64.apk` | エミュレータ、x86_64 の端末 |

**リリースは `main` からのみ作成されます。** `v` で始まるタグを push すると
`.github/workflows/release.yml` が動きますが、そのタグの commit が `main` に
含まれていなければビルド前に失敗します。手順は次のとおりです。

```sh
git checkout main
git merge --ff-only dev     # リリースしたい内容を main に入れる
git push origin main
git tag v0.2.0
git push origin v0.2.0      # ここで Release が作られる
```

`v0.2.0-rc1` のようにハイフンを含むタグは pre-release として公開されます。

旧 v0.1.0 系の評価関数同梱 APK は 2026-09-27 に
[Releases](../../releases) から取り下げました。以前ダウンロードされたコピーや
Git 履歴からは消えないため、旧バイナリを含む履歴とタグは別途整理します。
現行の Release ワークフローは正式版タグを数えて `versionCode` を割り当てるため、
履歴整理で `v0.1.0` タグを削除・付け替える場合は、その採番も同時に見直す必要があります。

旧 v0.1.0 のアプリ ID `com.example.androshogi` と v0.2.0 の
`org.androshogi` は異なります。アプリ内の棋譜・解析結果・設定は自動移行されないので、
旧版を削除する前に必要な棋譜をコピーまたは共有してください。
旧 debug APK が `org.androshogi` でインストールされている場合は、正式鍵で
署名した v0.2.0 を上書きできません。必要なデータを退避してから
旧 debug APK をアンインストールしてください。

`versionCode` は正式版タグ（`vX.Y.Z`）のバージョン順に 1 から採番します。
`v0.1.0` は 1、`v0.2.0` は 2 です。RC は対応する正式版と同じ番号、
手動ビルドはその時点での次の正式版の番号を使います。正式版のタグは
過去のバージョンを後から追加・削除せず、昇順に作成してください。
Actions が値を計算して release / debug APK に渡し、APK 内の値も確認します。

### 手元の動作確認用のビルド

タグを打たずに端末で試したいときは、Actions から `Release` ワークフローを
手動実行（workflow dispatch）してください。commit の先頭 6 桁を使って
`build-abc123` というタグの pre-release が作られ、APK が同じように並びます。
`main` しばりは掛からないので、作業中のブランチからでも作れます。同じ commit で
実行し直すと、前のビルドの release は置き換えられます。

設定画面に表示されるバージョンは、リリースのタグと同じ文字列です
（タグ付きなら `v0.2.0`、手動ビルドなら `build-abc123`）。

ABI ごとの APK は「1 ABI だけを対象にしたビルド」を ABI ごとに行って作ります
（AGP が `splits.abi` と `ndk.abiFilters` の併用を禁じているため）。CI では
`verify`（main 判定）→ `build`（ABI ごとの matrix ジョブで並列）→ `publish`
（アーティファクトを集めて Release 作成）の 3 ジョブで動きます。
手元で同じものを作るには次のようにします。

```sh
./gradlew -PtargetAbi=arm64-v8a -PappVersionName=v0.2.0 -PappVersionCode=2 assembleRelease
./gradlew -PtargetAbi=x86_64 -PappVersionName=v0.2.0 -PappVersionCode=2 assembleRelease
```

`-PtargetAbi` を付けなければ両方の ABI を含む APK になります。

release APK は `ANDROID_RELEASE_KEYSTORE` と `ANDROID_RELEASE_PASSWORD` に指定した
正式鍵で署名します。手動ビルド時の debug APK はデバッグ鍵で署名し、
`applicationIdSuffix ".debug"` により `org.androshogi.debug` として通常版
（`org.androshogi`）と同時にインストールできます。両者の棋譜・評価関数・設定は
共有されず、debug APK では `nn.bin` を別途設定する必要があります。
旧 debug APK は `org.androshogi` だったため、新しい debug APK への上書き更新は
できません。旧 debug APK が入っている場合、通常版のインストールには旧 debug APK の
アンインストールが必要です。`minifyEnabled` はどちらも false です。

## エンジンの再ビルド

同梱する実行ファイルは、[mizar/YaneuraOu の v7.5.0+20220507c.hash128](https://github.com/mizar/YaneuraOu/releases/tag/v7.5.0%2B20220507c.hash128)
の通常版 Android アーカイブ（`YaneuraOu-v7.5.0+20220507c.hash128-android.zip`）に
含まれる `android/NNUE/YaneuraOu_NNUE_arm64-v8a` と
`android/NNUE/YaneuraOu_NNUE_x86_64` です。`jni/Android.mk` の
`EVAL_EMBEDDING=OFF` によるビルドで、評価関数は実行ファイルに含まれません。
配布元バイナリの SHA-256 は次のとおりです。

| ABI | SHA-256 |
|---|---|
| arm64-v8a | `c036336b889d1fe4ed45a0567ca64af57c870cf1545441125b6c245d70c03a74` |
| x86_64 | `6291d8563f061a73891e252ccc33536ed1ce7e962fcf44c161cf873fc97f2ba9` |

それぞれ `libYaneuraOu_NNUE_<abi>.so` にリネームして `jniLibs/<abi>/` に置いています。
評価関数がない状態でもアプリは起動し、盤面・棋譜を操作できます。検討または棋譜解析を
選ぶと不足を知らせるダイアログが表示され、エンジンは起動しません。

評価関数は同梱していません。利用者が入手した標準 NNUE（HalfKP）対応の評価関数を
端末に保存し、アプリの **設定 → エンジン → 評価関数ファイル（nn.bin）** で選択します。
Android のファイル選択画面で選んだファイルはバックグラウンドでアプリ専用領域の
`filesDir/eval/nn.bin` にコピーされます。取り込みが完了した後、設定画面から戻ると
エンジンを作り直し、USI の `EvalDir` にそのフォルダを指定して読み込みます。
水匠5の `nn.bin` を使う場合は、設定 → エンジン → `FV_SCALE` で `24`、
Háo の評価関数を使う場合は `20` を選びます。
既定値は通常版やねうら王と同じ `16` です。変更は次の検討・解析開始時に反映されます。
大きなファイルの取り込みには時間がかかる場合があります。コピーに失敗した場合、
以前の評価関数は保持されます。対応しないファイルを選んだ場合はエンジンの起動に
失敗するため、別の評価関数を選び直してください。

再ビルドする場合は配布元タグのソースから Android の `script/android_build.sh` を
`YANEURAOU_ENGINE_NNUE` 指定で実行し、`EVAL_EMBEDDING` を有効にせず
同じ 2 ABI の実行ファイルを配置します。配布物のソース、ライセンス、詳しい
ビルド記録は配布元の ZIP に含まれています。

`EngineSession.java` はエンジンに対して `usi` → `setoption` → `isready` →
`usinewgame` を送って常駐させ、以後は `position sfen ...` → `go movetime ...`
→ `stop` を繰り返すだけなので、USI に準拠した他のエンジンに差し替えることも
可能です。

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

## 駒画像の生成

`app/src/python/koma.py` で `res/drawable-*/koma_*.png` を生成しています。
フォントに Yuji Mai を使用します。

```sh
cd app/src/python
pip install pillow aggdraw opencv-python numpy
python koma.py
```
