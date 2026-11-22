# RNAV SID/STAR 第三スライス（22 プレートの完全コード化）

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-23
- **関連**: [spec/20261122-sid-star/spec.md](../20261122-sid-star/spec.md)（#139）、[spec/20261123-sid-star-slice2/spec.md](../20261123-sid-star-slice2/spec.md)（draft-16）、Issue [#114](https://github.com/HorusATC/Horus/issues/114)
- **リポジトリ配置**: `spec/20261123-rnav-slice3/spec.md`
- **前提 main**: `9f4c999`（PR-A〜D / #141–#144 マージ後）
- **抽出データ**: `aip/slice3-20261101.json` および `aip/slice3-20261101.md`
- **触れないもの**: スライス 2 の出発サンプル PR（他エージェントが `spec-draft-16` から作成中）。`aip/rjtt-sid-star-20261101.json` と既定の到着クイックスタートは変更しない。

## 概要

AIRAC 1 Nov 2026 の RNAV プレート 22 枚（RJTT `AD 2.24.13–18, 87` と RJAA `AD 2.24.10–14, 16, 18, 25, 26, 30, 45–49`）から、コーディング表と座標表が完全に読める SID／STAR を抽出した。対象は **114 本**（SID 滑走路バリアント 91、SID トランジション 7、STAR 16）。読めない行のために除外した手続きは **0**。パス記述子はすべてエンジンが既に持つ IF / TF / VA / DF / HOLD（HOLD はカタログのみ）である。

このスライスの成果物は、スライス 1 カタログへ**足す**データと、それをシミュレータで割り当て可能にする実装である。スライス 1 の VAMOS と RJTT STAR 15 は置き換えない。出典は次の一行だけを README とデータファイルに書く。UI に警告バナーは出さない。

> Derived from AIP Japan (AIRAC 1 Nov 2026), may not be current, edited by the Horus project and not government-made.

## 背景・課題

### 現状（main `9f4c999`）

- カタログは `aip/rjtt-sid-star-20261101.json`。実装済みは RJTT の RNAV STAR 15（見出しはいずれも RWY 34R/34L）と VAMOS FOUR（6 滑走路系＋ TATEYAMA / DRAKY）。VISIP ONE は割り当て不能のまま。
- `ProcedureCatalog.listByAirport` は空港コードで既に絞り込める。クラスパス資源は `fix/rjtt-sid-star-20261101.json` の一つだけ。
- フロントエンドの手続き ID 型と割り当て UI（`Frontend/types/rjttProcedureIds.ts`）は RJTT に固定されている。
- `airports.json` に RJAA（成田）は既にある。`runways.json` に RJAA の 16R / 34L / 16L / 34R の 4 本もある。空港そのものを足す作業ではない。
- エンジンの `ProcedurePathDescriptor` は IF, TF, VA, DF, HOLD, UNKNOWN。VA は真方位で指定高度まで、その後 DF、その後 TF。公示ホールドの自動進入と、パス表 KIAS の自動適用は未実装（スライス 1 と同じ）。
- スライス 2 の結論は、当時手元にあった PDF では新規に完全コード化できる手続きが無い、というものだった。不足 48 プレートのうち優先度の高い RNAV 22 枚が、本スライスの入力である。

### 抽出結果の要点

- スキーマはスライス 1 と同じ（`sourceNotice`, `package`, `uncertain`, `waypoints`, `procedures`, `crossCheck`）。二空港のため `package.airports` 配列を足してある。各手続きは従来どおり `airportIcao` と `sourcePages` を持つ。
- ウェイポイント 166。プレート間の DMS 衝突は 0。既存 `waypoints.json` の 122 識別子とは座標が食い違わない。新規 44 は中間フィックス（`A4*`, `A6*`, `AA*`, `T6*`, `TT503`, `TT631`, `GUGVA`）。
- 磁差は印刷どおり（RJTT **-7.9**、RJAA **-7.5**）。Horus の機首は測地／真方位なので、シミュレータ機首に磁差を掛けない。
- 高度の `FLnnn` はフィートに換算せず `{constraint, fl}` で格納する。
- パス表の符号なし KIAS は制限種別を推測しない（`uncertain: true`）。符号付きの `-250`（SUPOK G の AA657）だけは、印刷されたマイナスを at-or-below として記録する。
- 大圏検査（球半径 6371 km、1 NM = 1852 m）で 0.05 NM / 0.05° を超えた対は 16。最大は 0.069 NM / 0.073°。球と公示値の丸めとして扱い、座標も針路も直していない。

## 方針

### 決定

1. **データを先に固定し、実装はそれに合わせる。** 正本は `aip/slice3-20261101.json`。コーディング表が読める手続きはすべて載せる。読めない値は作らない。
2. **カタログはスライス 1 ファイルを置き換えず、第三スライスを追加で読む。** Gradle の `processResources` でクラスパスへコピーし、`ProcedureCatalog` が両方（または結合結果）を載せる。スライス 1 JSON は編集しない。
3. **RJAA は空港追加ではない。** 空港と 4 滑走路は main に既にある。RJAA の 48 本はカタログと `GET /api/procedures?airport=RJAA` で返す。フロントの手続き ID ユニオンと割り当て操作を RJAA にも開ける。UI 全体の作り直しが過大なら、データと API だけの段階を許容する（第二の小さなスライスに分けてよい）が、既定方針は割り当て可能にすること。
4. **エンジンが持たないレグ種別はhackしない。** 本 22 プレートには該当が無い。将来出たらその手続きを除外リストに載せ、未知種別を TF 等へ読み替えない。
5. **`waypoints.json` の既存座標は上書きしない。** 新規識別子だけを必要なら追加する。手続きパスの座標の正本は AIP 抽出側である。
6. **公示ホールドはカタログに載せるが、自動ホールドは範囲外**（スライス 1 と同じ）。
7. **出典は一行だけ。** README とデータファイル。UI バナーは出さない。Horus はシミュレータであり、「運航に使えない」ことを前面に出さない。

### 検討した他案

| 案 | 内容 | 判断 |
|---|---|---|
| A. 22 プレート分を本スライスでカタログ＋API＋割り当てまで入れる（採用） | RJAA 空港データは既にある。利用者が RJAA 機に手続きを付けられる。 | 採用。影響はカタログ結合、フロント ID、割り当て UI に閉じる。 |
| B. RJAA はデータのみ。割り当ては次スライス | フロント差分を小さくできる。 | 許容されるフォールバック。空港新設が理由ではない。UI が過大だと分かったときだけ選ぶ。 |
| C. スライス 1 JSON に 114 本を追記して単一ファイルにする | 読込は単純。 | 却下。他エージェントのスライス 2 PR とスライス 1 正本を汚す。追加ファイルの方が差分が追いやすい。 |
| D. 未対応レグを近い種別へ読み替える | 今は不要。 | 禁止。 |

### トレードオフ

- カタログが二ファイルになる。読込とスキーマ検査を両方に掛けるコストを引き受ける。
- フロントを RJTT 専用型から空港付きの ID へ広げる必要がある。型を雑に `string` へ戻さない。
- 幾何フラグ 16 件を「誤り」として手修正すると、AIP を発明で上書きすることになる。0.05 を超えても 0.10 未満は丸めとして残す。

## 完了条件

1. **スキーマ／静的検査が先。** `slice3-20261101.json` がスライス 1 と同じ手続き・レグ・ウェイポイント契約を満たす（必須フィールド、パス記述子列挙、高度が `ft` または `fl`、符号なし速度の `uncertain`）。検査が通るまでシミュレータ実行やシナリオ追加に進まない。
2. **手続きごとの幾何と制約の検査。** 抽出表に対し、両端座標と公示距離／真針路がある TF は大圏検査する（方法は JSON の `crossCheck`）。高度・速度・fly-over・磁差が表の値と一致する。0.05 をわずかに超える対は `flaggedPairs` に残り、値は修正しない。
3. **エンジンが持たないレグ種別は除外として列挙し、hack しない。** 本スライスの 114 本には該当なし。HOLD はカタログのみで、自動進入を実装しない。
4. **README**（および必要なら Backend README のカタログ説明）に、出典一行と、カタログがスライス 1＋スライス 3 の二ファイルになったことを書く。UI バナーは追加しない。
5. **範囲外リスト**（本 spec の該当節）が実装 PR に残っている。
6. RJAA を割り当て可能にする場合、`listByAirport("RJAA")` とフロントの割り当てが、抽出 ID（例: `TETRA8.RWY16R`, `RUTASE`）を受け付ける。データのみに留める場合は、その旨を PR と README に明示する。
7. スライス 2 の出発サンプル PR、既定到着クイックスタート、スライス 1 JSON を変更していない。

## 影響範囲

- `aip/slice3-20261101.json` のクラスパスへのコピー（`processResources` または同等）。
- `ProcedureCatalog`：第二資源の読込、既存の `listByAirport` をそのまま使う。
- フロント：手続き ID 型、割り当て操作、空港切り替え時の手続きリスト。RJTT 専用ファイル名に依存している箇所。
- 新規ウェイポイント 44 を経路描画やカタログ解決が必要とするなら、`waypoints.json` への**追加のみ**（既存座標は変更しない）。
- README の出典とカタログ説明。
- 触れない：スライス 1 データ、スライス 2 PR、ATIS / ナビモード / フロントポーリング（PR-B〜D）、既定到着クイックスタート、磁気モデル、STCA/MSAW の仕様変更（新しい出発をサンプルに足す場合のみ、既存どおり衝突ガードを通す）。

## 範囲外

- 従来方式 SID／STAR（VISIP、および draft-16 に残る未取得のコンベンショナル・プレート）。
- 計器進入、ミスドアプローチ、自動ホールド、パス表 KIAS の自動適用、上昇勾配の強制。
- 磁差の適用、地球楕円体への針路再計算、公示座標の「修正」。
- スライス 1 の VAMOS／RJTT STAR の再抽出、スライス 2 出発サンプル、既定到着シナリオの変更。
- 空港の新規追加（RJAA は既にある）。滑走路形状の作り直し。
- `waypoints.json` 既存エントリの上書き。
- UI の出典警告バナー、「運航に使えない」類の強調。
- 22 枚に含まれない残プレートの推測や、読めない叙述の復元。

## 実装計画

### Phase 1 — 静的検査

- スライス 1 と同じスキーマ検査を `slice3-20261101.json` に掛ける（型、必須、パス記述子、高度、速度、シリアル連続、フィックスが waypoints ブロックに存在する）。
- `crossCheck.flaggedPairs` と `coordinateConflicts`（空）、`horusWaypointsJson` をレビューし、0.05 超過を丸めとして記録する。
- エンジン列挙に無い記述子が 0 件であることを断言する。

### Phase 2 — カタログ結合

- `aip/slice3-20261101.json` をリソースへコピーする。
- `ProcedureCatalog` がスライス 1 と 3 を両方読む。ID 衝突が無いこと（本抽出は VAMOS / OSHIMA 等を含まない）をテストする。
- `GET /api/procedures?airport=RJTT` は既存 15＋VAMOS に加え、本スライスの RJTT 66 本を返す。`airport=RJAA` は 48 本を返す。

### Phase 3 — ウェイポイント

- 抽出にある 44 の未登録識別子を `waypoints.json` に追加する（AIP の DMS から計算した度）。既存 122 は触らない。
- パス解決がカタログ内座標だけを見るなら、この Phase は描画や他機能が `waypoints.json` を要求する場合に限る。

### Phase 4 — フロントの割り当て

- 手続き ID 型を RJTT 固定から、空港＋本スライス ID を含むユニオン（または空港で切り替える定義）へ広げる。
- RJAA 機（または空港切替）で TETRA / RUTAS E 等が割り当てられること。UI が過大なら Phase 4 を次スライスに送り、Phase 2 までを本 PR の完了とする。

### Phase 5 — README

- 出典一行、カタログが二ファイルであること、RJAA 手続きの扱い（割り当て可またはデータのみ）を書く。バナーは出さない。

## 検証

- [x] スライス 3 JSON のスキーマ検査が通る（必須フィールド、パス記述子、高度 ft/fl、符号なし速度の uncertain、シリアル連続、フィックスが waypoints に存在）。
- [x] 手続きごとの幾何・制約検査が通る（`crossCheck.flaggedPairs` 16 件を丸めとして残し、値は修正しない）。代表例:
  - `LAXAS4.RWY16R`：VA 158°M / 150.0°T、+500、DF T6R11、TF TAURA +9000 9.0 NM、IMOLA +FL150、LAXAS +FL170。
  - `TETRA8.RWY16R`：VA 157 / 149.6 +600、DF ASPEN fly-over、TF A6R11 4.5 NM … TF TETRA。
  - `TETRA8.AGRIS`：IF TETRA +12000、TF RADIX +FL150、WHARF +FL180、AGRIS +FL200。
  - `PEDLA1.RWY34L`：DF ASTRA fly-over。
  - `RUTASE`：IF RUTAS … SIGMA at-or-below 8000、ホールド 4（RUTAS / AQUOS / CORGI / ELGAR）。
  - `SUPOKG`：AA657 速度 -250（at or below 250 KIAS）、VOGUE 符号なし 210。
  - `OLVAN2.RWY16L` と `ROVER3A.RWY16L`：CHANGE で分断された行が表どおり再結合されている。
- [x] `listByAirport("RJTT")` が 90、`listByAirport("RJAA")` が 48。エンジン未対応レグ種別は 0。
- [x] 既定の到着クイックスタートと VAMOS 出発サンプルの 10 分ガードが退行しない。スライス 1 JSON を変更していない。
- [x] README（root / Backend / Frontend）に出典一行と二ファイルカタログ、RJAA 割当可を書いた。UI バナーなし。
- [x] CI 4 ジョブが最終 head で合格する。

## README

次を追記する（文言はこのまま、または同等の一行）。

> Derived from AIP Japan (AIRAC 1 Nov 2026), may not be current, edited by the Horus project and not government-made.

カタログ説明には、SID／STAR が `aip/rjtt-sid-star-20261101.json`（スライス 1）と `aip/slice3-20261101.json`（本スライス）から載ること、RJAA の扱い、を書く。UI に出典バナーは出さない。

## 未解決事項

- Phase 4（フロント割り当て）を本 PR に含めるか、データ＋API だけにして次へ送るか。既定は含める。過大ならフォールバックを明示する。
- 新規 44 フィックスを `waypoints.json` に足すタイミング。パス描画がカタログ座標だけで足りるなら必須ではない。
- draft-16 に残るコンベンショナル・プレートを、入手後に別スライスで扱うこと。本スライスでは開かない。
- 公示ホールドの自動進入と符号なし KIAS の適用は、従来どおり別課題。
