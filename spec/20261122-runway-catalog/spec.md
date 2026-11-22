# 滑走路カタログの拡張（羽田 A/B/C/D、成田 A/B）

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md)、[spec/20261122-atis-runway-in-use/spec.md](../20261122-atis-runway-in-use/spec.md)、[spec/20261017-runway-final-approach/spec.md](../20261017-runway-final-approach/spec.md)、[spec/20261122-runway-distance-rbl/spec.md](../20261122-runway-distance-rbl/spec.md)
- **リポジトリ配置**: `spec/20261122-runway-catalog/spec.md`
- **実装ベース**: main `92de912`（#134 マージ後）
- **値の出典（実装時）**: 非公式 AD 2.12 PDF 写し（RJTT EFF 28 NOV 2024、RJAA EFF 2 OCT 2025）および AirBinder AIRAC 2611（本文 2026-10-29）。**公式 AIP in force とは未照合。公式値であると主張しない。**

## 概要

滑走路カタログを、東京国際（RJTT）の A/B/C/D（16R/34L、16L/34R、04/22、05/23）と成田国際（RJAA）の A/B（16R/34L、16L/34R）の全末端に広げる。進入端座標・真方位・長さ・標高は、JCAB AIP Japan AD 2.12（Runway Physical Characteristics）および公示距離 AD 2.13 を定義源とする。既存の `16R` 識別子と、`GET /ats/runways`、`GET`/`PUT /api/atis`、`JOIN FINAL`／復行／タワー移管の API 形は維持する。JSON に調査値が載っている末端はそれを使い、#132 の対向派生は、対向が JSON に無いときだけのフォールバックに残す。

**16R の数値は、識別子は残すが、AIP の調査値へ訂正する。** 現行の RJTT 16R（方位 163、長さ 3360 m、進入端 35.553 / 139.781）は AD 2.12 と一致せず、3360 m は 16L/34R の寸法である。16L を真方位 149.88° で足しつつ 16R を 163° のままにすると、平行滑走路が平行にならない。契約（パス・識別子・既定の使用滑走路 16R）は壊さないが、最終進入のコースと進入端は約 0.9–2 km・十数度動く。この訂正に依存するテストは更新する。

## 背景・課題

### 現状（着手前: main `92de912` / 直前の #132 系は `9580dab`）

- `Backend/src/main/resources/fix/runways.json` の明示エントリは RJTT 16R と RJAA 16R の 2 件だけだった。
  - RJTT 16R: `heading` 163、進入端 35.5530 / 139.7810、`lengthM` 3360
  - RJAA 16R: `heading` 155、進入端 35.7647 / 140.3864、`lengthM` 4000
- #132 は `RunwayCatalog.deriveReciprocal` で対向（34L）を、既存進入端をカタログ方位と長さでオフセットして足す。README も「AIP の公式座標そのものとは限らない」と書いている。
- `RunwayDefinition.heading` は `int` だった。`GET /ats/runways`、ATIS の `availableRunways`（16R と派生 34L）、`JOIN FINAL`、進入端距離、MSAW 進入廊は、このカタログを使う。ATIS の既定は各空港 16R / A。
- #132 の非目標に「羽田・成田の 16R/34L 以外の滑走路追加」があった（本スライスで解消）。

### 課題（Problem Statement）

- 羽田は 4 本、成田は 2 本の滑走路を使い、ATIS 上の使用滑走路も 22/23 や 34L など 16R 以外になる。カタログが 16R/34L だけだと、#132 の使用滑走路切替が運用を再現できない。
- 現行 16R の数値は AIP AD 2.12 とずれており（後述の対照）、派生 34L もそのずれを引き継ぐ。

### なぜ今か（Motivation）

- 使用滑走路と ATIS は #132 で入った。次に足りないのは、切替先となる調査済みの末端である。手順（交差滑走路、LAHSO、同時平行進入）は本スライスに入れない。
- 値は AD 2.12 / 2.13 の表にあり、Python での DMS 換算と距離のクロスチェック、契約テスト、ブラウザで検証できる。

---

## 方針

### 決定方針（Decision）

1. **出典と発効日（公式本文はログインが必要で、本ドラフトでは未ログイン）**
   - **公式の定義源**: 国土交通省航空局 AIP Japan、飛行場 AD 2「RJTT - TOKYO International」「RJAA - NARITA INTL」の **AD 2.12 RUNWAY PHYSICAL CHARACTERISTICS**（真方位、寸法、進入端座標、進入端標高、変位進入端）および **AD 2.13 DECLARED DISTANCES**（TORA / LDA、RWY 05 は着陸不可）。
   - **公式の入手先**（本ドラフト作成時はアカウント無しで本文を取得できなかった）:
     - [AIS JAPAN](https://aisjapan.mlit.go.jp/)
     - [SWIM portal](https://top.swim.mlit.go.jp/swim) の [AIP 閲覧サービス（S2004）](https://top.swim.mlit.go.jp/swim/servicedetails/S2004)
   - **本ドラフトで照合したコピー**（公式ログイン本文ではない。実装時は SWIM の “AIP in force” で再確認する）:
     - AD 2.12 の数値は、公開されている AIP 本文 PDF の写しから読み取った。RJTT AD 2.12 表の頁フッタは `Civil Aviation Bureau, Japan (EFF: 28 NOV 2024)`（31/10/24）。RJAA AD 2.12 表の頁フッタは `(EFF: 2 OCT 2025)`（2/10/25）。
     - 真方位・寸法・LDA は、[AirBinder RJTT](https://airbinder.jp/airports/rjtt/) および [AirBinder RJAA](https://airbinder.jp/airports/rjaa/) が「AIP Japan AIRAC 2611、本文版 2026-10-29、有効 2026-10-29 00:00 JST 〜 2026-11-26 00:00 JST」として掲げる表と一致した。AirBinder 自身は公式頁ではない。
   - ドラフト作成日は 2026-11-22（Asia/Tokyo）。この日の有効サイクルは AIRAC 2611（〜2026-11-26 00:00 JST）。SWIM は 2026-11-26 発行分を既に公開している。実装・マージ時は、その時点の AIP in force を優先し、数値が変わっていればカタログと本 spec の表を更新する。
   - カタログの `heading` は **真方位**（AD 2.12 TRUE BRG）。シミュレータに磁偏差モデルは無い。AD 2.2 の MAG VAR（RJTT 8°W (2021)、RJAA 8°W (2023)）は参考に止め、数値には掛けない。

2. **16R の扱い（識別子は互換、数値は AIP へ訂正）**
   - `16R` は両空港で残し、ATIS の既定使用滑走路も 16R / A のままとする。
   - 進入端・真方位・長さは下表の AIP 調査値に更新する。現行値との差:
     - RJTT 16R: 着陸進入端まで約 915 m、方位 163° → 149.88°、長さ 3360 m → 3000 m（3360 m は 16L/34R）。
     - RJAA 16R: 進入端まで約 1,959 m、方位 155° → 149.50°、長さ 4000 m は一致。
   - `JOIN FINAL`・復行・タワー移管・進入端距離・MSAW 進入廊のうち、カタログの 16R 幾何に依存するものは、新しい進入端と真方位に追随する。方位 163 や座標 35.553 / 139.781 を直接書いたテスト（`RunwayCatalogTest`、`FinalApproachInterceptTest`、`MsawDetectorTest`、`rangeBearing.test.ts` 等）は、調査値に更新する。

3. **#132 の対向派生**
   - JSON に載っている末端は、常にその調査値をカタログの正とする。
   - `deriveReciprocal` は、対向の designator が JSON に **無い** ときにだけ使う（フォールバック）。34L が JSON にあれば派生 34L は作らない。
   - 派生関数自体は残し、往復テストも、調査値が無い対に対して残してよい。

4. **カタログに載せる値（着陸進入端を `threshold*` にする）**
   - `thresholdLatitude` / `thresholdLongitude` は、着陸に用いる進入端とする。AD 2.12 に Displaced THR がある場合は変位後の座標（RJTT 16L / 34R / 16R）。変位がない 34L、04/22、05/23、成田 4 末端は表の THR を使う。
   - `lengthM` は AD 2.12 の寸法（舗装長）。`heading` は TRUE BRG を小数のまま保持する（`int` から `double` へ広げる。JSON の number としては互換。API の `heading` も number のまま、非整数になり得る）。
   - 加算フィールド（任意、既存クライアントは無視できる）の例: `widthM`、`thresholdElevationFt`、`ldaM`、`toraM`、`displacedLandingThreshold`（真偽）、`physicalThresholdLatitude` / `physicalThresholdLongitude`（変位があるとき）、`source`（`AIP_AD_2_12` または `DERIVED_RECIPROCAL`）、`usableForLanding`（RJTT 05 は false）。
   - 最終進入の幾何: インターセプトと延長は **着陸進入端** から測る。滑走路描画の遠端は、対向の物理進入端、または着陸進入端から LDA（変位があるとき）／`lengthM`（無いとき）とする。着陸進入端に舗装全長を足すと、変位分だけ滑走路が長くなるのでしてはいけない。

5. **採用する調査値（DMS は AD 2.12、十進は同じ値の換算。地球半径 6378.1 km での距離は参考）**

   **RJTT（羽田）。通称 A=16R/34L、B=16L/34R、C=04/22、D=05/23。**

   | 末端 | TRUE BRG | 寸法 (m) | 着陸進入端（DMS） | 十進緯度 | 十進経度 | THR 標高 (ft) | LDA (m) | 注 |
   |------|----------|----------|-------------------|----------|----------|---------------|---------|----|
   | 16R | 149.88° | 3000×60 | 353322.47N 1394618.19E（変位） | 35.55624167 | 139.77171944 | 16.4 | 2520 | 物理 THR 353335.95N 1394608.64E、標高 20.5 ft。変位 480 m 内側 |
   | 34L | 329.88° | 3000×60 | 353211.76N 1394708.41E | 35.53660000 | 139.78566944 | 18.2 | 3000 | 変位なし。TDZ 18.4 ft |
   | 16L | 149.88° | 3360×60 | 353346.27N 1394719.34E（変位） | 35.56285278 | 139.78870556 | 19.2 | 2970 | 物理 THR 353357.23N 1394711.59E、標高 21.8 ft。変位 390 m |
   | 34R | 329.88° | 3360×60 | 353233.02N 1394811.34E（変位） | 35.54250556 | 139.80315000 | 19.7 | 3000 | 物理 THR 353222.90N 1394818.49E、標高 27.8 ft。変位 360 m |
   | 04 | 035.01° | 2500×60 | 353256.47N 1394540.60E | 35.54901944 | 139.76127778 | 19.0 | 2500 | |
   | 22 | 215.01° | 2500×60 | 353402.88N 1394637.61E | 35.56746667 | 139.77711389 | 35.0 | 2500 | |
   | 05 | 042.56° | 2500×60 | 353126.41N 1394812.47E | 35.52400278 | 139.80346389 | 45.5 | — | AD 2.13: Not usable for LDG。出発には使える |
   | 23 | 222.56° | 2500×60 | 353226.15N 1394919.61E | 35.54059722 | 139.82211389 | 54.7 | 2500 | |

   **RJAA（成田）。通称 A=16R/34L、B=16L/34R。変位進入端の記載なし。**

   | 末端 | TRUE BRG | 寸法 (m) | 進入端（DMS） | 十進緯度 | 十進経度 | THR 標高 (ft) | LDA (m) |
   |------|----------|----------|---------------|----------|----------|---------------|---------|
   | 16R | 149.50° | 4000×60 | 354627.80N 1402205.85E | 35.77438889 | 140.36829167 | 129.6 | 4000 |
   | 34L | 329.51° | 4000×60 | 354435.96N 1402326.66E | 35.74332222 | 140.39073889 | 139.4 | 4000 |
   | 16L | 149.50° | 2500×60 | 354818.72N 1402241.19E | 35.80520000 | 140.37810833 | 134.5 | 2500 |
   | 34R | 329.51° | 2500×60 | 354708.82N 1402331.72E | 35.78578333 | 140.39214444 | 141.1 | 2500 |

   対向の真方位は、AIP 表記のまま入れる（成田は 149.50° と 329.51° で、180° ちょうどではない）。本ドラフトで地球半径 6378.1 km の球面距離を取ると、変位 390 / 360 / 480 m は 391.0 / 361.0 / 481.1 m、LDA 相当は公示値の約 +0.2% に収まった。クロスチェックはこの許容（変位 ±2 m、対向長 ±15 m）で固定する。

6. **API（形は互換、要素の追加と 16R 数値の訂正）**
   - `GET /ats/runways` の要素に使っている既存キーは残す。滑走路が増える。`heading` は number のまま、小数を許す。
   - `GET` / `PUT /api/atis`: 既定は各空港 16R / A。`availableRunways` は、その空港の全カタログ末端（派生でなく JSON の調査値）になる。未知の designator は従来どおり 400。
   - 使用滑走路は、現行契約どおり空港あたり 1 本の `runwayInUse` を維持する。羽田の実運用では到着 22/23・出発 16L/16R のように分かれることがあるが、到着／出発の分離は本スライスでは足さない（後続の加算フィールド候補）。
   - `JOIN FINAL`／復行／タワー移管: 要求形（`airportIcao` + `runwayDesignator`）は変えない。カタログにあれば、その着陸進入端と真方位で幾何を組む。**RJTT 05 への `JOIN FINAL` は 400**（AD 2.13 の着陸不可）。復行・出発のための 05 指定は、既存の復行 API が滑走路方位を使う範囲では拒否しなくてよい（着陸会合ではない）が、README に「05 は着陸に使わない」と書く。
   - 位置 API と Hold API は変えない。

7. **JOIN FINAL と使用滑走路への影響**
   - 16R への最終進入コースは、旧 163°（RJTT）／155°（RJAA）から AIP 真方位へ変わる。インターセプト点も新しい着陸進入端の 8 NM 外側になる。既に古い 16R 延長に載っているシナリオ機がいれば、コースから外れる。サンプルが 16R ファイナル前提なら、位置または針路を新しい幾何に合わせる。
   - Controller が使用滑走路を 34L や 22 などに変えると、ATIS 本文と情報識別は #132 どおり更新され、Operator の `JOIN FINAL` 初期値もその末端になる。進入中の機体は、使用滑走路変更だけでは付け替わらない（#132 のまま）。
   - 進入端距離と MSAW 進入廊は、カタログの全着陸可能末端に対して計算される（廊が増える）。

### 検討した他案（Alternatives Considered）

- **案 A: 現行 16R の数値を凍結し、他末端だけ AIP で足す。** 採用しなかった理由: RJTT 16R を 163°・3360 m のまま 16L を 149.88° で足すと、平行滑走路が平行でない。研究用カタログとして使えない。
- **案 B: 到着滑走路と出発滑走路を ATIS で分離する。** 採用しなかった理由: #132 の `runwayInUse` 契約を広げる必要がある。カタログ拡張が主目的なので、後続の加算に残す。
- **案 C: 変位を無視して物理進入端だけ使う。** 採用しなかった理由: 16R の着陸可用長は 2520 m であり、進入端距離と最終進入は着陸進入端から測るべきである。

### トレードオフ（Trade-offs）

- **メリット**: 羽田・成田の全末端で、使用滑走路と `JOIN FINAL` が調査値に基づく。
- **デメリット / 受容する制約**: 16R の進入端とコースが変わる（API パスは同じ）。公式 AIP の再確認は実装時に必要。使用滑走路は空港あたり 1 本のまま。交差運用や同時平行は入らない。

### 非目標（Non-goals）

- 交差滑走路の運用方式、LAHSO、同時平行 ILS／NTZ、騒音軽減の時間帯別フロー
- 到着滑走路と出発滑走路の ATIS 分離、風による自動選定
- 磁方位への換算、他空港の滑走路、SID／STAR のカタログ
- 公式 AIP のリポジトリへの全文収録（値と出典・発効日だけをカタログと spec に残す）

---

## 完了条件（Success Criteria）

- [x] `spec/20261122-runway-catalog/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている。出典 URL と、実装時に確認した AIP in force / AIRAC の発効日が書いてある（公式ログイン本文は未取得。非公式写し／AirBinder AIRAC 2611 を明記）
- [x] `runways.json` に上表の 12 末端（RJTT 8、RJAA 4）が調査値で載っている。16R の識別子は残るが、数値は表のとおり
- [x] リポジトリ内の Python クロスチェックが、DMS→十進を再現し、変位距離（390 / 360 / 480 m ±2 m）と対向長（公示 LDA または寸法の ±15 m）を検証し、JSON の緯度経度・真方位が表と一致する（緯度経度 1e-7°、方位 0.01°）。出力は `crosscheck_output.txt`
- [x] JSON に対向があるときは `deriveReciprocal` で二重に足さない。調査値の 34L が、旧 16R（163° / 3360 m）からの派生点ではない
- [x] `GET /ats/runways` の既存キーは残り、要素数が増える。`GET /api/atis` の既定は 16R / A のまま、`availableRunways` に新末端が含まれる。`PUT` で 22 や 34L を選べ、未知の識別は 400
- [x] `JOIN FINAL` の要求形は変わらない。新末端（例: RJTT 22、RJAA 16L）で 200 になり、進入コースが当該 TRUE BRG になる。RJTT 05 への `JOIN FINAL` は 400
- [x] 16R の旧座標・163° に依存していたユニットテストを調査値へ更新し、合格する
- [x] Frontend のテストとビルド、Backend のテストが合格する
- [x] CI の 4 ジョブが、マージするコミットの SHA で全部合格する（PR #135 / `d14d1c4`）
- [x] Backend README と Frontend README に、12 末端、真方位、変位進入端、16R 数値の訂正、05 は着陸不可、対向派生はフォールバック、出典と発効日を書く。ルート README に変更があれば同じ PR に含める
- [x] ギャップ台帳または #132 子 spec の「16R/34L 以外は非目標」を、本スライスの完了に合わせて更新する
- [x] ブラウザ確認とスクリーンショット: Controller でサンプル開始＋ATIS パネル、および live `/ats/runways` から羽田 4 本・成田 2 本の幾何を描画した証跡を PR #135 に掲載。ATIS PUT 22/16L・JOIN FINAL 成功／RJTT 05 の 400 は curl 証跡済み

### 16R 旧値 → 新値（意図的な挙動変更）

| 空港 | 項目 | 旧 | 新（調査値） |
|------|------|----|--------------|
| RJTT 16R | heading | 163 | 149.88 |
| RJTT 16R | lengthM | 3360 | 3000 |
| RJTT 16R | threshold | 35.5530 / 139.7810 | 35.55624167 / 139.77171944（着陸進入端・変位） |
| RJAA 16R | heading | 155 | 149.50 |
| RJAA 16R | lengthM | 4000 | 4000（変更なし） |
| RJAA 16R | threshold | 35.7647 / 140.3864 | 35.77438889 / 140.36829167 |

---

## 影響範囲

- **Backend**: `runways.json`、`RunwayDefinition`（`heading` を double に、任意フィールド）、`RunwayCatalog`（調査値優先、派生は欠ける対向のみ、進入幾何は着陸進入端）、ATIS の `availableRunways`、`JOIN FINAL` の 05 拒否、関連テスト、`UranosAPI.yml`（`heading` が非整数になり得ること、要素増加）
- **Frontend**: 滑走路描画、進入端距離、ATIS／`JOIN FINAL` の選択肢、`rangeBearing` 等の 16R 座標テスト
- **シナリオ**: 旧 16R ファイナルに載せている機がいれば位置・針路を更新
- **README**: Backend / Frontend（ルートにあれば同様）
- **位置 API・Hold API**: 変更しない

---

## 実装計画

### Phase 1（Must-have）

1. Python クロスチェックと、表を正とした `runways.json` を先に置く。
2. カタログ読み込みを調査値優先に変え、`heading` を double にし、着陸進入端ベースの幾何と 05 の着陸拒否を入れる。
3. 旧 16R 数値に依存するテストを更新し、README と出典を書く。

### Phase 2（このスライスの外）

- 到着／出発滑走路の ATIS 分離、時間帯別フロー
- 交差運用、LAHSO、同時平行進入
- 他空港

---

## 検証

- [x] Python クロスチェックとカタログ／JOIN FINAL／ATIS のテストが、実装後は成功する（変異で失敗することも確認）
- [x] 既存 API のパスと 16R 識別子が残っている
- [x] ブラウザで全滑走路の描画、使用滑走路切替、16R／別末端の最終進入、05 の拒否を確認し、スクリーンショットを残す（PR #135 証跡）
- [x] CI 4 ジョブがマージ SHA で合格する（PR #135 / `d14d1c4`）

---

## 未解決事項（Unresolved Questions）

- 実装時点の AIP in force が AIRAC 2611 を過ぎて数値を変えていた場合は、SWIM の新しい表を正とし、本 spec の表を同じ PR で更新する。
- 通称 A/B/C/D を API に載せるか。第 1 版は README の説明に留め、必須フィールドにはしない。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20261122-atis-runway-in-use/spec.md](../20261122-atis-runway-in-use/spec.md)
- [spec/20261017-runway-final-approach/spec.md](../20261017-runway-final-approach/spec.md)
- 公式: [AIS JAPAN](https://aisjapan.mlit.go.jp/)、[SWIM portal](https://top.swim.mlit.go.jp/swim)、[AIP 閲覧サービス S2004](https://top.swim.mlit.go.jp/swim/servicedetails/S2004)（AIP Japan AD 2.12 / AD 2.13。ログインが必要）
- 照合用（非公式）: AirBinder [RJTT](https://airbinder.jp/airports/rjtt/)・[RJAA](https://airbinder.jp/airports/rjaa/)（AIRAC 2611、本文 2026-10-29）
- [Backend/README.md](../../Backend/README.md)
- [Frontend/README.md](../../Frontend/README.md)
