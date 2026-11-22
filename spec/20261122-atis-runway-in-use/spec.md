# 使用滑走路と ATIS 表示（風モデルなし）

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md) フェーズ 6-1・6-2、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、[spec/20261017-runway-final-approach/spec.md](../20261017-runway-final-approach/spec.md)
- **リポジトリ配置**: `spec/20261122-atis-runway-in-use/spec.md`

## 概要

ターミナルレーダー席で常に共有される「使用滑走路」と、それに連動する ATIS（情報識別つきの空港情報）を、Controller と Operator のレーダー周辺に表示する。風モデルは入れない。気象欄は欠測として明示し、使用滑走路の変更だけが ATIS 本文と情報識別を進める。既存の `JOIN FINAL`／復行／タワー移管 API は変えず、滑走路はこれまでどおり要求に含める。カタログが空港あたり 1 末端しかないため、既存エントリの対向末端を同じ幾何から算出して加算し、切替を意味あるものにする。Hold 指示・位置 API は変更しない。

付随タスク: 既にマージされた [spec/20261122-hold-entry-sectors/spec.md](../20261122-hold-entry-sectors/spec.md) の Decision §6 テストベクトル「019° → DIRECT」を、半開区間規則どおり **PARALLEL** に直す（コードとテストは既に正しい）。

## 背景・課題

### 現状（main `9b8f5af` で確認）

- 滑走路カタログ（`Backend/src/main/resources/fix/runways.json`）は RJTT 16R と RJAA 16R の 2 末端だけである。`GET /ats/runways` で地図と進入廊、進入端距離に使う。`JOIN FINAL`・復行・タワー移管は、要求ごとに `airportIcao` と `runwayDesignator` を明示する。
- 使用滑走路のセッション状態も、ATIS パネルも無い。風ベクトルも無い（IAS は無風換算のまま）。
- ギャップ台帳のレーダー画面の残りに「風・ATIS・使用滑走路（ロードマップ 6-1、6-2）」がある。`spec.md` の 6-2 は「静的 JSON + レーダー周辺パネル」、6-1 は風と対地速度である。
- 待機は指示・レーストラック・進入セクターまで 3 PR 連続で入った。公示パターンと風修正はホールド側の残りだが、本スライスでは扱わない。

### 課題（Problem Statement）

- ターミナル管制では、到着機への初回情報と出発機への情報に使用滑走路が含まれ、ATIS を受信した旨の通報があれば省略できる。レーダー席に使用滑走路と現行 ATIS が無いと、その運用を訓練・研究できない。
- カタログが 16R のみだと、「使用滑走路を切り替える」操作が成立しない。

### なぜ今か（Motivation）

- 優先度 1（少ステップで最初のシナリオへ）は #128 で済んでいる。優先度 2 のうち、待機は一区切りついたので、同じホールド領域を続けず、画面に出る空港運用情報へ移る。
- 風モデルは GS・IAS・待機に波及するため 1 PR にしない。使用滑走路の選択と ATIS 表示は、新規の加算 API とパネル、カタログの対向末端追加に閉じられる。ブラウザと契約テストで検証できる。

---

## 方針

### 決定方針（Decision）

1. **根拠となる一次資料**
   - **日本（管制方式）**: 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』（JAPA 2025-03 公開、改正 145、国空制第 591 号、令和 7.3.20 適用）。
     - (Ⅱ)７(４)【到着機に対する情報等】: ターミナル管制所または飛行場管制所は、到着機と最初の無線通信を設定したのち、使用滑走路、風向風速、所定の場合の雲高・視程、高度計規正値等を速やかに通報する。ただしこれらのうち指定された項目は、ATIS または広域対空援助局等の情報に含まれ、航空機がそれを受信した旨を通報した場合は省略できる。
     - (Ⅲ)５(１)【出発機に対する情報及び指示】: 使用滑走路、風向風速、高度計規正値等を含める。ATIS 受信の通報があれば指定項目を省略できる。
     - (Ⅲ)１(６)【滑走路の選定】: 地上風 5 ノット以上なら風向に最も近い方位の滑走路、5 ノット未満で無風滑走路が指定されていれば当該滑走路。本スライスは風モデルが無いので、選定は Controller の明示操作とする（自動選定しない）。
     - (Ⅰ) 総則の ATIS 定義（飛行場に発着しようとする航空機へ必要な航空情報を自動装置により継続送信すること）、および ATIS 機関への通報（(Ⅰ) の当該項。放送項目の詳細は第４運航情報業務処理規程を参照しており、第４の本文は本スライスでは未取得のため項目リストを推測して置かない）。
   - **国土交通省航空局**: [空の安全を守るツバサノシゴト（ATIS）](https://www.mlit.go.jp/koku/jans/shigotomap/phase04.html)。ATIS は進入方式・使用滑走路・滑走路の状態・気象等を反復放送・データ通信で提供する業務、とする。
   - **FAA**: Order JO 7110.65BB Change 3（効力 2026-07-09）。
     - [2-9 Automatic Terminal Information Service Procedures](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap2_section_9.html): 2-9-1 は各 ATIS を音声文字コード（Alpha〜Zulu、順送り）で識別する。2-9-2 は使用滑走路の変更など関連データの変化で新しい録音にする。2-9-3 の内容には、空港／施設名、音声文字コード、気象観測時刻（UTC）、風・視程等の気象、計器進入と使用滑走路、初期交信で受信を通報する旨、が含まれる。
     - [3-10-1 Landing Information](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap3_section_10.html): 到着機への着陸情報に runway in use、地上風、高度計規正値が含まれる。パイロットが適切な ATIS コードを述べれば、放送に含まれる項目は省略できる。
   - **AIP Japan GEN 3.3**: 飛行場情報放送の国内記載箇所として知られるが、AIS Japan の原文は今回取得していない。放送項目の定義源にはしない。
   - 滑走路方位は既存カタログどおり測地フレーム（磁偏差なし）。

2. **使用滑走路はセッション状態。飛行は自動では変えない。**
   - カタログにある空港ごとに、現行の使用滑走路（designator）と ATIS 情報識別（A–Z）を持つ。既定は RJTT 16R / A、RJAA 16R / A。
   - Controller が使用滑走路を切り替えると、その空港の情報識別を 1 つ進め（Z の次は A）、ATIS 本文の滑走路を書き換える（FAA 2-9-2 の「runway change で新しい録音」）。識別だけを進める操作も可。
   - 既に `JOIN FINAL` した機体の針路や進入滑走路は、使用滑走路の変更だけでは動かさない。個別の進入・復行・移管指示は、既存 API どおり要求に滑走路を含める。
   - Operator の `JOIN FINAL`（および復行・タワー移管）の滑走路入力は、当該空港の使用滑走路を初期表示してよい。送信契約は現行のまま（明示フィールド、未送信にしない）。

3. **ATIS 本文（単一の合成関数。風・実気象は入れない）**
   - 必須要素: 空港名（`airports.json` の `name`）と ICAO、情報識別（音声文字、例 INFORMATION ALPHA）、使用滑走路（LANDING AND DEPARTURE RUNWAY〔designator〕）、受信通報の要求（ADVISE YOU HAVE INFORMATION〔letter〕）。
   - 風・視程・雲・気温・QNH・進入方式の公示名称は、観測も IAP カタログも無いため本文に数値や方式名を作らない。欠測を明示する 1 行を置く（例: `WIND AND WEATHER NOT AVAILABLE.`）。無風を `CALM` と称して擬似気象にしない。
   - 合成結果は Backend の 1 関数をテストが直接呼び、ゴールデン文字列で固定する。

4. **カタログに対向末端を加算する（既存 16R は残す）**
   - 現行は空港あたり 1 末端なので、RJTT 16R の対向と RJAA 16R の対向を追加する。識別は対向番号規則（16R の対向は 34L）。進入端座標は、既存の進入端をカタログの方位と長さだけオフセットして求める。これは既存データの派生であり、AIP の公式座標そのものとは限らないことを README に書く。
   - 往復（16R → 34L → 16R）が距離・方位の許容差内で元に戻ることをユニットテストで固定する。`GET /ats/runways` の要素形状は変えず、要素数が増えるだけとする（加算。16R の既存エントリは残す）。
   - 羽田・成田の他滑走路（16L 等）は、公式座標が無いので本スライスでは追加しない。

5. **API（新規加算。既存契約を壊さない）**
   - 新設（例）:
     - `GET /api/atis`: カタログ空港ごとの現行状態の配列。各要素は少なくとも `airportIcao`、`airportName`、`runwayInUse`、`informationIdent`（1 文字 A–Z）、`text`、選択可能な `availableRunways`（designator の配列）。
     - `PUT /api/atis/{airportIcao}`: `runwayInUse` 必須（カタログに存在する designator）。`informationIdent` は任意（省略時は滑走路が変われば自動で 1 つ進め、同じ滑走路の再送では進めない）。未知の空港・滑走路は 400。
   - 既存の位置 API、Hold API、`JOIN FINAL`／復行／タワー移管、`GET /ats/runways` の要素スキーマは変えない。
   - `UranosAPI.yml` と契約テストを追加する。Frontend は BFF 経由で同じパスをプロキシする。

6. **レーダー UI**
   - Controller と Operator のレーダー周辺に、現行 ATIS 本文と使用滑走路が見えるパネルを置く（コンフリクト要約ストリップと重ならない位置）。
   - Controller は空港と使用滑走路を選べる。Operator は読取専用（必要なら情報識別の復唱欄は Must にしない）。
   - 使用滑走路の末端は、既存の滑走路描画でカタログどおり見える（34L 追加後は対向も描ける）。

### 検討した他案（Alternatives Considered）

- **案 A: リーダー線の自動配置を先にする。** 採用しなかった理由: 既定オフセットが全機 `(50, 50)` で重なる問題は実在し、1 PR でも足りる。ただしラベルは既に手動ドラッグでき、管制手順（使用滑走路・ATIS）の再現にはならない。
- **案 B: 風モデル（6-1）まで同時に入れる。** 採用しなかった理由: GS・IAS 換算・待機に波及し、1 PR を超える。JCAB の滑走路選定は風 5 ノット基準だが、本スライスは手動選定に留める。
- **案 C: 公示待機や SID／STAR を先にする。** 採用しなかった理由: カタログ整備が大きく、待機を 3 PR 続けたあとに同じ領域へ戻る優先度は低い。
- **案 D: 使用滑走路の変更で、進入中の機体を自動的に付け替える。** 採用しなかった理由: 既存の進入契約を黙って変える。指示は個別 API のままにする。
- **案 E: ATIS を静的 JSON の読み上げだけにする（6-2 の短い案）。** 採用しなかった理由: 滑走路を切り替えられないと、(Ⅱ)７(４) の中核が見えない。識別の繰り上げも再現できない。

### トレードオフ（Trade-offs）

- **メリット**: レーダー席に、到着・出発情報の前提となる使用滑走路と現行 ATIS が見える。滑走路変更と情報識別の連動をテストできる。
- **デメリット / 受容する制約**: 風・実気象・公示進入方式は無い。対向末端の座標は既存 16R からの派生。他滑走路は増えない。進入中の機体は自動では付け替わらない。

### 非目標（Non-goals）

- 風モデル、対地速度への風の適用、自動での滑走路選定（(Ⅲ)１(６) の風基準）
- 実 METAR／SPECI、QNH の時系列、視程・雲・RVR
- 公示進入方式名（ILS Z 等）のカタログ、D-ATIS、音声再生
- 羽田・成田の 16R/34L 以外の滑走路追加
- 使用滑走路変更による進入機の自動付け替え
- Hold API・位置 DTO の変更、SID／STAR、リーダー線の自動配置、セクター間ハンドオフ

### 付随タスク（ホールド進入セクター spec の訂正）

- `spec/20261122-hold-entry-sectors/spec.md` Decision §6 のテストベクトル「019° → DIRECT」を **「019° → PARALLEL」** に直す。
- 根拠は同 spec Decision §2 の半開区間（右旋回・入方向 090° のパラレルは 270° 以上 020° 未満）。019° は 020° 未満なので PARALLEL。020° はダイレクトの開始。コードと `HoldingPatternGeometryTest`（「Half-open [270, 020): 019° is PARALLEL」）は既に正しい。飛行コードは変えない。

---

## 完了条件（Success Criteria）

- [x] `spec/20261122-atis-runway-in-use/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている
- [x] `spec/20261122-hold-entry-sectors/spec.md` Decision §6 が「019° → PARALLEL」になっており、この訂正以外にホールド飛行コードを変えていない
- [x] 新規は `GET /api/atis` と `PUT /api/atis/{airportIcao}`（または本 spec で固定した同等パス）の加算に限る。位置 API、Hold API、`JOIN FINAL`／復行／タワー移管の要求・応答は変わらない
- [x] `GET /ats/runways` の各要素の形は変わらず、RJTT 16R と RJAA 16R が残ったまま、RJTT 34L と RJAA 34L が加算されている。対向座標の往復テストがある
- [x] 未知の空港・滑走路への PUT は 400。滑走路変更で情報識別が 1 つ進み、ATIS 本文の滑走路番号が変わることを契約テストまたはユニットテストで固定する。同じ滑走路の再 PUT では識別が進まない
- [x] ATIS 合成関数のゴールデンテストがある（空港名、INFORMATION〔letter〕、RUNWAY〔designator〕、欠測行、ADVISE YOU HAVE…）。風速や QNH の造値を本文に含まない
- [x] Controller と Operator のレーダーに、現行の使用滑走路と ATIS 本文が見える。Controller が滑走路を切り替えると、両方の画面の本文と識別が更新される
- [x] Operator の `JOIN FINAL` 等は、使用滑走路を初期表示してもよいが、既存 API に滑走路を明示して送る。使用滑走路を変えても、既に進入中の機体は自動では付け替わらない
- [x] Frontend のテストとビルド、Backend のテストが合格する
- [ ] CI の 4 ジョブが、マージするコミットの SHA で全部合格する
- [x] Backend README と Frontend README に、ATIS／使用滑走路 API、パネル、対向末端の派生である旨、風が無いこと、既存進入 API が不変であることを書く。ルート README に変更があれば同じ PR に含める
- [x] `spec/spec.md` の 6-2（および必要な範囲でギャップ台帳）を更新し、6-1 の風モデルが残課題であることを残す
- [x] ブラウザ確認とスクリーンショット: サンプルを開始し、パネルに RJTT 16R と INFORMATION ALPHA が見える。Controller が 34L に切り替えると本文と識別（BRAVO）が変わり、地図上の 34L が使える。Operator 側も同じ ATIS を見る。Resume／進入中機体の自動付け替えが起きないことを確認する

---

## 影響範囲

- **Backend**: `runways.json`（対向末端の加算）、`RunwayCatalog` と既存幾何テスト、ATIS／使用滑走路の状態と合成、新規 API、`UranosAPI.yml`、契約テスト
- **Frontend**: BFF、レーダー周辺パネル、Operator の滑走路初期値（任意）、関連テスト
- **README**: Backend / Frontend（ルートにあれば同様）
- **spec/spec.md**、ギャップ台帳、`spec/20261122-hold-entry-sectors/spec.md` の typo 訂正
- **位置 API・Hold API・JOIN FINAL／復行／移管の契約**: 変更しない

---

## 実装計画

### Phase 1（Must-have）

1. 対向末端をカタログに加算し、往復幾何テストを書く。
2. ATIS 合成と使用滑走路の状態、GET／PUT、識別の繰り上げ、契約テスト。
3. Controller／Operator パネル、README と台帳の更新。
4. 進入セクター spec の 019° 表記を PARALLEL に直す。

### Phase 2（このスライスの外）

- 風モデルと、(Ⅲ)１(６) に基づく自動選定
- 実気象、公示進入方式、他滑走路の AIP 座標
- D-ATIS、音声

---

## 検証

- [x] 完了条件の契約・合成・幾何テストが、未実装では失敗し、実装後は成功する（letter advance / reciprocal ミューテーションで失敗を確認）
- [x] 既存の進入・Hold・位置 API を変える差分が PR に含まれていない（カタログ件数の増加と typo 訂正を除く）
- [x] ブラウザで 16R→34L の切替、識別の進行、両画面の表示を確認し、スクリーンショットを残す
- [ ] CI 4 ジョブがマージ SHA で合格する

---

## 未解決事項（Unresolved Questions）

- パネルの既定空港を、レーダー中心に最も近いカタログ空港にするか、常に RJTT を出すか。実装時は RJTT を既定とし、空港の切替を可能にする。
- シナリオ開始・シミュレーションリセットで ATIS を ALPHA／16R に戻すか、プロセス寿命で保持するか。推奨は、新規ゲーム／シナリオ開始で既定に戻す。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)
- [spec/20261122-hold-entry-sectors/spec.md](../20261122-hold-entry-sectors/spec.md)（付随の typo 訂正）
- 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』: [JAPA 公開 PDF（2025-03）](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)（(Ⅱ)７(４)、(Ⅲ)５(１)、(Ⅲ)１(６)、ATIS 定義）
- 国土交通省航空局: [ATIS（ツバサノシゴト）](https://www.mlit.go.jp/koku/jans/shigotomap/phase04.html)
- FAA Order JO 7110.65BB Change 3: [2-9 ATIS](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap2_section_9.html)、[3-10-1 Landing Information](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap3_section_10.html)
- [Backend/README.md](../../Backend/README.md)
- [Frontend/README.md](../../Frontend/README.md)
