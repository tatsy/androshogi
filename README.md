# AndroShogi

[![Android CI](https://github.com/tatsy/androshogi/actions/workflows/android.yml/badge.svg)](https://github.com/tatsy/androshogi/actions/workflows/android.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub Release](https://img.shields.io/github/v/release/tatsy/androshogi?color=blue)](https://github.com/tatsy/androshogi/releases)

将棋の棋譜をAI（やねうら王 NNUE）で検討するためのAndroidアプリです。
盤面を操作しながら候補手と読み筋を確認したり、貼り付けた棋譜を
手順に沿って検討したりすることを目的にしています。

## インストール

[Releases](https://github.com/tatsy/AndroShogi/releases) からAPKをダウンロードしてインストールしてください。

このアプリはGoogle Playストアでは配布していないため、端末の設定で「提供元不明のアプリ」を許可する必要があります。

## 評価関数の設定

初回起動時に、やねうら王のNNUE評価関数を設定する必要があります。

[やねうら王の公式のインストール手順](https://github.com/yaneurao/YaneuraOu/wiki/%E3%82%84%E3%81%AD%E3%81%86%E3%82%89%E7%8E%8B%E3%81%AE%E3%82%A4%E3%83%B3%E3%82%B9%E3%83%88%E3%83%BC%E3%83%AB%E6%89%8B%E9%A0%86)
内にある評価関数の中から好きなものを選んでダウンロードし、その中に含まれる `nn.bin` をアプリから選んでください。

設定の「エンジンの種類」で、評価関数の構造に対応するエンジンを選択してください。

| NNUE 構造 | 評価関数の例 | FV_SCALE の初期値 |
| --- | --- | --- |
| `halfkp_256x2_32_32` | [Háo](https://github.com/nodchip/tanuki-/releases/tag/tanuki-.halfkp_256x2-32-32.2023-05-08)、[水匠5](https://github.com/yaneurao/YaneuraOu/releases/tag/suisho5) | 16（Háo は20、水匠5は24に変更） |
| `halfkp_512x2_8_64` | 振電3 | 40 |
| `halfkp_768x2_16_64` | AobaNNUE | 40 |

選択後、「評価関数ファイル（nn.bin）」から対応するファイルを取り込んでください。
評価関数と FV_SCALE は構造ごとに保持し、切り替えても以前の設定を保持します。
従来の評価関数と FV_SCALE は `halfkp_256x2_32_32` 用として引き継ぎます。
FV_SCALE は評価関数の配布元の案内に合わせて調整してください。
メイン画面に戻るとエンジンを再起動します。実行中の検討・棋譜解析は終了し、保存済みの解析結果は保持します。

## 主な機能

- 盤面の表示と操作（合法手のみ、成り／不成の選択、持ち駒の打ち込み）
- 手を戻す／進める、開始局面・終了局面へのジャンプ、盤の上下反転
- クリップボードからの KIF 棋譜の読み込み（平手・駒落ち）。端末からの
  `.kif`/`.kifu` ファイル選択や、他アプリから共有された KIF テキスト／ファイル
  （Shift_JIS／UTF-8）にも対応
- 表示中の棋譜を KIF としてコピー・共有、またはファイルに保存（`.kif` は Shift_JIS、`.kifu` は UTF-8）。
  初回保存時に Documents/AndroShogi などのフォルダーを作成・選択すると、次回からその場所を保存画面の初期位置に指定する（端末のファイル選択画面によっては反映されない場合がある）
- やねうら王による検討。候補手を盤上の矢印で、読み筋をテキストで表示
- 棋譜全体の自動解析。結果は局面ごとに保持し、手を戻す／進めると再表示
- 「新規」で初期局面に戻す
- 設定: エンジンの種類、評価関数、思考時間、候補手の数、スレッド数、ハッシュサイズ、FV_SCALE、評価値のスケール。

## ライセンス

GNU General Public License v3.0 (GPLv3) 2026 (c) Tatsuya Yatagawa

本プロジェクトでは以下のオープンソースを利用しています。

- [cshogi](https://github.com/TadaoYamaoka/cshogi): GPLv3
- [やねうら王](https://github.com/yaneurao/YaneuraOu): GPLv3

第三者ソフトウェアの詳細については[こちら](./docs/THIRD_PARTY_NOTICES.md)を参照してください。
