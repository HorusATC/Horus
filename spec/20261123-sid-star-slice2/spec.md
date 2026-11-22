# SID / STAR 第二スライス（手元データで完了できる範囲と、不足プレート）

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-23
- **関連**: [spec/20261122-sid-star/spec.md](../20261122-sid-star/spec.md)（#139）、[spec/20261123-star-quickstart-scenario/spec.md](../20261123-star-quickstart-scenario/spec.md)（#140）、Issue [#114](https://github.com/HorusATC/Horus/issues/114)
- **リポジトリ配置**: `spec/20261123-sid-star-slice2/spec.md`
- **前提 main**: `9f4c999`（PR-A〜D / #141–#144 マージ後）
- **抽出データ**: `aip/slice2-20261101.json`（本スライスの目録と不足プレート。パスレグの正本は引き続き `aip/rjtt-sid-star-20261101.json`）

## 概要

東京国際空港（RJTT）の残りの SID と、成田（RJAA）を第二空港として載せることを目標に、AIRAC 1 Nov 2026 の手元 PDF で**完全にコード化できる手続き**（レグ種別、座標付きフィックス、高度／速度制約）を判定した。結論は、**新規に完全コード化できる手続きは無い**。VISIP ONE のプレートは既にあるが、コーディング表も VISIP の緯度経度も無く、飛ばせない。残る RJTT の RNAV SID と、他滑走路配置の STAR、および RJAA の SID／STAR は、コーディング表がチャートプレート上にあり、それらは未取得である。

手元のデータで完了でき、利用者に見えるスライスは次とする。既にコード化済みの **VAMOS FOUR**（スライス 1 で対応済みの滑走路 16R/16L/34L/34R/04/05/22 と TATEYAMA / DRAKY）を使う、読込可能な出発サンプルを足す。既定の到着クイックスタートは変えない。カタログと目録のスキーマ検査を置き、サンプルを足す場合は STCA／MSAW の衝突ガードを通す。不足プレートは公式の正確なファイル名で列挙し、取得後の第三スライスで残 SID と RJAA を載せる。

出典は次の一行だけとする。README とデータファイルに書く。UI に警告バナーは出さない。

> Derived from AIP Japan (AIRAC 1 Nov 2026), may not be current, edited by the Horus project and not government-made.

## 背景・課題

### 現状（main `9f4c999`）

- カタログは `aip/rjtt-sid-star-20261101.json`。実装済みは RNAV STAR 15 本（いずれもチャート見出し RWY 34R/34L）と VAMOS FOUR（滑走路 6 系＋トランジション 2）。VISIP ONE はカタログに残るが割り当て不能（コーディング表なし、`legs` 0）。公示ホールド自動・KIAS 自動適用は未実装。
- 既定の「START WITH SAMPLE」は #140 の到着 5 機（OSHIMA1A / AKSEL1K / AROSA2C / AKSEL2C / GODIN2A、使用滑走路 34L）。出発の既定サンプルは無い。
- 手元 PDF（`/workspace/horus-ops/aip/20261101/`）: `JP-AD-2-RJTT-en-JP.pdf`、`JP-AD-2-RJAA-en-JP.pdf`、および RJTT プレート `2.24.12`（VAMOS）、`2.24.26`（OSHIMA 1A/1K/2C）、`2.24.27`（AKSEL）、`2.24.32`（POLIX）、`2.24.53`（AROSA）、`2.24.71`（GODIN）、`2.24.88`（VISIP）。
- エンジンが飛ばせるパス記述子は IF / TF / VA / DF（HOLD はカタログのみ）。従来方式の HDG／VOR ラジアル／DME は未対応。
- AD-2 本文の 2.24 目次は手続き**名**だけを持ち、プレート番号 `JP-AD-2.24.xx` は印刷されていない。番号は AIRAC 1 Nov 2026 の eAIP AD-2 HTML 目次（`.../20261101/JP-AD-2-RJTT-en-JP.html` および `JP-AD-2-RJAA-en-JP.html` の公式 `href`）から取った。リスト順からの推定ではない。

### 手元データで完全にコード化できるか

判定基準は、レグ種別、フィックス座標、高度／速度制約が、発明なしでプレートまたは本文から読めること。

| 対象 | 判定 | 理由 |
|---|---|---|
| スライス 1 の STAR 15 と VAMOS FOUR | 既にコード済み | 手元プレートにコーディング表と座標表がある。 |
| VISIP ONE（RJTT 従来 SID） | **不可** | プレートあり。コーディング表なし。VISIP は `D28.1 TTE` のみで緯度経度が無い。TTE は Horus の `radio_navigation_aids.json` / `waypoints.json` に無い。叙述は RWY HDG・HDG100/208/223・TTE R178/R120・DME。 |
| 残る RJTT SID（SEKIYADO / OPPAR / ISOGO 従来、LAXAS / NINOX / TIARA A/B/C / BEKLA A/B/C / ROVER A/B/C / RUTAS / GUSRO の RNAV） | **不可（プレート不足）** | AD-2 本文にコーディング表も DMS 表も無い。 |
| RJTT の他 STAR（SULPU/OLRAT 従来、2H / 1H / 1N/2N / 1B/2B / 1S/1D / L/R / NIGHT / V） | **不可（プレート不足）** | 手元の STAR プレートは 34L/34R の 1A/1K/2C および 2A/2K/1C のみ。 |
| RJAA の全 SID／STAR（従来 SAKURA / AKAGI / SUNNS / SOUTH / NORTH、RNAV TETRA / REDEK / PIGOK / GULBO / BORLO / PEDLA / OLVAN、RUTAS/SWAMP/SUPOK/LUBLA の E/T と G/N） | **不可（プレート不足）** | RJAA のチャートプレートは未取得。AD-2 本文の SPID 表は初期フィックス名（34L ARIES、PEDLA のみ ASTRA、34R BOXER、16L BEAMS、16R ASPEN）だけ。それらの**名前**は `waypoints.json` にあるが、中間レグと制約はプレート上にある。 |

したがって、残る RJTT SID も RJAA も、今ある PDF だけでは「完全にコードして割り当てて飛ばす」ことはできない。座標やレグを推定しない（スライス 1 と同じ）。

### 課題（Problem Statement）

スライス 1 のあとも、RJTT の出発は VAMOS を手で割り当てる以外に既定の出発シナリオが無く、残 SID と第二空港はプレート待ちである。不足ファイル名が分からないと次の取得ができない。

### なぜ今か（Motivation）

レビュー修正 PR-A〜D はマージ済み。公式の AD-2 本文と eAIP 目次が揃い、不足プレートを正確な ID で列挙できる。新しいコーディングを発明せず、手元の VAMOS で出発サンプルという利用者に見える一段を置き、残作業をプレート取得後に残す。

---

## 方針

### 決定方針（Decision）

1. **データの範囲と出典**
   - 本スライスの目録は `aip/slice2-20261101.json`（および要約 `aip/slice2-20261101.md`）。パスレグとウェイポイント座標の正本は、既存の `aip/rjtt-sid-star-20261101.json` のままとする。スライス 2 の JSON に新しい完全コード化レグは載せない（載せられるものがないため）。
   - カタログと README、本目録に、概要の出典一行を書く。UI には警告バナー・モーダル・透かしを出さない。

2. **手元で完了する利用者向けスライス: VAMOS 出発サンプル**
   - 既定の到着「START WITH SAMPLE」と `quickstart-arrival-sample.json` は変えない。
   - 読込可能な出発サンプルを足す（推奨パス: `Frontend/data/quickstart-departure-sample.json`）。空港は RJTT、手続きは既にコード済みの VAMOS FOUR のみ。適用滑走路はスライス 1 が既に支える 16R / 16L / 34L / 34R / 04 / 05 / 22。任意で TATEYAMA または DRAKY を付ける。
   - トップまたは Flight Plan Setup の JSON 読込から、到着サンプルと同じ手順で投入できること。到着既定を置き換えないなら、ボタンを増やしてもよいし、アップロード用サンプルとして置くだけでもよい。手順は README に書く。
   - 16L と 16R、または 34L と 34R の VAMOS は VAMOS フィックスで合流し得る。**STCA／MSAW の衝突ガード**に掛け、衝突する組み合わせはサンプルから外す（#140 で POLIX を外したのと同じ規則）。衝突するなら機数を減らし、単機の出発（と、必要なら十分離れたトランジション上の機）にする。到着 5 機の既定サンプルと混ぜない。

3. **スキーマと静的検査**
   - `aip/slice2-20261101.json` と既存カタログの両方を、手続きスキーマの検査で読む。`procedures` に完全コード化として載るものは、すべてのレグのフィックス（VA を除く）が座標解決できること。目録の不足プレートは `missingPlates.sidStarOnly` のファイル名が、下記の公式 ID と一致すること。
   - 割り当て可能な手続きを、レグも座標も無いのに割り当て可能と表示しない（VISIP は従来どおり割り当て不能）。

4. **不足プレート（取得後の第三スライス）**
   - 残る RJTT RNAV SID と RJAA の RNAV SID／STAR は、下記の正確なファイル名を取得してからコードする。本スライスではレグを書かない。
   - IAP・飛行場図・障害物・ビジュアル・MVA 等は SID／STAR のコード化に不要で、本スライスの取得対象にしない。

5. **退行を出さない**
   - 既存の割当 API、位置 API、到着クイックスタート、`DEPART`、ATIS、PR-A〜D の契約を壊さない。既存フィックス座標を上書きしない。CI 4 ジョブがマージ SHA で合格する。

### 検討した他案（Alternatives considered）

- **案 A: VISIP を TTE の推定座標と HDG／ラジアルで飛ばす。** 座標の発明と、未実装の従来レグが要る。スライス 1 の方針に反する。却下。
- **案 B: `waypoints.json` に既にある LAXAS 等のエンルート名だけで SID を組む。** 中間レグと高度制約がプレートにしか無く、経路が公示と一致しない。却下。
- **案 C: 本スライスで残 SID／RJAA の空の手続き枠だけを UI に出す。** 割り当て不能な項目が増えるだけで、訓練に使えない。目録に留め、UI には出さない。
- **案 D: 到着既定サンプルを出発に差し替える。** 研究者が最初に見る到着 STAR 訓練を壊す。却下。出発は追加の読込用サンプルとする。
- **案 E: プレートが揃うまで PR を出さない。** 目録と不足ファイル名と出発サンプルは、今の PDF だけで完了でき、次の取得を具体化できる。採用する範囲はそこまでとする。

### トレードオフ（Tradeoffs）

- 第二スライスの名称でも、新しい公示経路は増えない。残 SID と RJAA はプレート待ちであることを仕様と README で明示し、期待をずらさない。
- 出発サンプルは VAMOS に限る。他方向の出発（TIARA / BEKLA 等）はプレート後になる。
- 平行滑走路の同時出発は公示上あり得るが、簡易な追従では VAMOS 合流で STCA が成り得る。ガードを優先し、機数を減らしてよい。

---

## 完了条件（Success Criteria）

型と静的検査をテストより先に満たす。そのあと、プレートに対する幾何（既存カタログ）、サンプルの衝突ガード、文書を残す。

1. **型・静的検査（先に）**
   - `aip/slice2-20261101.json` が、リポジトリの AIP JSON と同じトップレベル契約（`sourceNotice`、`package`、`uncertain`、`waypoints`、`procedures`）を持ち、スキーマ検査に通る。`procedures` に完全コード化として新しいレグを足していないこと（本ファイルでは空でよい）。
   - 既存カタログ `aip/rjtt-sid-star-20261101.json` の、割り当て可能なすべての手続きについて、各レグのフィックスが（VA レグを除き）座標解決できること。未知フィックスが残らないこと。
   - 目録の `missingPlates.sidStarOnly` が、本仕様「不足プレート」節のファイル名と一致する。
   - Frontend の型検査とビルド、Backend のコンパイルが通る。
2. **プレートに対する幾何（既存の完全コード化分）**
   - スライス 1 で置いた、コーディング表の距離・真方位と座標のクロスチェック（または同等の恒久テスト）が、本スライスで緩まない。新しい完全コード化レグは無いので、新規の幾何ゴールデンは要求しない。
3. **衝突／MSAW ガード（サンプルを足す場合）**
   - 出発サンプルをリポジトリに置くなら、その機の組み合わせで、既定の到着サンプルと同じ水準の STCA／MSAW ガードを通す。警報が出る組み合わせはサンプルに残さない。既定の到着 5 機サンプルのガードも退行させない。
4. **出発サンプルの利用者向け契約**
   - VAMOS FOUR の、衝突ガードを通った滑走路系（と任意のトランジション）が、JSON サンプルから割り当て・レーダー折れ線・飛行計画パネルに出る。VISIP は割り当てない。到着既定サンプルの起動手順と機構成は変わらない。
5. **文書**
   - ルート / Frontend / Backend README のうち SID／STAR とクイックスタートに触れる箇所を更新する。出典一行を維持する。不足プレートと、VISIP／残 SID／RJAA が未コードであることを、実装済みのように書かない。
6. **CI**
   - 4 ジョブがマージ SHA で合格する。

---

## 影響範囲

- **データ**: `aip/slice2-20261101.json` / `.md` をリポジトリへ追加。既存 `aip/rjtt-sid-star-20261101.json` のレグは変えない。
- **Frontend**: 出発サンプル JSON、トップまたは Setup からの読込、README。既定到着サンプルは触らない。
- **Backend**: カタログ読込の対象ファイルは原則そのまま。サンプル投入が既存の `procedureId` スポーンを使うだけなら、ドメイン変更は不要。
- **テスト**: 目録／カタログのスキーマとフィックス解決、出発サンプルの衝突ガード、既存クイックスタートの退行。
- **文書**: README と本仕様の不足プレート一覧。

---

## 範囲外（Non-goals）

- 不足プレート上の手続きのコード化（残る RJTT SID、RJTT の他 STAR 派生、RJAA の全 SID／STAR）。
- VISIP ONE の割当・飛行、VISIP または TTE 座標の推定、従来方式レグ（HDG／ラジアル／DME）のエンジン追加。
- 公示ホールディングの自動飛行、KIAS の自動適用、到達勾配 5.0% の強制、磁方位モデル。
- IAP、ゴーアラウンド経路、飛行場図・障害物図のカタログ化。
- 既定の到着クイックスタートの機構成や ATIS シードの変更。
- A / K / C と 34L / 34R の自動対応付け。
- 出典を示す UI バナー。
- 本 PR 内での SWIM からの追加ダウンロード。

---

## 実装計画

### Phase 1（目録と静的検査）

- `aip/slice2-20261101.json` をリポジトリに置く。スキーマ検査と、既存カタログのフィックス解決検査を足す。

### Phase 2（出発サンプル）

- VAMOS 出発サンプルを作り、STCA／MSAW ガードで組み合わせを確定する。読込経路と README を足す。到着既定は変えない。

### Phase 3（文書）

- README に、手元でできる範囲、VISIP と残プレートが未コードであること、不足ファイルの参照（本仕様または `aip/slice2-20261101.md`）を書く。

---

## 検証

- [x] スライス 2 JSON と既存カタログのスキーマ検査が通る。割り当て可能手続きのフィックスがすべて解決できる。
- [x] 新しい完全コード化レグを発明していない（`slice2` の `procedures` は空、または既存カタログへの参照のみ）。
- [x] 出発サンプルを置く場合、衝突／MSAW ガードが通り、VISIP を含まない。
- [x] 既定の到着クイックスタートと既存の SID 割当テストが退行しない。
- [x] README に出典一行があり、未コード範囲を実装済みと書かない。
- [x] CI 4 ジョブがマージ SHA で合格する。

## README

- 出典は概要の一行のみ（README と `aip/` のデータファイル）。
- 既定サンプルは従来どおり RJTT RNAV STAR 到着 5 機であること。VAMOS 出発サンプルは別ファイルから読めること（置く場合）。
- VISIP、残る RJTT SID、RJAA の SID／STAR は、不足プレートまたは従来方式のため未コードであること。
- 不足プレートの一覧は `aip/slice2-20261101.md` または本仕様を指す。

## 不足プレート（SID／STAR のみ。ベース `.../20261101/pdf/`）

取得対象は次の **48** ファイル。タイトルは eAIP AD-2 目次のまま。

### RJTT SID（10）

- `JP-AD-2.24.19-RJTT-en-JP.pdf` — SEKIYADO（従来）
- `JP-AD-2.24.21-RJTT-en-JP.pdf` — OPPAR（従来）
- `JP-AD-2.24.24-RJTT-en-JP.pdf` — ISOGO（従来）
- `JP-AD-2.24.13-RJTT-en-JP.pdf` — LAXAS-RNAV
- `JP-AD-2.24.14-RJTT-en-JP.pdf` — NINOX-RNAV
- `JP-AD-2.24.15-RJTT-en-JP.pdf` — TIARA-A/B/C-RNAV
- `JP-AD-2.24.16-RJTT-en-JP.pdf` — BEKLA-A/B/C-RNAV
- `JP-AD-2.24.17-RJTT-en-JP.pdf` — ROVER-A/B/C-RNAV
- `JP-AD-2.24.18-RJTT-en-JP.pdf` — RUTAS-RNAV
- `JP-AD-2.24.87-RJTT-en-JP.pdf` — GUSRO-RNAV

### RJTT STAR（18、34L/34R の 1A/1K/2C・2A/2K/1C 以外）

- `JP-AD-2.24.89-RJTT-en-JP.pdf` — SULPU-1S/1D, OLRAT-1A/1B（従来）
- `JP-AD-2.24.28-RJTT-en-JP.pdf` — OSHIMA, AKSEL, AROSA-2H-RNAV
- `JP-AD-2.24.29-RJTT-en-JP.pdf` — GODIN, POLIX-1H-RNAV
- `JP-AD-2.24.61-RJTT-en-JP.pdf` — OSHIMA-1N/2N-RNAV
- `JP-AD-2.24.30-RJTT-en-JP.pdf` — OSHIMA-1B/2B-RNAV
- `JP-AD-2.24.31-RJTT-en-JP.pdf` — AKSEL-1N/2N-RNAV
- `JP-AD-2.24.72-RJTT-en-JP.pdf` — AKSEL-1B/2B-RNAV
- `JP-AD-2.24.22-RJTT-en-JP.pdf` — AROSA-1N/2N-RNAV
- `JP-AD-2.24.25-RJTT-en-JP.pdf` — AROSA-1B/2B-RNAV
- `JP-AD-2.24.73-RJTT-en-JP.pdf` — GODIN-1S/1D-RNAV
- `JP-AD-2.24.74-RJTT-en-JP.pdf` — POLIX-1S/1D-RNAV
- `JP-AD-2.24.78-RJTT-en-JP.pdf` — OSHIMA-L/R-RNAV
- `JP-AD-2.24.79-RJTT-en-JP.pdf` — AKSEL-L/R-RNAV
- `JP-AD-2.24.80-RJTT-en-JP.pdf` — AROSA-L/R-RNAV
- `JP-AD-2.24.81-RJTT-en-JP.pdf` — GODIN-L/R-RNAV
- `JP-AD-2.24.82-RJTT-en-JP.pdf` — POLIX-L/R-RNAV
- `JP-AD-2.24.75-RJTT-en-JP.pdf` — OSHIMA, AKSEL, AROSA, MESSE-NIGHT-RNAV
- `JP-AD-2.24.76-RJTT-en-JP.pdf` — OSHIMA, AKSEL, AROSA, MESSE-V-RNAV

### RJAA SID（10）

- `JP-AD-2.24.17-RJAA-en-JP.pdf` — SAKURA（従来）
- `JP-AD-2.24.20-RJAA-en-JP.pdf` — AKAGI（従来）
- `JP-AD-2.24.22-RJAA-en-JP.pdf` — SUNNS（従来）
- `JP-AD-2.24.10-RJAA-en-JP.pdf` — TETRA-RNAV
- `JP-AD-2.24.11-RJAA-en-JP.pdf` — REDEK-RNAV
- `JP-AD-2.24.12-RJAA-en-JP.pdf` — PIGOK-RNAV
- `JP-AD-2.24.13-RJAA-en-JP.pdf` — GULBO-RNAV
- `JP-AD-2.24.14-RJAA-en-JP.pdf` — BORLO-RNAV
- `JP-AD-2.24.16-RJAA-en-JP.pdf` — PEDLA-RNAV
- `JP-AD-2.24.18-RJAA-en-JP.pdf` — OLVAN-RNAV

### RJAA STAR（10）

- `JP-AD-2.24.27-RJAA-en-JP.pdf` — SOUTH（従来）
- `JP-AD-2.24.28-RJAA-en-JP.pdf` — NORTH（従来）
- `JP-AD-2.24.25-RJAA-en-JP.pdf` — RUTAS E/T-RNAV
- `JP-AD-2.24.26-RJAA-en-JP.pdf` — SWAMP E/T-RNAV
- `JP-AD-2.24.30-RJAA-en-JP.pdf` — SUPOK E/T-RNAV
- `JP-AD-2.24.45-RJAA-en-JP.pdf` — LUBLA E/T-RNAV
- `JP-AD-2.24.46-RJAA-en-JP.pdf` — RUTAS G/N-RNAV
- `JP-AD-2.24.47-RJAA-en-JP.pdf` — SWAMP G/N-RNAV
- `JP-AD-2.24.48-RJAA-en-JP.pdf` — SUPOK G/N-RNAV
- `JP-AD-2.24.49-RJAA-en-JP.pdf` — LUBLA G/N-RNAV

既に手元にあり再取得不要: `JP-AD-2.24.12/26/27/32/53/71/88-RJTT-en-JP.pdf`、および両空港の `JP-AD-2-*-en-JP.pdf`。

優先して取ると第三スライスが進むのは、既対応滑走路向けの **RJTT RNAV SID**（`2.24.13`–`18` と `2.24.87`）と、**RJAA RNAV SID／STAR**（SID `2.24.10`–`14`/`16`/`18`、STAR `2.24.25`/`26`/`30`/`45`–`49`）。従来方式の SEKIYADO / VISIP / OPPAR / ISOGO / SAKURA / AKAGI / SUNNS / SULPU / SOUTH / NORTH は、エンジン側のレグ追加が別に要る。

## 未解決事項（Unresolved Questions）

- 出発サンプルをトップの第二ボタンにするか、JSON アップロード専用にするかは、到着既定を壊さなければどちらでもよい。README に手順を書く。
- ガードを通る機数と滑走路の組み合わせは、実装時の STCA／MSAW 実行結果で確定する。仕様は「衝突する組み合わせを残さない」ことだけを固定する。
- 不足プレート取得後の第三スライスで、カタログを `rjtt-sid-star-20261101.json` にマージするか、空港別ファイルにするかは、そのときの PR で決めてよい。
