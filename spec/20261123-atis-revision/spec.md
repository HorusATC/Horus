# ATIS の下書きとサーバ状態の分離、および revision による更新競合の検出

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-23
- **関連**: 外部レビューおよび検証（PR #117–#139）の PR-B（①、⑩a、⑩b、決定事項 4）、[spec/20261122-atis-runway-in-use/spec.md](../20261122-atis-runway-in-use/spec.md)、[spec/20261122-uniform-wind/spec.md](../20261122-uniform-wind/spec.md)、[spec/20261122-atis-qnh/spec.md](../20261122-atis-qnh/spec.md)、[spec/20261123-star-quickstart-scenario/spec.md](../20261123-star-quickstart-scenario/spec.md)、[spec/20261123-hold-handoff-wind/spec.md](../20261123-hold-handoff-wind/spec.md)（PR-A / #141）
- **リポジトリ配置**: `spec/20261123-atis-revision/spec.md`
- **前提 main**: `c739fb1`（#141 マージ後）

## 概要

Controller の ATIS パネルで、風・QNH の未保存入力（下書き）が 1 秒ポーリングで消えないようにし、保存成功後だけサーバ状態を入力へ反映する。ATIS の GET / PUT 応答はクライアント側の通し番号で順序付けし、遅れた応答は捨てる。`GET /api/atis` に空港ごとの加算フィールド `revision` を載せ、`PUT /api/atis/{icao}` に任意の `expectedRevision` を足す。不一致なら状態を変えず 409 と現在の状態を返す。`expectedRevision` を付けない既存クライアントは、今日と同じ振る舞いを続ける。風向・風速・QNH の小数入力は切り捨てず、既存の範囲メッセージで拒否する。

対象は検証ドキュメントの PR-B である（確認済み ①、⑩a、⑩b、決定事項 4）。Hold / タワー移管（PR-A、済）および位置ポーリングや経路描画（PR-C / PR-D）は含めない。

## 背景・課題

### 現状

main `c739fb1`（#140 の STAR 既定サンプル、#141 の Hold / ハンドオフ風修正のあと）。ATIS の編集パネル自体は #136 / #138 のままである。

- Controller の `atisPanel.tsx` は約 1 秒で `GET /api/atis` をポーリングする。`current` は毎回新しいオブジェクト参照になる。風・QNH 入力をサーバ値で上書きする `useEffect` の依存に `current` が入っているため、値が同じでも未保存の入力が消える（①、`atisPanel.tsx` 132–146 行付近）。既存の `atisPanel.test.tsx` は `mockResolvedValue([sample])` で同一配列を返すため、この経路を見ていない。
- ポーリング GET と滑走路 PUT に順序保証が無い。PUT で 34L / BRAVO にしたあと、その前に出た GET が遅れて着くと、表示が 16R / ALPHA に戻る（⑩a）。
- 風・QNH・CALM・Clear の PUT は、パネルが持っている `runwayInUse` を必ず付けて送る。他クライアントが滑走路を変えたあとでも、古い滑走路付きの風 PUT は滑走路変更として受理され、識別も進む（⑩b、`AtisStaleRunwayReproTest`）。`runwayInUse` は今日も必須である。
- 風向・風速・QNH は `Number.parseInt` で読むため、`160.9` / `12.7` / `1013.6` は 160 / 12 / 1013 として送られる（決定事項 4）。範囲外だけが既存の範囲メッセージで拒否される。
- `GET /api/atis` と PUT 200 の本文に版番号は無い。`UpdateAtisRequestDto` は未知キーを無視する（`@JsonIgnoreProperties(ignoreUnknown = true)`）。

### 課題（Problem Statement）

- 通常の速さで風や QNH を入力しても、次のポーリングで下書きが消える。ATIS の数値入力が実質的に使いにくい（①）。
- 成功した保存や、他クライアントが変えた滑走路が、遅延 GET や古い `runwayInUse` 付き PUT で巻き戻る（⑩a、⑩b）。
- 管制官が打った小数が、拒否されずに別の整数として保存される（決定事項 4）。

### なぜ今か（Motivation）

検証の推奨順は PR-A → PR-B である。PR-A（#141）はマージ済み。残っているユーザ可視の P1 が、この ATIS 編集の信頼性である。加算 API とパネルの状態機械に閉じ、運動モデルや他のポーリングは触らない。

---

## 方針

### 決定方針（Decision）

1. **下書きとサーバ状態を分ける（①）**
   - パネル状態は、サーバが報告した ATIS（`server`）と、風・QNH の入力下書き（`draft`）を分けて持つ。形は検証 PR-B に合わせ、例えば `{ server; serverSeq; draft: { kind: "pristine" } | { kind: "editing"; wind; qnh } }` を `useReducer`（または同等の明示的な状態）で持つ。
   - ポーリングは `server`（本文・識別・使用滑走路・保存済みの風/QNH）だけを更新する。`draft` が `pristine` のときだけ、入力欄を `server` から入れ直す。編集中の下書きは、値が同じポーリングでも、サーバの風/QNH が変わったポーリングでも消さない。サーバ本文（識別・滑走路・合成テキスト）は編集中でも更新してよい。
   - このクライアントの Apply / CALM / Clear が成功したときだけ、下書きを `pristine` に戻し、入力欄をその応答のサーバ状態に合わせる。保存に失敗したときは下書きを残す。
   - 空港切替では、切替先の下書きが無ければその空港の `server` を表示する。他空港の未保存下書きを混ぜない。
   - Operator（読取専用）に下書きは無い。サーバ表示だけを、下記の応答順序規則で更新する。

2. **クライアントの通し番号で、遅れた ATIS 応答を捨てる（⑩a）**
   - パネルが出す GET と PUT のそれぞれに、単調増加するリクエスト通し番号を付ける（パネル内のカウンタでよい。バックエンドの `revision` とは別物）。
   - 適用済みの通し番号より古い応答は、成功でも失敗でも状態に載せない。これにより、滑走路 PUT の成功表示のあとに遅れて着いた GET が 16R / ALPHA へ戻すことを防ぐ。
   - 通し番号は API 契約に必須のフィールドとしては足さない（サーバの `revision` と混同しない）。

3. **加算の `revision` / 任意の `expectedRevision`（⑩b）。既存クライアントは今日と同じ**
   - `GET /api/atis` の各空港オブジェクト、および PUT 200 の本文に、整数 `revision` を加算する。既存キー（`airportIcao`、`runwayInUse`、`informationIdent`、`text`、風・QNH など）の意味は変えない。
   - `revision` は空港ごとに単調増加する。セッション開始（その空港の初期状態）から数え、その空港の GET 表現が実際に変わる成功更新のたびに 1 増やす。風はセッション共有なので、風の更新で GET の風欄や本文が変わる空港は、対象パスの空港だけでなく、それらの `revision` も進める。
   - `PUT /api/atis/{icao}` に任意フィールド `expectedRevision` を足す。
     - **キーを省略**したボディは、今日と同一に処理する（必須の `runwayInUse` を適用し、風・QNH キーの省略規則も現行どおり）。古いクライアントと、`expectedRevision` を付けない既存テストは、滑走路付きの風 PUT が滑走路を変える振る舞いを維持する。
     - **キーがある**とき、値がその空港の現在の `revision` と一致すれば更新し、新しい `revision` を返す。一致しなければ **状態を変えず HTTP 409** を返す。409 の本文は、現在のその空港の ATIS（`revision` を含む）と、人間が読める `message` を含む。
   - 新しい Frontend は、滑走路・風・QNH・CALM・Clear のすべての PUT に、直前に適用した `server.revision` を `expectedRevision` として付ける。409 のときは「ATIS changed by another controller」を出し、409 本文の状態で `server` を更新する。風・QNH の未保存下書きは残し、新しい `revision` で再送できるようにする。使用滑走路の表示は 409 本文に合わせ、他クライアントの滑走路へ巻き戻さない（⑩b）。
   - 範囲外の風・QNH など、今日 400 になる入力は 400 のままとする。409 は `expectedRevision` の不一致に限る。

4. **小数の数値入力は拒否する（決定事項 4）**
   - 風向・風速・QNH は、単一の整数パーサで読む。文字列を trim したあと `/^\d+$/` に限り、その整数が既存範囲（風向 1–360、風速 0–99、QNH 850–1100）に入るときだけ受理する。`Number.parseInt` による切り捨ては使わない。
   - 小数（`160.9`、`12.7`、`1013.6`）、空、符号、指数表記は拒否する。メッセージは既存の範囲メッセージを使う（新しい文言を増やさない）。CALM（風速 0）は整数 0 のままでよい。
   - バックエンドの風・QNH は既に整数フィールドである。本スライスで小数を受理する API は足さない。拒否のユーザ経路はパネルのパーサである。

5. **退行を出さない**
   - `GET` / `PUT /api/atis` のパス、必須フィールド（`runwayInUse`）、風・QNH キーの省略規則、識別の進め方、本文のゴールデン、運動（位置・IAS・MSAW / STCA の垂直判定）、シナリオの ATIS シード（#140）、#141 の Hold / ハンドオフを壊さない。
   - 既存テストは、本機能用の期待値追加（`revision` の存在、Frontend が `expectedRevision` を付けること）を除き、契約を緩めて通してはならない。CI 4 ジョブがマージ SHA で合格する。

### 検討した他案（Alternatives Considered）

- **案 A: 入力中はポーリングを止める。** 下書きは残るが、他クライアントの滑走路変更も見えなくなり、⑩a / ⑩b は残る。不採用。
- **案 B: 風・QNH の PUT から `runwayInUse` を無くし、省略時は滑走路を変えない。** 新しいクライアントの ⑩b は緩和されるが、必須だった `runwayInUse` を任意に変えることになり、古いクライアントは今どおり滑走路を送る。本スライスの競合検出は `expectedRevision` に寄せ、必須フィールドは変えない。
- **案 C: サーバの `revision` だけで遅延 GET も捨てる。** 保存前の GET と PUT の順序問題（⑩a）はクライアントの通し番号の方が直接的である。`revision` はサーバ状態の楽観ロックに使う。両者を混用しない。
- **案 D: 小数を四捨五入または気象庁どおりの切り捨てで受理する。** 検証の推奨どおり、入力した値を黙って変えない。拒否する。

### トレードオフ（Trade-offs）

- **メリット**: 通常速度の入力が消えない。保存結果が遅延 GET で巻き戻らない。新しいクライアント同士では、古い滑走路付きの風 PUT が他者の滑走路を黙って上書きしない。小数の黙った変更が無くなる。古いクライアントと既存必須フィールドは維持できる。
- **デメリット / 受容する制約**: `expectedRevision` を付けないクライアントは、今日どおり古い `runwayInUse` で滑走路を変えられる。風は共有なので、風更新は複数空港の `revision` を進める。409 後は利用者が Apply をやり直すことがある。

### 非目標（Out of scope）

- PR-A の再作業（Hold の WCA / 進入、タワー移管の風予測）。#141 で入っている。
- PR-C（横断モードの状態機械、Resume 中の VA、拒否されたハンドオフと DEPART）。
- PR-D（位置取得エラーで機影が消えること、位置ポーリングの順序、平行滑走路の距離選択、残 1 フィックスの経路描画、フライトプランパネルの遅延上書き）。
- `runwayInUse` を任意フィールドにすること、ATIS PUT のその他の破壊的変更。
- 視程・雲、QNH による高度換算、inHg、高度別風。
- ATIS 以外の数値入力（IAS、高度指示など）の小数規則。
- `expectedRevision` 無しの古いクライアントまで、滑走路の黙った上書きを禁止すること。

---

## 完了条件（Success Criteria）

型と静的検査をテストより先に満たす。そのあと、検証 PR-B が指定した反転再現テストと組み合わせテストを永久テストとして残す。

1. **型・静的検査（先に）**
   - Frontend: `AtisState` に `revision`、更新ボディに任意の `expectedRevision`。`ATIS_STATE_FIELD_KEYS` を整合させる。下書き状態は `pristine` | `editing` として型で表す。整数パーサは風と QNH で共有し、戻り値は成功または既存の範囲メッセージである。
   - Backend: PUT DTO に任意の `expectedRevision`。GET / PUT 200 の本文型（またはマップ契約）に `revision` がある。
   - Frontend の型検査（または既定の静的検査）と Frontend ビルド、Backend のコンパイルが通る。
2. **反転再現テスト（必須）**
   - `/workspace/horus-ops/reviews/repro/frontend/atisPanel.repro.test.tsx` の 4 件を、バグを断言する形から期待動作へ反転し、リポジトリの Frontend テストとして残す（コピーして断言を逆にする）。
     - **claim1**: 値が同じ新しいオブジェクトのポーリングが来ても、未保存の 160 / 12 / 1009 が入力欄に残る。
     - **claim10a**: 滑走路 PUT が 34L / BRAVO を示したあと、遅れて着いた 16R / ALPHA の GET を無視し、表示は 34L / BRAVO のまま。
     - **claim10b**: 風 PUT は `expectedRevision` を付ける。サーバが既に新しい `revision`（他クライアントが滑走路を変えたあと）なら 409 を扱い、表示上の使用滑走路を古い 16R へ戻さない。
     - **decision4**: `160.9` / `12.7` の Apply wind、および `1013.6` の Apply QNH は `updateAtis` を呼ばず、既存の範囲メッセージを出す。
   - `/workspace/horus-ops/reviews/repro/backend/AtisStaleRunwayReproTest.java` を反転して Backend テストとして残す。クライアント B が RJTT を 34L にしたあと、クライアント A が **B より前の `expectedRevision`** と古い滑走路付きで風を PUT する → **409**、滑走路は 34L のまま、識別は B の更新後から進まない。
   - 対になる退行テスト: `expectedRevision` を付けない同じ風 PUT は、今日どおり滑走路を変えられる（古いクライアント契約）。
3. **組み合わせ**
   - 下書きを入力する → サーバ風が変わったポーリングが来る（下書きは残り、サーバ本文は新しい値）→ Apply が成功する → その後のポーリングでも適用値が残る。
4. **ブラウザ（Controller）**
   - 風を 3 秒以上かけてゆっくり入力しても、ポーリングで消えない。
5. **既存契約**
   - `expectedRevision` 無しの GET / PUT は、加算の `revision` キー以外、今日のキーと意味を保つ。既存の ATIS 本文ゴールデン、シナリオの ATIS シード、#141 の退行を緩めない。
6. **文書**
   - ルート README、Backend README、Frontend README のうち、ATIS の GET / PUT またはパネル操作を説明している箇所を、本スライスの契約に合わせて更新する（下記 README）。
7. **CI**
   - 4 ジョブがマージ SHA で合格する。

---

## 影響範囲

- **Frontend**: `components/atisPanel.tsx`、`utility/api/atis.ts`、`components/atisPanel.test.tsx`、反転した `atisPanel.repro`、Frontend README。
- **Backend**: `UpdateAtisRequestDto`、`AtisController`（409）、`AtisService`（`revision`）、`AtisApiIntegrationTest` / `AtisServiceTest`、反転した `AtisStaleRunwayReproTest`、Backend README。
- **BFF**: `app/api/atis` は本文を透過すれば追加ロジックは不要。409 を吞み潰さない。
- **触らないもの**: 位置 API、Hold、`DEPART`、タワー移管、SID/STAR、運動、シナリオシードの形（内部更新は `expectedRevision` 無しで現行どおりでよい）。

---

## 実装計画

### Phase 1（型と加算契約）

- `revision` と任意の `expectedRevision` を型と DTO に足す。GET に `revision` を出す。静的検査を通す。

### Phase 2（Backend）

- 空港ごとの `revision` と 409 分岐を入れる。風更新時の他空港 `revision` を進める。`expectedRevision` 無しの経路は現行の更新関数に載せる。反転 `AtisStaleRunwayReproTest` と、無し契約の退行テストを追加する。

### Phase 3（Frontend）

- パネルを server / draft / 通し番号に切り替える。PUT に `expectedRevision` を付ける。409 を処理する。共有整数パーサに切り替える。

### Phase 4（検証と文書）

- 4 件の `atisPanel.repro` を反転して恒久化する。組み合わせテストを足す。既存 `atisPanel.test.tsx` の PUT 期待値に `expectedRevision` を足す。README を更新する。Controller で 3 秒入力を確認する。

---

## 検証

- [x] Frontend の型検査（または既定の静的検査）と Frontend ビルドが通る。Backend がコンパイルできる。
- [x] 反転した `atisPanel.repro` 4 件と、反転した `AtisStaleRunwayReproTest`、`expectedRevision` 無しの退行テストが通る。
- [x] 下書き → サーバ風が変わったポーリング → Apply → 再ポーリングの組み合わせテストが通る。
- [x] 既存の ATIS / シナリオ / #141 関連テストを、本機能用の期待値追加以外で緩めずに通す。
- [x] Controller で風を 3 秒以上かけて入力しても、ポーリングで消えない。
- [x] CI 4 ジョブがマージ SHA で合格する。

再現テストの置き場所（検証 README どおり、恒久化するときはバグ断言を逆にする）:

- Frontend: `/workspace/horus-ops/reviews/repro/frontend/atisPanel.repro.test.tsx`（claim1、claim10a、claim10b、decision4）
- Backend: `/workspace/horus-ops/reviews/repro/backend/AtisStaleRunwayReproTest.java`

## README

- **Frontend README** の ATIS パネル説明: 未保存の風・QNH はポーリングで消えないこと、保存成功後にサーバ値へ戻ること、遅延した GET は無視すること、更新は `expectedRevision` を付け 409 なら他コントローラによる変更として表示を同期すること、小数は範囲メッセージで拒否すること。
- **Backend README** と、ルート README が ATIS API に触れる箇所: `revision` の加算、任意の `expectedRevision`、不一致は 409（状態は不変、本文に現在状態）、キー省略時は現行どおり、であることを短く書く。本文ゴールデンや高度モデルは変えない、と矛盾させない。

## 未解決事項（Unresolved Questions）

- `revision` の初期値を 0 にするか 1 にするかは実装で固定し、テストで断言すればよい（単調増加と不一致検出ができればどちらでもよい）。
- 409 本文を「200 と同じ空港オブジェクト + `message`」にするか、`state` で包むかは、Frontend が現在状態と `revision` を読める形に固定する。テストで本文形を固定すること。
