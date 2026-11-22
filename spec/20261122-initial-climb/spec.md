# 離陸から初期上昇（滑走路方位での出発）

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md)、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、[spec/20261017-go-around/spec.md](../20261017-go-around/spec.md)、[spec/20261017-tower-handoff/spec.md](../20261017-tower-handoff/spec.md)、[spec/20261017-runway-final-approach/spec.md](../20261017-runway-final-approach/spec.md)、[spec/20261122-runway-catalog/spec.md](../20261122-runway-catalog/spec.md)、[spec/20261122-msaw/spec.md](../20261122-msaw/spec.md)、[spec/20261122-uniform-wind/spec.md](../20261122-uniform-wind/spec.md)、Issue [#115](https://github.com/HorusATC/Horus/issues/115)
- **リポジトリ配置**: `spec/20261122-initial-climb/spec.md`

## 概要

到着側の `JOIN FINAL`／復行／タワー移管に対し、出発機をカタログ滑走路から上げ、初期上昇の針路と高度まで誘導できるようにする（Issue #115）。既定の針路は当該滑走路のカタログ真方位（FLY RUNWAY HEADING）である。地上滑走・V 速度・SID は入れない。公式 AIP の SID／STAR 本文はログイン必須のため本スライスの対象外とし、Issue #114 に残す。API は加算（新 POST と位置 DTO の任意フラグ）であり、既存の到着系 API・無風時の運動・ATIS 既定本文は変えない。

同じ PR の文書修正（機能変更ではない）:

- `spec/spec.md` のフェーズ 1-5（トップ等からの JSON アップロード起動）を Done にする。実装は PR #118、Issue #48 はクローズ済み。ギャップ表の「1-5 は未着手」も直す。
- Frontend README の、速度ベクトルを「針路方向の白線」と書いている箇条を、#136 以降の実装どおり **トラック＋GS** に直す（同 README の前段「速度ベクトル（トラック）」と揃える）。

## 背景・課題

### 現状（main `c1d5788`、#136 一様風マージ済み。Issue #69 クローズ）

- 優先度 1（研究者が少ない手順で最初のシナリオに到達する）は #128 で満たしている（3 コマンド、0 判断、2 クリック）。本スライスでクイックスタートを広げない。
- 優先度 2 の到着・出発ギャップのうち、最終進入・復行・タワー移管・ホールド・使用滑走路／ATIS・一様風・12 末端カタログは実装済み。台帳の残りは **SID／STAR、離陸から初期上昇、公示ホールディング／EAT／EFC、待機レグ時間の公式風補正** など。SID／STAR（#114）の公式 AIP は AIS／SWIM がログイン必須で、#135 と同様に公式本文を取れない。
- 出発を始める API は無い。Operator には `JOIN FINAL`／`GO AROUND`／タワー移管がある。復行は滑走路方位で上昇するが、滑走上からの出発ではない。
- MSAW はカタログ進入端から 1.0 NM 未満で抑制する。出発機が DER 付近を 1 NM 以上離れてまだ低いと、誤警報になり得る。
- サンプル交通は到着中心である。
- 文書のずれ: `spec/spec.md` の 1-5 は ⬜ 未着手のまま（#118 / #48 と不一致）。Frontend README の機能箇条に「速度ベクトル線（針路方向の白線）」が残り、#136 のトラック表示と食い違う。

### 課題（Problem Statement）

ターミナル席の出発側入口（滑走路から初期上昇の針路・高度まで）が再現できない。到着の最終進入と対になる操作が無く、出発訓練シナリオを置けない。

### なぜ今か（Motivation）

優先度 2 の到着・出発の残りのうち、SID／STAR は公式出典が取れない。初期上昇はカタログの真方位・標高と、既にある上昇運動・復行 API の型で 1 PR に収まる。公開一次資料（FAA 5-8-2 / 5-8-3 注、JCAB (Ⅲ)５(１)）で針路と高度の扱いを固定できる。QNH や方位目盛より、飛行が見えるギャップが大きい。

---

## 方針

### 決定方針（Decision）

1. **根拠となる一次資料**
   - **FAA** Order JO 7110.65BB Change 3（効力 2026-07-09）:
     - [5-8-2 INITIAL HEADING](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_8.html): 離陸直後にレーダー誘導する場合、出発前に初期針路を指定する。用語は `FLY RUNWAY HEADING.` / `TURN LEFT/RIGHT, HEADING (degrees).`
     - [5-8-3](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_8.html) NOTE 1: FAA Order 8260.46 および 8260.3（TERPS）は、旋回を始める前に DER 標高から 400 フィート上昇することを前提とし、障害物間隔は DER からの 200 feet per nautical mile の上昇勾配で確保される、とする。同時平行出発の間隔そのものは本スライスに入れない。
     - [4-3-2 Departure Clearances](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap4_section_3.html): 出発許可に、離陸後の初期針路と高度を含め得る。用語に `FLY RUNWAY HEADING` がある。
   - **国土交通省航空局**『航空保安業務処理規程 第５管制業務処理規程』（[JAPA 2025-03 公開 PDF](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)）(Ⅲ)５(１): 出発機への情報及び指示に使用滑走路、風向風速、高度計規正値等を含める。本スライスは出発の運動と針路・高度に限り、ATIS の QNH 追加はしない（#71 の残り）。
   - **滑走路幾何**: 既存カタログ（#135）の真方位・物理進入端・進入端標高を使う。公式 AIP の SID チャートは取得しない。針路はシミュレータの測地／真方位フレームのまま（磁偏差は掛けない。FAA の磁方位用語とは数字がずれ得ることを README に書く）。

2. **1 PR に収める出発モデル（地上滑走なし）**
   - 新 API（例）: `POST /api/aircraft/{callsign}/depart`。ボディは復行にならい、必須が `airportIcao`、`runwayDesignator`、`climbAltitudeFt`。任意で `initialHeadingDeg`（省略時はカタログの当該末端真方位 = runway heading）。
   - 地上滑走・加速・V1/VR/V2 は持たない。指示を受けた機体を、**出発滑走路の物理進入端**（TORA 始端。変位がある末端は `physicalThreshold*`、無い末端は `threshold*`）の直上へ置き、高度はその端の標高 + **35 ft**、機首は滑走路真方位（または指定の初期針路）として、指定高度へ既存の上昇性能で上げる。35 ft は FAA 5-8-3 注が参照する「DER を少なくとも 35 ft で通過」に合わせた、離陸ロールを省略した空中開始点である（DER そのものではなく始端からの出発に使う）。
   - この「滑走路始端への移動」は訓練用の出発開始であり、既存位置からの連続タクシーではない。README と UI にそう書く。
   - 上昇目標は復行と同じく、開始高度（置いたあとの高度）より **500 ft 以上**高いこと。範囲は 1,000–45,000 ft。満たさない場合、未知の空港／滑走路、存在しないコールサインは **400**。RJTT **05 は出発に使える**（AD 2.13 の着陸不可は `JOIN FINAL` だけに効く。05 への DEPART は 200）。
   - 速度: 新たな性能モデルは足さない。既存の指示 GS／IAS があれば維持する（復行と同じ）。どちらも無ければ、指示対地速度 180 kt を置く（無風では TAS = GS。風があれば #136 の三角形に従う）。V 速度表は作らない。
   - 既定は滑走路方位のまま上昇する。`initialHeadingDeg` が滑走路方位と異なるときだけ、**DER 標高 + 400 ft** に達するまで滑走路方位を維持し、その後に指定針路へ旋回する（5-8-3 NOTE 1）。DER は対向末端の物理進入端（出発の遠端）とその標高とする。200 ft/NM の勾配は既存の最大上昇率に任せ、新しい性能制限としては強制しない（満たせない機体があり得ることを README に書く）。
   - 「FLY RUNWAY HEADING」は **機首方位** である。横風では #136 どおりトラックがずれてよい（コース維持の WCA は掛けない）。

3. **レーダー上で出発中と分かること**
   - 位置 API に加算の boolean（例: `initialClimb`、既定／省略は false）を付ける。既存の `goAround` / `towerHandoff` は変えない。真のあいだ、レーダーラベルに到着の `GA` / `TWR` と区別できる表示（例: `DEP`）を出す。
   - フラグは、次の針路指示、Direct To、Hold、Resume、`JOIN FINAL`、復行、フライトプラン付与、タワー移管で消す（復行フラグと同じ考え方）。
   - Operator に DEPART 操作を足す。空港・滑走路の初期値は当該機の目的地または ATIS の使用滑走路でよい。送信契約は明示フィールド。

4. **MSAW**
   - 既存の「いずれかのカタログ進入端から 1.0 NM 未満は抑制」は残す。
   - 追加: `initialClimb` が真で、かつ出発端（物理進入端）から滑走路方位に沿って **5 NM 以内、左右 1.0 NM 以内** にいるあいだは MSAW を出さない。廊を出るかフラグが消えたら通常どおり。通常の低高度誤警報を防ぐためであり、遠方で初期高度に達したあとも永久に抑制しない。

5. **退行を出さない（No-regression）**
   - 既存 API のパス・必須フィールド・ステータスコードを変えない（`JOIN FINAL`、復行、タワー移管、Hold、`GET`/`PUT /api/atis`、`GET /ats/runways`、位置 API の既存キー、風が欠測のときの運動）。
   - 風欠測時の位置・針路・GS、ATIS 既定本文（風なしなら `WIND AND WEATHER NOT AVAILABLE.`、風ありなら現行の風行 + `WEATHER NOT AVAILABLE.`）、到着サンプルの開始配置は、出発 API を呼ばない限り今日と同一。
   - 既存の Backend / Frontend テストは、本機能用の期待値追加を除き、契約を緩めて通してはならない。CI 4 ジョブがマージ SHA で合格する。
   - 文書修正（1-5 の状態、速度ベクトルの README）は表示の訂正だけとし、実行時の挙動を変えない。

6. **同じ PR の文書修正**
   - `spec/spec.md`: フェーズ 1-5 の状態を ✅ にし、所在を PR [#118](https://github.com/HorusATC/Horus/pull/118)・Issue [#48](https://github.com/HorusATC/Horus/issues/48)（クローズ済み）に更新する。現状認識表の「1-5 は未着手」を削除または Done に直す。1-2〜1-4 の Optional 残り書きまで消す必要はない。
   - `Frontend/README.md`: 「**速度ベクトル線**（針路方向の白線）」を、位置 API の `vector.track` と GS で引く白い速度ベクトル線である、と直す。#136 で既に書かれている「速度ベクトル（トラック）」の段と矛盾しないこと。

### 検討した他案（Alternatives Considered）

- **案 A: 今すぐ 1 本の羽田 STAR／SID を載せる（#114）。** 採用しなかった理由: 公式 AIP の手続本文がログイン必須で取れない。非公式写しに頼るカタログを手続きレベルで増やすには、経路追従と制約の設計が 1 PR を超える。
- **案 B: ATIS に QNH を足して #71 を閉じる。** 採用しなかった理由: 風の次としては自然だが、高度計モデルを変えない限り表示だけになる。優先度 2 の到着・出発ギャップより小さい。次候補とする。
- **案 C: 離陸ロールと地上誘導を入れる。** 採用しなかった理由: 地表運動と管制許可の状態機械が要り、初期上昇の入口が埋もれる。
- **案 D: 既存機を動かさず、新しい出発機だけをスポーンする。** 採用しなかった理由: 復行と同じく既存コールサインへの指示があった方が Operator の操作とテストが単純。スポーン専用 API は後続で足せる。

### トレードオフ（Trade-offs）

- **メリット**: 出発の針路と高度が見え、05 を含むカタログ滑走路から訓練できる。既存到着フローは壊さない。
- **デメリット / 受容する制約**: 滑走・SID・騒音方式は無い。機体は始端へ飛ぶ（連続タクシーではない）。200 ft/NM は既存上昇率任せ。ATIS の QNH はまだ欠測。

### 非目標（Non-goals）

- SID／STAR の手続き選択と経路（#114。公式 AIP がログイン必須）
- 地上滑走、交差滑走路での待機、同時出発間隔（5-8-3 の海里）、ウェイク
- 公示障害物出発方式（ODP）の全文、200 ft/NM の強制、V 速度
- QNH・視程など ATIS 気象の残り（#71）、使用滑走路の自動選定
- 公示ホールディング、EAT／EFC、待機時間の公式風補正
- 方位目盛、セクター境界、セッション記録、出発席と進入席の分割

---

## 完了条件（Success Criteria）

- [x] `spec/20261122-initial-climb/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている。出典 URL がある
- [x] `POST /api/aircraft/{callsign}/depart`（または同等の加算パス）が、カタログ滑走路について 200 を返し、機体が物理進入端付近（許容はテストで固定、例: 0.1 NM 以内）、標高+35 ft 付近、滑走路真方位（または 400 ft 後の指定針路）で、指定高度へ上昇する
- [x] 未知の滑走路・コールサイン、上昇高度が開始高度 + 500 ft 未満は 400。RJTT 05 への DEPART は 200、`JOIN FINAL` の 05 は 400 のまま
- [x] 位置 API の `initialClimb`（名称は実装で固定）が真のあいだ、レーダーに `DEP`（または仕様で決めた表示）が出る。次の針路指示等で消える。`goAround` / `towerHandoff` の既存契約は変わらない
- [x] 既定針路はカタログ真方位。異なる `initialHeadingDeg` では DER 標高 + 400 ft まで滑走路方位を維持してから旋回する
- [x] 風欠測では、出発しない機体の運動と ATIS 本文が今日と同一。横風では滑走路方位出発のトラックが偏流してよい
- [x] `initialClimb` 中かつ出発廊（始端から 5 NM、左右 1.0 NM）内では MSAW が出ない。廊の外では既存規則どおり
- [x] **退行なし**: 既存の Backend / Frontend テストが、契約を緩めずに合格する。`JOIN FINAL`／復行／タワー移管／Hold／ATIS／風欠測のゴールデン文字列と無風同値を壊さない
- [x] Frontend と Backend のビルドおよびテストが合格する
- [x] CI の 4 ジョブが、マージするコミットの SHA で全部合格する
- [x] Operator から DEPART でき、Controller レーダーで `DEP` と上昇が見える。ブラウザ確認とスクリーンショットを残す（例: サンプル開始 → 1 機を RJTT 16R・適切な高度で出発 → 始端から滑走路方位で上昇、`DEP` 表示、近傍で MSAW なし。別ケースで 05 出発が成功し、05 への `JOIN FINAL` は失敗）
- [x] Backend README と Frontend README に、出発 API、始端への移動、35 ft／400 ft、真方位、SID を含めないこと、MSAW 廊、出典を書く
- [x] `spec/spec.md` の 1-5 が ✅（#118 / #48）になり、「1-5 は未着手」が消えている
- [x] Frontend README の速度ベクトル箇条が、針路方向ではなくトラック＋GS を示す
- [x] ギャップ台帳の「離陸から初期上昇」を本スライス完了に更新し、SID／STAR は公式 AIP 待ちとして残す

---

## 影響範囲

- **Backend**: 出発指示のドメインと POST、位置 DTO の加算フラグ、MSAW の出発廊、`UranosAPI.yml`、テスト
- **Frontend**: Operator の DEPART、レーダーラベル、型／BFF
- **README**: Backend / Frontend
- **文書**: `spec/spec.md` の 1-5、ギャップ台帳、Frontend README の速度ベクトル
- **変えないもの**: 既存 API 契約、風欠測時の運動、ATIS 既定本文、SID カタログ、地上運動

---

## 実装計画

### Phase 1（Must-have）

1. 出発の純関数（始端座標、35 ft、滑走路方位、400 ft までの旋回抑制、MSAW 廊）と契約テスト、無風・既存 API の退行テストを先に置く。
2. POST、位置フラグ、Operator UI、レーダー `DEP`、MSAW 抑制を接続する。
3. README、台帳、`spec/spec.md` 1-5、Frontend README の速度ベクトルを同じ PR で直す。

### Phase 2（このスライスの外）

- SID／STAR（#114、公式 AIP が取れてから）
- 地上滑走、同時出発間隔
- ATIS の QNH（#71）、方位目盛

---

## 検証

- [x] 出発の成功／400 ケース、05 の非対称（DEPART 可・JOIN FINAL 不可）、400 ft 前の旋回抑制、MSAW 廊、フラグ解除のテストが、未実装では失敗し、実装後は成功する
- [x] 既存テストと ATIS／無風同値が退行しない
- [x] ブラウザで出発・`DEP`・16R 方位上昇・05 ケースを確認し、スクリーンショットを残す
- [x] CI 4 ジョブがマージ SHA で合格する
- [x] 文書修正が diff 上で 1-5 と速度ベクトルの 2 点に限って正しい

---

## 未解決事項（Unresolved Questions）

- 既定の 180 kt は V2 ではない。機種別の初期速度を後続で足すかは未決。
- 公式 AIP の日本向け障害物出発勾配を後で載せる場合は、SWIM の本文を確認してからにする。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)
- [spec/20261017-go-around/spec.md](../20261017-go-around/spec.md)
- [spec/20261122-runway-catalog/spec.md](../20261122-runway-catalog/spec.md)
- [spec/20261122-msaw/spec.md](../20261122-msaw/spec.md)
- FAA Order JO 7110.65BB Change 3: [5-8 Radar Departures](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_8.html)、[4-3 Departure Procedures](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap4_section_3.html)
- 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』: [JAPA 公開 PDF（2025-03）](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)
- Issue [#115](https://github.com/HorusATC/Horus/issues/115)、[#114](https://github.com/HorusATC/Horus/issues/114)（本スライス外）、[#48](https://github.com/HorusATC/Horus/issues/48)（文書のみ）、PR [#118](https://github.com/HorusATC/Horus/pull/118)
- [Backend/README.md](../../Backend/README.md)
- [Frontend/README.md](../../Frontend/README.md)
