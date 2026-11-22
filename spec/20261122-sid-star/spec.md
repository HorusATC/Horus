# RJTT の SID / STAR 第一スライス

## メタデータ

- **Status**: In Progress
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md)、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、Issue [#114](https://github.com/HorusATC/Horus/issues/114)、出発初期上昇 [#137](https://github.com/HorusATC/Horus/pull/137)（[#115](https://github.com/HorusATC/Horus/issues/115)）、ウェイポイント高度制約 `spec/20261017-waypoint-constraints/spec.md`
- **リポジトリ配置**: `spec/20261122-sid-star/spec.md`
- **抽出データ（実装の入力）**: `aip/rjtt-sid-star-20261101.json` および同名の要約 `.md`（公式 AIP Japan、AIRAC 1 Nov 2026）。リポジトリにコミットする JSON はこの1本のみ。Backend は Gradle `processResources` で classpath `fix/rjtt-sid-star-20261101.json` へコピーして読む（resources への二重コミットはしない）

## 概要

東京国際空港（RJTT）の公式 SID / STAR をカタログとして読み込み、航空機に割り当てて既存の飛行計画ウェイポイント追従で飛ばし、レーダー上に経路を出す。対象は抽出済みの RNAV STAR 15 本（いずれもチャート見出しは RWY 34R/34L）と VAMOS FOUR RNAV SID（全滑走路および TATEYAMA / DRAKY トランジション）である。VISIP ONE（従来方式、座標表なし）と公示ホールディングの自動飛行は入れない。

出典の扱いは次の一行だけとする。README とデータファイルに書く。UI に警告バナーは出さない。

> Derived from AIP Japan (AIRAC 1 Nov 2026), may not be current, edited by the Horus project and not government-made.

## 背景・課題

### 現状（main `8e5b680`、#137 初期上昇マージ済み。Issue #115 クローズ、#114 オープン）

- フィックスカタログは `Backend/src/main/resources/fix/waypoints.json`（名前・緯度・経度・種別）。ATS 下位経路と enroute の RNAV 経路（例: M523）はあるが、SID / STAR の手続きカタログも割当 API も無い。
- 飛行計画のウェイポイントは `FlightPlanWaypointDto`（`fix`, `altitude`, `constraint`, `speed`, `action`）。高度制約 `AT` / `AT_OR_ABOVE` / `AT_OR_BELOW` / `BETWEEN` / `NONE` と、その解決は既にある。`speed` は対地速度であり、AIP の KIAS ではない。
- 飛行計画付きスポーンは、フィックス名が `waypoints.json` に無いと失敗する。本抽出の 53 点のうち、`XAC`・`T6R11`・`T6L21`・`TT450`–`TT468`・`TT501`・`TT502` の 24 点はカタログに無い。既にある 29 点は AIP の DMS 換算と最大おおむね 1.4×10⁻⁵ 度差で、既存座標は変えない。
- `DEPART` はカタログ滑走路の物理しきい・標高+35 ft・滑走路真方位で初期上昇を始める。SID の VA / DF とはまだつながっていない。
- 位置 API に飛行計画の全経路は乗っていない。Setup の経路プレビューと ATS 経路描画はある。割当した SID / STAR の折れ線をレーダーで見せる必要がある。
- 公式チャートは SWIM の AIP ビューア、効力 AIRAC 1 Nov 2026 の PDF から抽出した。コーディング表は磁方位と括弧内の真方位、偏差 -7.9 を掲載している。Horus の針路は測地／真の枠であり、偏差をシミュレータ針路に適用しない（既存方針）。

### 課題（Problem Statement）

到着機・出発機に公示の SID / STAR を割り当てて飛ばす手段が無く、端末レーダーの訓練で経路を手入力の飛行計画に頼っている。#114 の第一スライスである。

### なぜ今か（Motivation）

初期上昇（#137）まで入り、公式チャートも手元にある。既存の飛行計画追従と高度制約を再利用すれば、ARINC 424 の完全なエンジンやホールディング自動飛行を待たずに、割り当て・追従・表示を 1 PR に収められる。

---

## 方針

### 決定方針（Decision）

1. **データの範囲と出典**
   - 実装が読むカタログは、抽出 JSON（またはそれと同等のリポジトリ内リソース）とする。RNAV STAR 15 本と VAMOS FOUR の 6 滑走路系＋2 トランジションを載せる。VISIP ONE は抽出には含めるが、本スライスでは割り当て対象にしない（座標が無く、従来方式のため）。
   - ウェイポイント座標は AIP の DMS 表が正。未収録の 24 点をフィックス参照に追加する（`waypoints.json` へ足すか、同じリポジトリが読む追加ファイルでもよい）。既に存在する名前の座標は書き換えない。
   - カタログと README に、概要に示した出典の一行を書く。UI には同等の警告バナー・モーダル・透かしを出さない。Horus はシミュレータであり、「運航に使わない」旨を強調しない。

2. **API（加算。既存の飛行計画契約を壊さない）**
   - 手続きの一覧と詳細を返す GET を足す（例: `GET /api/procedures?airport=RJTT` と `GET /api/procedures/{id}`）。応答には識別子、種別（SID / STAR / トランジション）、名前、適用滑走路、レグ（フィックス名・パス種別、高度制約、公示真方位と距離があればそれ）を含めてよい。既存のパスは変えない。
   - 割当は加算の POST とする（例: `POST /api/aircraft/{callsign}/assign-procedure`、ボディに `procedureId`、VAMOS のエンルートトランジションを付けるときはその ID）。成功時は、その手続きの経路を既存の `FlightPlanWaypoint` 列として飛行計画に載せる（高度制約をマッピングする）。未知の ID、未知のコールサインは 404。既存のスポーン・飛行計画更新・`DEPART`・Direct To・Hold・`JOIN FINAL` のパスと必須フィールドは変えない。
   - パス表の KIAS はカタログに残してよいが、`FlightPlanWaypointDto.speed`（対地速度）には入れない。KIAS 制限の自動適用は本スライスの非目標。
   - `FL150`（POLIX の IF）を既存のフィート高度フィールドへ載せるときは、シミュレータの既存フィールドへの対応として 15000 ft・制約 `AT` とする。これは AIP の換算表ではなく、マッピングであることをテストか短いコメントで残す。

3. **飛行（既存のウェイポイント追従を使う）**
   - STAR の IF / TF は、既存のフィックス間飛行と高度制約解決で追従する。針路は測地／真の枠のままとし、磁方位や偏差 -7.9 を指示針路に足し引きしない。公示真方位は検証や表示に使ってよい。
   - VAMOS の VA は、コーディング表の真方位を指示針路とし、そのレグの at-or-above 高度に達するか、次の DF フィックスへ移る条件を満たすまで維持する。続いて DF は当該フィックスへの Direct、そのあとの TF は STAR と同じ追従とする。これは簡易な対応であり、ARINC 424 の完全な path terminator 処理や到達勾配 5.0% の強制ではない（勾配はカタログ注記に留める）。
   - 公示ホールディング（コーディング表の Hold 行）はカタログに残してよいが、本スライスでは自動では入らない。既存の Hold 指示はそのまま使える。
   - A / K / C を 34L か 34R の一方へ自動割付しない。適用滑走路はチャート見出しどおり 34L と 34R。利用者が手続きを選ぶ。

4. **UI**
   - Operator（必要なら Setup の飛行計画画面でもよい）から、空港の手続きを選んで対象機へ割り当てられるようにする。
   - 割り当てた経路は、飛行計画パネルのウェイポイント列に加え、Controller / Operator のレーダーに折れ線（または同等の経路描画）で出す。これが本スライスの「画面に見える」条件である。
   - 出典の警告バナーは出さない。フィックス名や手続き名のラベルは通常の表示でよい。

5. **退行を出さない（No-regression）**
   - 既存 API のパス・必須フィールド、クイックスタート到着サンプルの起動、`DEPART`、Hold、`JOIN FINAL`、位置 API の既存キー、ATS / RNAV 経路の表示、既存フィックスの座標を壊さない。
   - 既存の Backend / Frontend テストは、本機能用の期待値追加を除き、契約を緩めて通してはならない。CI 4 ジョブがマージ SHA で合格する。

### 検討した他案（Alternatives Considered）

- **15 STAR と VAMOS をデータに載せず、OSHIMA / AKSEL と VAMOS 34L/34R だけ実装する**: 飛行コードは汎用になるので、同じ PR でカタログを抽出どおり載せた方が後続が小さい。必須のデモは OSHIMA 1A/1K・AKSEL 1A/1K と VAMOS（少なくとも 34L/34R）で足りる。
- **VISIP も飛ばす**: 座標表が無く、従来方式のヘディング / ラジアルだけである。発明座標はしない方針と両立しない。
- **KIAS を `targetSpeed` に入れる**: 単位が対地速度なので誤りになる。
- **磁方位に偏差を適用して指示する**: 既存の測地／真の枠と矛盾する。表に真方位がある。
- **UI に運航注意のバナーを出す**: プロダクト方針に合わない。出典は README とデータファイルの一行に限る。

## 非目標（Non-goals）

- VISIP ONE の割当・飛行、および VISIP 座標の推定。
- 公示ホールディングの自動エントリー / 周回。
- 他空港、IAP、ゴーアラウンド経路、SID と `DEPART` の完全な一体化（VA の簡易対応以上の結合）。
- 到達勾配 5.0% の強制、RADAR / GNSS 要件のシミュレーション。
- KIAS 速度制限の自動適用、および対地速度への換算。
- 磁方位モデル、偏差の適用。
- ARINC 424 エンジンの完全実装（CF、CA 等、本抽出に無い descriptor を含む）。
- A / K / C と 34L / 34R の自動対応付け。
- 出典を示す UI バナー、モーダル、透かし。
- 既存飛行計画 API の破壊的変更。

## 完成条件（Success Criteria）

静的検査と型検査をテストより優先してよい。ただし次は明示する。

1. **Backend**
   - カタログがビルドに含まれ、STAR 15・VAMOS 滑走路系 6・トランジション 2 を返せる。
   - ゴールデン例: OSHIMA 1A のウェイポイント順が `XAC, ANZAC, TT450, TT451, TT452, TT453, WANDA, WEDGE, ARLON` で、ANZAC / WANDA が AT 13000、WEDGE が AT 8000。パス表の 230 KIAS は飛行計画の `speed` に入らない。
   - 割当 API で航空機の飛行計画がその経路になり、IF / TF を既存の高度制約つき追従で飛ぶ（少なくとも OSHIMA 1A または AKSEL 1A のテスト）。
   - VAMOS FOUR の 34L/34R（または同等）で、VA の真方位 330.0° を指示し、その後 TT502 → LOCUP（AT OR ABOVE 5000）→ VAMOS（AT OR ABOVE 9000）へ進むことを、テストか同等の検証で示す。
   - 未知手続きは 404。既存のスポーン / 飛行計画 / `DEPART` テストは契約を緩めずに通る。
2. **Frontend**
   - 型検査（またはプロジェクト既定の静的検査）と `Frontend` のビルドが通る。
   - 手続きを割り当てる操作があり、割り当てた経路がレーダーに折れ線として見える（スクリーンショットかブラウザ確認）。飛行計画パネルにフィックス列が出る。警告バナーは無い。
3. **クイックスタート**
   - 既存の到着サンプルが、本スライス以前と同じ手順で起動する。SID / STAR 未割当の既定機の動きを変えない。
4. **文書**
   - Backend またはルート README と、リポジトリに置くデータファイルの両方に、概要の出典一行がある。未実装（VISIP 飛行、公示ホールド自動、KIAS 適用、勾配強制）を実装済みのように書かない。
5. **CI**
   - 4 ジョブがマージ SHA で合格する。

## 実装の手がかり

- 抽出の正本: 本仕様と同時に置く `aip/rjtt-sid-star-20261101.json`（唯一のコミット済み JSON）。ビルド時に classpath `fix/` へコピーする。不確実項目は JSON の `uncertain` と要約 MD を正とする（パス表速度の制限種別、A/K/C と滑走路の一対一、頁フッタ日付と AIRAC の差、VISIP の文言と TTE 座標と VISIP 自体の欠測座標）。
- 高度の符号は既存の `AltitudeConstraint` へそのまま対応できる（AT / AT_OR_ABOVE / AT_OR_BELOW）。`FL150` だけは上記のフィートマッピング。
- 追加フィックスはスポーンと Direct To からも解決できる場所に置く。
- レーダー描画は Setup の経路プレビューや ATS 経路描画を参考にしてよいが、航空機の割当経路として Controller / Operator 画面に出すこと。
- テストはカタログ読込と割当・制約のゴールデン、および既存契約の退行に絞る。画面の網羅テストより型検査とビルドを優先してよい。

## README

- 出典は概要の一行のみ（README とデータファイル）。
- Operator（または Setup）から SID / STAR を割り当て、レーダーに経路が出ることを書く。
- VAMOS の VA / DF は簡易対応であること、公示ホールドと VISIP と KIAS 自動適用は未実装であることを明記する。
- 既存のクイックスタート手順は維持する。
