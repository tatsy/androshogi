# Third-party notices / 第三者ソフトウェアについて

AndroShogi は GNU General Public License v3.0（`LICENSE`）で公開しています。
やねうら王や cshogi など、同梱・派生している GPLv3 のコンポーネントについて以下に記載します。
このブランチの APK には NNUE 評価関数を同梱しません。

## 思考エンジン

### やねうら王 (YaneuraOu) — GPLv3

- 配置: `app/src/main/jniLibs/<abi>/libYaneuraOu_NNUE_<abi>.so`
- 出典: https://github.com/yaneurao/YaneuraOu
- Android 上で実行ファイルとして起動するため、`.so` の名前で同梱しています（`EngineSession.java`）。
- 同梱バイナリは [mizar/YaneuraOu v7.5.0+20220507c.hash128](https://github.com/mizar/YaneuraOu/releases/tag/v7.5.0%2B20220507c.hash128)
  の通常版 Android ZIP の `android/NNUE/` から取得しました。標準 NNUE (HalfKP) 版です。
- 配布元の `jni/Android.mk` では `EVAL_EMBEDDING=OFF` が既定値です。
  水匠5やその他の評価関数のデータはこのバイナリに含めていません。
- 各 ABI の元ファイルの SHA-256 と配置手順は `README.md` の「エンジンの再ビルド」を参照してください。
- 配布元 ZIP に付属する `Copying.txt` と `source.tar.xz`、やねうら王のソースリポジトリを参照してください。

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
