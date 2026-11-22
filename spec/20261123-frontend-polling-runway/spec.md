# 位置ポーリングの明示的結果と、滑走路選択規則・経路折れ線の修正

## メタデータ

- **Status**: In progress
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-23
- **関連**: 外部レビューおよび検証（PR #117–#139）の PR-D（⑦、⑨、MSAW 決定、P-1、P-2、P-4）、[spec/20261122-runway-distance-rbl/spec.md](../20261122-runway-distance-rbl/spec.md)、[spec/20261122-msaw/spec.md](../20261122-msaw/spec.md)、[spec/20261123-nav-mode-validate/spec.md](../20261123-nav-mode-validate/spec.md)（PR-C / #143）、[spec/20261123-atis-revision/spec.md](../20261123-atis-revision/spec.md)（PR-B / #142）
- **リポジトリ配置**: `spec/20261123-frontend-polling-runway/spec.md`
- **前提 main**: `6413499`（#143 マージ後）

## 概要

レーダーの位置取得は、成功と失敗を型で区別する結果（`success | error` / `FetchResult`）を返す。失敗を空配列として扱わないので、通信エラーでスコープ上の機が消えることが型として表せない。位置ポーリングは飛行中の要求を重ねず、通し番号で遅い応答を捨てる。飛行計画パネルの再取得は、選択機が変わったら取り消す。進入端までの残距離表示と MSAW 進入廊は、**同じ一つの「最寄り中心線」規則**（廊内で横断距離の絶対値が最小、同値なら沿トラックが短い方）を使う。経路折れ線は、通過済みフィックスではなく、機体の現在位置から残りの最初のフィックスへ引き、残が 1 個でも描く。残が 0 なら描かない。

位置 API・滑走路 API・`flightPlanRoute` の形は変えない。Hold / ATIS / 航法モード（PR-A〜C）は対象外である。

## 背景・課題

### 現状

main `6413499`。PR-A〜C はマージ済み。表示とポーリングの契約は、レビュー時点と同じである。

- **P-1（CONFIRMED）**: `fetchAircraftLocation`（`Frontend/utility/api/location.ts`）は HTTP 失敗・例外でも `Aircraft[]` を返す。成功時だけ中身を埋め、それ以外は初期値の `[]` のまま返す。`radarCanvas` はそれをそのまま `setControllingAircrafts` に渡す。すでに機がいるスコープで 500 やネットワーク切断が起きると、機と固定ラベルが消える。本物の「機が 0 機」と区別できない。
- **P-2（CONFIRMED）**: 位置ポーリングは `setInterval` のたびに `fetchAircraftLocation` を待つことなく起動する。飛行中ガードも通し番号もない。遅い応答が新しい応答のあとに着くと、古い位置で上書きする。
- **P-4（CONFIRMED）**: `flightPlanDisplay` の初回取得だけは `cancelled` フラグがある。5 秒間隔と `FLIGHT_PLAN_CHANGED_EVENT` から呼ぶ `refresh()` は `fetchFlightPlan(callsign).then(setStatus)` だけで、選択機の切り替わりを見ない。A の再取得が飛ぶ → B を選ぶ → 遅い A 応答が着く、でパネルが A に戻る。
- **⑦（CONFIRMED、#124 の規則どおり）**: `findThresholdDistance` は廊に入っている着陸可能末端のうち、**沿トラックが最短**のものを選ぶ。RJTT 16L / 16R（および 34L / 34R）は半幅 1.0 NM の廊が重なる。16L 中心線上 5 NM では 16R の沿トラックがわずかに短い（再現では 16R 4.927 NM、横断は 0.8 NM 超）ため、データブロックが隣の滑走路を出す。16R / 34R 中心線は自分を選ぶ（非対称）。
- **MSAW 決定事項（現行は不一致）**: `MsawDetector.checkApproachCorridor` はカタログ順の**最初の一致**でフロアを決める。表示の最短沿トラックとも、後述の最寄り中心線とも違う。同じ点でも選ばれる滑走路（と沿トラック、フロア）が側によって変わる。
- **⑨（CONFIRMED）**: 位置 API の `flightPlanRoute` は現在指数以降の残フィックスだけを返す（`GetAllAircraftLocationsWithRiskUseCase.toFlightPlanRoute`）。`drawFlightPlanRoute` は点が 2 個未満なら描かず、2 個以上でも最初の残フィックスから折れる。機体→次フィックスの辺が無い。残 1 個（まだそのフィックスへ向かっている）で緑の経路が消える。

再現は `/workspace/horus-ops/reviews/repro/frontend/` の `locationAndPanel.repro.test.tsx`、`geometry.repro.test.ts`、`drawRoute.repro.test.ts`、および `/workspace/horus-ops/reviews/repro/backend/MsawOverlapReproTest.java` にある。これらは**現行の不具合を断言**している。

### 課題（Problem Statement）

- 位置取得の失敗が空の成功と同じ型で表され、エラー時に機が消える（P-1）。
- 遅い位置応答と、選択機切替後の遅いパネル応答が、新しい表示を上書きする（P-2、P-4）。
- 平行滑走路の中心線上で、残距離と MSAW が別の規則・別の滑走路を使う（⑦、MSAW 決定）。
- 残フィックスが 1 個になると経路折れ線が消え、2 個以上でも機体からではなく通過直後のフィックスから始まる（⑨）。

### なぜ今か（Motivation）

検証の推奨順は PR-A → PR-B → PR-C → PR-D である。A〜C はマージ済みで、レビュー #117–#139 の残りはこの PR-D だけである。位置 API の形を変えず、フロントの結果型・ポーリング順序と、既に決めた滑走路規則・折れ線の始点に閉じられる。

---

## 方針

### 決定方針（Decision）

1. **位置取得は明示的な `FetchResult` を返す（P-1）。失敗を空配列で表さない**
   - クライアントモジュールの戻り値を次の判別可能なユニオンにする（名前は同等なら可）。

     ```ts
     type FetchResult<T> = { kind: "ok"; data: T } | { kind: "error" };
     ```

   - `fetchAircraftLocation` は `FetchResult<Aircraft[]>` を返す。HTTP 非 OK とネットワーク例外は `{ kind: "error" }`。成功で機が 0 機のときだけ `{ kind: "ok"; data: [] }`。
   - 呼び出し側（`radarCanvas` の位置ポーリング）は `error` では最後に適用した機リストを残す。`setControllingAircrafts` もラベル再配置も、成功結果のときだけ行う。ピン留めラベルとターゲットは消えない。
   - 「失敗なのに空のスコープ」は型として組み立てできない。`Aircraft[]` を失敗の合図に使う公開関数は残さない。

2. **位置ポーリングは飛行中ガードと通し番号を両方持つ（P-2）**
   - すでに位置要求が飛行中なら、次の間隔では新しい要求を始めない（飛行中ガード）。
   - 発行する要求には単調増加の通し番号を付ける。応答を状態へ適用するのは、その番号が「最後に適用した番号」より新しいときに限る。古い応答は捨てる。
   - シミュレーション速度の取得が同じ `Promise.all` に乗っているなら、機リストの適用判定は位置結果の通し番号に従う。速度だけ先に更新してよいが、古い位置で機リストを上書きしてはならない。

3. **飛行計画パネルの再取得は、選択機が変わったら取り消す（P-4）**
   - 初回取得、5 秒間隔、`FLIGHT_PLAN_CHANGED_EVENT` を含む**すべての**再取得経路に、打ち切りを付ける。`AbortController` でも、応答適用前のコールサイン照合（または同等の `cancelled`）でもよい。
   - 選択が A から B に変わったあと、A 向けに出していた応答はパネルを更新しない。B の表示が A に戻ってはならない。

4. **残距離と MSAW は、既決の「最寄り中心線」規則を一つの関数で共有する（⑦、MSAW 決定）**
   - 廊の定義は変えない。着陸可能（`usableForLanding !== false`。RJTT 05 は除外）なカタログ末端について、進入端から外側へ沿コース 0 NM 超〜 10 NM 以下、左右横断 1.0 NM 以下（現行の `APPROACH_CORRIDOR_*` / `msa-areas.json` と同じ）。目的地 ICAO の一致は必須にしない（#124 どおり）。
   - **選択規則（既決）**: 廊に入っている末端のうち、**横断距離の絶対値 `|xtk|` が最小**のものを選ぶ。同値なら**沿トラックが短い**方。点がどの廊にも入らなければ対象なし（表示なし / MSAW は進入フロアを使わない）。
   - この規則は**側ごとに一つの関数**にだけ置く。推奨名は両側で同じ `selectClosestCenterlineRunway`（同等の単一名でも可）。Frontend の残距離（今日の `findThresholdDistance`）と Backend の `MsawDetector.checkApproachCorridor`（および予測位置での同じ判定）は、この関数を呼ぶだけにする。 greppable: その名前を検索すると、選択の定義と、表示・MSAW・ゴールデンテストの呼び出しだけがヒットし、別のループで「最初の一致」や「最短沿トラック」を選ぶ実装が残らない。
   - 共通ゴールデン表を両側のテストで共有する（値は現行 RJTT カタログ）。対象: 16L / 16R / 34L / 34R の中心線上、進入端から外側 **1 / 5 / 9 NM**。各点は**その中心線の滑走路自身**を選ぶ。16R / 34R も自分を選ぶ（非対称の例外を残さない）。

5. **経路折れ線は機体の現在位置から、残りの最初のフィックスへ引く（⑨）**
   - 位置 API の `flightPlanRoute` は、いまどおり現在指数以降の残フィックスだけを返す。形は変えない（機体位置を配列先頭に足さない）。
   - 描画は、残が 1 個以上なら、機体の現在位置（`geoPosition`）→ 残りの先頭フィックス → その後の残フィックス、の順で折線を描く。残が 0（または `null` / 欠如）なら描かない。
   - 「残 2 個以上のときだけ、先頭フィックスから描く」は採用しない。通過済みフィックスから始める描画もしない。

6. **API の形は変えない**
   - `GET /api/aircraft/location/all` のキーと意味、`flightPlanRoute` の残フィックス配列、`GET /api/ats/runways`、指示系・ATIS の契約は、本スライスで足さない・削らない・破壊しない。MSAW の選択はサーバ内部の判定だけを変える（`msawAlert` のフィールド形は同じ）。

### 検討した他案（Alternatives considered）

- **案 A: 失敗時も `[]` を返し、呼び出し側が「空は失敗かもしれない」と解釈する。** 本物の 0 機と区別できず、P-1 が型で禁止できない。却下。
- **案 B: 飛行中ガードだけ、または通し番号だけ。** ガードだけでは既に出た遅い応答を捨てられない。番号だけでも過負荷で要求が積み上がる。両方を採用する。
- **案 C: 位置 API に世代番号や `Last-Modified` を足す。** 順序の正しさはクライアントで足り、API 形を変える理由にならない。却下。
- **案 D: 滑走路選択を #124 の最短沿トラックのままにする。** ⑦ のまま隣の平行滑走路を出す。既決の最寄り中心線に反する。却下。
- **案 E: MSAW はカタログ順の最初の一致のまま、表示だけ直す。** 同じ点でフロアと残距離の滑走路が食い違う。既決に反する。却下。
- **案 F: Frontend と Backend で一つの言語の共有パッケージにする。** このスライスには重い。関数を側ごとに一つ置き、同じゴールデン表でDrift を防ぐ。
- **案 G: `flightPlanRoute` の先頭に機体位置を入れて API を変える。** ⑨ は描画の始点の問題であり、既存クライアントと DTO を動かさずフロントで足りる。却下。
- **案 H: 残 1 個のときだけ点または短い線をフィックスに描き、機体からは引かない。** 経路の接続が見えない。却下。

### トレードオフ（Tradeoffs）

- `FetchResult` は HTTP ではなくクライアントモジュールの契約である。呼び出し箇所の型をすべて直す必要があるが、それが検査になる。
- 飛行中ガードは、遅いサーバのとき最大で約 1 間隔ぶん位置更新が疎になる。ちらつきや巻き戻りより受容する。
- `|xtk|` 最小は、隣の平行滑走路の沿トラックがわずかに短い場合でも、乗っている中心線を選ぶ（16L 中心線 5 NM で 16L 5.000 対 16R 4.927）。残距離の数字は #124 よりわずかに長くなり得る。それが正しい表示である。
- 規則を二言語で持つとDriftし得る。共通ゴールデン表（1 / 5 / 9 NM × 4 中心線）を CI で両側に課して防ぐ。
- 機体→次フィックスの辺は、機が経路から外れているとき経路の飛行済み部分ではなく現在位置からの直線になる。残りの案内として正しい。

---

## 完了条件（Success Criteria）

型と静的検査をテストより先に満たす（違法状態を表現不能にする）。そのあと、検証が指定した反転再現、組み合わせ、API 非変更を残す。

1. **型・静的検査（先に）**
   - `fetchAircraftLocation` の戻り値は `FetchResult<Aircraft[]>`（または同等の判別ユニオン）である。失敗経路が `Aircraft[]`（とくに `[]`）を返すことは、型として通らない。
   - 滑走路選択は、両側で単一の名前付き関数（推奨: `selectClosestCenterlineRunway`）にだけある。その名前をリポジトリで検索すると、定義と、残距離表示・MSAW 進入廊・ゴールデンテストの呼び出しに限られる。`findThresholdDistance` や `checkApproachCorridor` に、別の選択ループ（最初の一致 / 最短沿トラック単独）が残らない。
   - Frontend の型検査とビルド、Backend のコンパイルが通る。位置 DTO の既存キーを壊さない。
2. **反転再現テスト（必須）**
   - `/workspace/horus-ops/reviews/repro/frontend/geometry.repro.test.ts`（⑦）を反転し、Frontend の恒久テストにする。16L / 34L 中心線上 5 NM は**自分**の滑走路を選び、選ばれた横断の絶対値は 0 に近い（隣を選ぶときの 0.8 NM 超ではない）。16R / 34R 中心線も自分を選ぶ。
   - 同じゴールデン表（RJTT 16L / 16R / 34L / 34R × 1 / 5 / 9 NM）を Backend でも使う。`/workspace/horus-ops/reviews/repro/backend/MsawOverlapReproTest.java` を、印字だけでなく「各中心線がその滑走路自身を選ぶ」断言に反転し、恒久テストにする。Frontend と Backend で、同じ点の選択結果が一致する。
   - `/workspace/horus-ops/reviews/repro/frontend/drawRoute.repro.test.ts`（⑨）を反転する。残 2 個では機体→先頭フィックスと先頭→次の辺が描かれる（`stroke` あり。先頭フィックスだけを始点にした 1 本の `lineTo` ではない）。残 1 個でも `stroke` があり、機体→そのフィックスである。残 0 / `null` では `stroke` しない。
   - `/workspace/horus-ops/reviews/repro/frontend/locationAndPanel.repro.test.tsx` を反転する。
     - **P-1**: 既存機リストを渡して HTTP 500 およびネットワーク例外のとき、戻り値は `{ kind: "error" }` であり `[]` ではない。
     - **P-4**: A の再取得が飛行中に B へ切り替えたあと、遅い A 応答が着いてもパネルは B（例: `BFIXX`）のまま、A（例: `AFIXX`）に戻らない。
3. **組み合わせテスト**
   - 位置応答の前後が入れ替わる対（古い番号が新しい番号のあとに着く）→ 新しい状態だけが表示に残る。
   - 成功ポーリングのあいだに 1 回の取得失敗を挟む → 機（ターゲット）とピン留めラベルが残る。失敗を空スコープとして適用しない。
   - STAR（または通常の飛行計画）で残フィックスが 1 個のとき、機体からそのフィックスへ線が描かれる。それを通過して残 0 になると線は消える。
4. **API の形を変えない**
   - `GET /api/aircraft/location/all`、`flightPlanRoute`、滑走路 API、指示・ATIS の JSON キーを本スライスで追加・削除・破壊しない。既存の位置 DTO テストおよびフロントの DTO 型が、キー集合について退行しない。
5. **文書**
   - [spec/20261122-runway-distance-rbl/spec.md](../20261122-runway-distance-rbl/spec.md) の §2（滑走路の選び方）を、本スライスの最寄り中心線規則（`|xtk|` 最小、同値なら沿トラック短い方）へ更新する。Frontend README（進入端距離の段落）と、Backend README の MSAW 進入廊の説明を同じ規則に揃える。経路折れ線（機体→次フィックス、残 1 でも描く）と、位置取得失敗時に機を消さないことを Frontend README に書く。
6. **CI**
   - 4 ジョブがマージ SHA で合格する。

---

## 影響範囲

- **Frontend**: `utility/api/location.ts` の戻り値、`radarCanvas` の位置ポーリング、`flightPlanDisplay` の再取得、`utility/measure/rangeBearing.ts` の滑走路選択、`utility/aircraft/drawAircraft.ts` の経路描画。
- **Backend**: `MsawDetector.checkApproachCorridor`（およびそれが使う選択）のみ。位置 DTO の組み立てと `toFlightPlanRoute` の形は変えない。
- **テスト**: 上記の反転再現、ゴールデン表、組み合わせ。既存の距離・廊判定・MSAW テストは、⑦ / MSAW 決定で期待滑走路が変わるものだけ直す。
- **文書**: `spec/20261122-runway-distance-rbl` §2、Frontend README、Backend README（MSAW）。ルート README が進入端距離に触れる一文があれば矛盾しないよう揃える。

---

## 範囲外（Non-goals）

- 位置 API・滑走路 API・`flightPlanRoute` の形の変更。機体位置を経路配列に足すこと。
- 進入廊の長さ・幅、MSAW フロア勾配、専用 MSAW エンドポイント。
- ラベル矩形の幅を描画テキスト幅に合わせること（レビューの未決事項）。
- ⑧ FighterJet / Helicopter の無風 3 引数 `AircraftVector`。
- DEPART の GS / IAS 既定（レビューの未決事項）。
- PR-A〜C で扱った Hold の WCA、タワー移管予測、ATIS 下書き / `revision`、航法モード型。
- SID / STAR カタログの拡充、新しい AIP ダウンロード。
- 位置ポーリング間隔そのものの変更、STCA の判定。
- 残距離の表示トグル、最終進入の距離チケット。

---

## 実装計画

### Phase 1（型）

- `FetchResult` を導入し、`fetchAircraftLocation` の戻り値を切り替える。失敗が `[]` になる経路を型で閉じる。呼び出し側を成功 / 失敗で分岐する。コンパイルと型検査を通す。
- 滑走路選択関数を単一名で両側に切り出し、既存の最短沿トラック / 最初の一致ループをそこに置き換える準備をする（この時点で規則を最寄り中心線にする）。

### Phase 2（ポーリングとパネル）

- 位置ポーリングに飛行中ガードと通し番号を付ける。`error` では機リストを更新しない。
- 飛行計画パネルの全再取得経路に打ち切りを付ける。

### Phase 3（経路折れ線）

- `drawFlightPlanRoute` を、機体現在位置→残フィックス（1 個以上で描画、0 で非表示）に変える。API は触らない。

### Phase 4（検証と文書）

- `geometry` / `drawRoute` / `locationAndPanel` の再現と `MsawOverlapReproTest` を反転して恒久化する。ゴールデン表と組み合わせ 3 件を足す。`spec/20261122-runway-distance-rbl` §2 と README を更新する。

---

## 検証

- [ ] `fetchAircraftLocation` が `FetchResult` を返し、失敗を `[]` として表せない。
- [ ] 滑走路選択関数の名前が greppable で、表示と MSAW の唯一の選択実装である。
- [ ] 反転した `geometry.repro`、`drawRoute.repro`、`locationAndPanel.repro`（P-1 と P-4）、`MsawOverlapReproTest` が通る。
- [ ] 共通ゴールデン表（16L / 16R / 34L / 34R × 1 / 5 / 9 NM）で、両側が自分の中心線を選ぶ。
- [ ] 組み合わせ 3 件（位置の順序逆転、失敗を挟んでも機とピンが残る、残 1 フィックスの線→通過後に消える）が通る。
- [ ] 位置 API・`flightPlanRoute`・滑走路 API のキーが本スライスで変わっていない。
- [ ] `spec/20261122-runway-distance-rbl` §2 と README が最寄り中心線規則と経路・ポーリング契約を述べている。
- [ ] 既存テストを、本機能用の期待値修正以外で緩めずに通す。CI 4 ジョブがマージ SHA で合格する。

再現テストの置き場所（検証 README どおり、恒久化するときはバグ断言を逆にする）:

- `/workspace/horus-ops/reviews/repro/frontend/geometry.repro.test.ts` — ⑦
- `/workspace/horus-ops/reviews/repro/frontend/drawRoute.repro.test.ts` — ⑨
- `/workspace/horus-ops/reviews/repro/frontend/locationAndPanel.repro.test.tsx` — P-1、P-4
- `/workspace/horus-ops/reviews/repro/backend/MsawOverlapReproTest.java` — MSAW 決定（各中心線が自分の滑走路を選ぶ）

## README

- **Frontend README**（進入端距離の段落、いま沿コース投影と廊の寸法だけを述べている箇所）: 平行滑走路が重なるときは `|xtk|` 最小、同値なら沿トラックが短い末端を使うこと。経路折れ線は機体の現在位置から残りの次フィックスへ引き、残 1 個でも描き、残 0 で消すこと。位置一覧の取得に失敗しても、最後の機リストとピン留めラベルを残すこと（空配列で消さないこと）。
- **Backend README**（MSAW 進入廊の箇条書き）: 重なる進入廊では表示と同じ最寄り中心線規則で末端を選び、その沿トラックでフロアを計算すること。
- **ルート README**: 進入端距離に触れる一文があれば、選択規則と矛盾しない一言に揃える。
- **[spec/20261122-runway-distance-rbl/spec.md](../20261122-runway-distance-rbl/spec.md) §2**: 「入っている滑走路が複数なら沿コース距離が短い方」を、本スライスの規則に更新する。廊の寸法と「目的地 ICAO は必須にしない」は維持する。

## 未解決事項（Unresolved Questions）

- `FetchResult` の判別子を `kind: "ok" | "error"` にするか `success` にするかは、ユニオンが一意で失敗がデータと混ざらなければどちらでもよい。検証文案の `{ kind: "ok"; data: T } | { kind: "error" }` を推奨する。
- パネル打ち切りを `AbortController` にするかコールサイン照合にするかは、P-4 の反転が通ればどちらでもよい。
- ゴールデン表の座標は、実装時点の `RunwayCatalog` / `GET /api/ats/runways` の RJTT 調査値を使う。カタログ値が main で変わっていれば、中心線の作り方（進入コースの逆方位に 1 / 5 / 9 NM）は変えず、閾値座標だけ追従する。
