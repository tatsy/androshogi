# Third-party notices / 第三者ソフトウェアについて

AndroShogi は GNU General Public License v3.0（`LICENSE`）で公開しています。
やねうら王や cshogi など、同梱・派生している GPLv3 のコンポーネントについて以下に記載します。
このブランチの APK には NNUE 評価関数を同梱しません。

## 思考エンジン

### やねうら王 (YaneuraOu) — GPLv3

- 配置: `app/src/main/jniLibs/<abi>/libYaneuraOu_NNUE_<NNUE構造>_<abi>.so`
- 出典: https://github.com/yaneurao/YaneuraOu
- 固定コミット: [`c1b80eaa09fe13d5f12b1599d1ae4d53c224de30`](https://github.com/yaneurao/YaneuraOu/tree/c1b80eaa09fe13d5f12b1599d1ae4d53c224de30)（9.80git）
- 同梱バイナリは本リポジトリのサブモジュール `third_party/YaneuraOu` から
  `tools/build_yaneuraou.sh` でビルドしたものです。次の3構造を arm64-v8a・x86_64 の両 ABI 向けに同梱しています。
  - `halfkp_256x2_32_32`
  - `halfkp_512x2_8_64`
  - `halfkp_768x2_16_64`
- Android 上で実行ファイルとして起動するため、`.so` の名前で同梱しています（`EngineSession.java`）。
- ビルドの既定値は NDK `27.2.12479018`、対象 API `android-29` です。
  構造ヘッダを生成し、`patches/yaneuraou/0001-fix-android-neon.patch` を適用します。
  このパッチは arm64-v8a のビルドオプションを調整し、通常の NEON 経路を有効にします。
- 上流の `script/jni/Android.mk` の `EVAL_EMBEDDING=OFF` を使用しています。
  水匠5などの NNUE 評価関数のデータは埋め込まず、利用者が指定する `nn.bin` を読み込みます。
- ライセンス全文は上流の固定コミットの [LICENSE](https://github.com/yaneurao/YaneuraOu/blob/c1b80eaa09fe13d5f12b1599d1ae4d53c224de30/LICENSE) を参照してください。
  対応するソースは上記固定コミットと本リポジトリのパッチ・生成／ビルドスクリプトです。
  再ビルド・配置手順は [CONTRIBUTION.md の「思考エンジンの再ビルド」](CONTRIBUTION.md#思考エンジンの再ビルド) に記載しています。

## 局面ロジック（JNI）

### cshogi — GPLv3

- 配置: `app/src/main/cpp/cshogi/`（`jni.cpp` を除く）
- 出典: https://github.com/TadaoYamaoka/cshogi
- `cshogi.h`, `dfpn.*`, `mate.*`, `parser.h`, `endianness.h` は cshogi 由来、
  それ以外の C++ ソースは cshogi が取り込んでいる Apery 由来です。
- `KifParser.java` は cshogi の KIF パーサ（Python）を Java に移植したものです。

### Apery — GPLv3

- Copyright (C) 2011-2018 Hiraoka Takuya
- 出典: https://github.com/HiraokaTakuya/apery
- Apery 自体は Stockfish（GPLv3）から派生しています。
  - Copyright (C) 2004-2008 Tord Romstad (Glaurung author)
  - Copyright (C) 2008-2015 Marco Costalba, Joona Kiiski, Tord Romstad
  - Copyright (C) 2015-2018 Marco Costalba, Joona Kiiski, Gary Linscott, Tord Romstad

## フォント

### Noto Sans JP — SIL Open Font License 1.1

- 配置: `app/src/main/res/font/noto_sans_jp.ttf`
- 出典: https://fonts.google.com/noto/specimen/Noto+Sans+JP

### Yuji Mai — SIL Open Font License 1.1

- 配置: `app/src/python/YujiMai-Regular.ttf`
- 出典: https://fonts.google.com/specimen/Yuji+Mai
- 駒画像（`res/drawable*/koma_*.png`）を `app/src/python/koma.py` で生成する際に使用しています。
