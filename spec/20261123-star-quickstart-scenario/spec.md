# クイックスタートの既定シナリオを RJTT RNAV STAR にする

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-23
- **関連**: [spec/spec.md](../spec.md) フェーズ 1、[spec/20261121-one-command-quickstart/spec.md](../20261121-one-command-quickstart/spec.md)、[spec/20261122-local-quickstart/spec.md](../20261122-local-quickstart/spec.md)、[spec/20261122-sid-star/spec.md](../20261122-sid-star/spec.md)（#139 / `25f7e20`）、[spec/20261122-atis-runway-in-use/spec.md](../20261122-atis-runway-in-use/spec.md)、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、Issue [#114](https://github.com/HorusATC/Horus/issues/114)
- **リポジトリ配置**: `spec/20261123-star-quickstart-scenario/spec.md`

## 概要

トップの「START WITH SAMPLE (サンプルで始める)」が、既存カタログの RJTT RNAV STAR（RWY 34L/34R）を既に割り当てた到着機 5 機を起動するようにする。レーダーに経路折れ線が出て、ATIS の使用滑走路は 34L（または仕様で固定した 34R）で始まる。研究者は追加の割当操作なしに、#139 で入った手続きを最初のシナリオとして見られる。

新しい SID／STAR 手続きの追加や、SWIM からの追加チャートは本スライスに含めない。

## 背景・課題

### 現状（main `25f7e20`、#139 マージ済み。ATIS QNH は #138 / `e1fe2fc`）

- 優先度 1 の開始フロー（`dev-up` → トップの START WITH SAMPLE → Controller）は実装済み。`scripts/verify-quickstart.sh` はホームページ、`POST /api/scenario/load`、5 機、2 秒後の位置変化を見る。
- 既定サンプル `Frontend/data/quickstart-arrival-sample.json` は ANA101 / JAL202 / SKY303 / ANA404 / JAL505 の 5 機で、到着空港は RJTT だが **`route` は空**。初期位置はおおまかな緯度経度で、公示 STAR の IF には乗っていない。
- #139 で `GET /api/procedures` と `POST /api/aircraft/{callsign}/assign-procedure`、レーダーの飛行計画折れ線が入った。カタログは RNAV STAR 15 本と VAMOS FOUR（トランジション含む）。VISIP は割当不可。
- シナリオ読込 `POST /api/scenario/load`（`ScenarioLoadDto`）は機体配列だけである。`procedureId` も ATIS シードも無い。セッション開始時の ATIS 使用滑走路は **16R**（`AtisService.DEFAULT_RUNWAY`）。STAR はすべてチャート見出しが RWY 34R/34L なので、既定のままでは使用滑走路と経路が揃わない。
- ギャップ台帳は、SID／STAR を「公式 AIP 待ち、#114」と書いたままである。第一スライスは #139 で入っている。#114 は残りの手続き（他の RJTT SID、RJAA など）のためにオープンのまま。
- オープン issue（15）: #114, #91, #83, #75, #72, #73, #70, #66, #67, #65, #59, #58, #57, #53, #43。#71 / #115 / #69 はクローズ済み。

### 課題（Problem Statement）

研究者が最短手順で最初のシナリオを開いても、新しい STAR は見えず、経路の無い降下と 16R の ATIS になる。#139 の能力を使うには、Operator で手続きを割り当て、Controller で使用滑走路を 34L/R に変える追加操作が要る。

### なぜ今か（Motivation）

優先度 1 は「セットアップから最初のシナリオまでの手数」である。手続きカタログは揃ったが、既定サンプルがそれを使っていない。残りの RJTT RNAV SID や RJAA の第二スライスは、手元に無い SWIM の追加 PDF が要る（下記）。本スライスは既存カタログと既存の割当・描画だけで 1 PR に収まる。

---

## 方針

### 決定方針（Decision）

1. **既定の START WITH SAMPLE を STAR 到着シナリオにする**
   - 機数は **5 機のまま**（`verify-quickstart.sh` の台数契約を維持する）。コールサインは現行どおり ANA101 / JAL202 / SKY303 / ANA404 / JAL505 でよい。
   - 5 機はいずれも RJTT 向け到着で、カタログにある RNAV STAR を 1 機 1 本割り当てる。**最終ゴールデン（STCA 無し・10 シミュレーション分）**:
     - ANA101: `OSHIMA1A`（IF XAC → ARLON）
     - JAL202: `AKSEL1K`（IF AKSEL → KAIHO、公示 AT 12000）
     - SKY303: `AROSA2C`（IF AROSA → CREAM）
     - ANA404: `AKSEL2C`（IF AKSEL → CREAM。`AKSEL1K` と同一 IF だが最初の TF が CLONE で分岐。高度は `AKSEL1K` より 1000 ft 以上）
     - JAL505: `GODIN2A`（IF GODIN → ARLON。北からの単独フィーダー）
   - **POLIX 系は既定サンプルに入れない**（GODIN と CHIPS–COLOR… を共有し、起動直後から STCA になるため。`POLIX2A` の代わりに `AKSEL2C` を使う）。
   - 既定サンプルはロード後 **少なくとも 10 シミュレーション分**、`GET /api/conflict/violations` が空で、MSAW もクリーンであること（Backend 統合テストで固定）。終端付近の経路合流は、その間に STCA 予測が出なければ許容する。
   - 初期位置は、その手続きの **IF のカタログ座標**（`waypoints.json` / 手続きカタログ。新規の座標を作らない）。初期針路は **次の TF の公示真方位**（測地／真の枠。偏差は適用しない）。初期高度は、IF に公示高度があるときはそれに合わせる（AKSEL 1K は AT 12000）。IF に高度が無い手続き（OSHIMA / AROSA / AKSEL2C / GODIN）は、最初の下流制約を下回らない巡航高度をシナリオに書く（例: OSHIMA1A は ANZAC の AT 13000 以上、AROSA は AVEEY の AT 11000 以上、AKSEL2C は同一 IF の AKSEL1K より 1000 ft 以上、GODIN は CHIPS の AT OR BELOW 13000 に向けて降りる前の巡航。数字はテストで固定し、AIP の未記載高度を「公示」と書かない）。
   - 対地速度は既存サンプルと同程度（260–300 kt 程度）でよい。垂直速度は、次の高度制約に向けて緩やかな降下でも、一時的に 0 でもよい。
   - 既定ボタンは VAMOS 出発機を入れない（`DEPART` との結合は #139 の非目標のまま）。VISIP も入れない。

2. **経路はカタログから載せる（手書きのウェイポイント列を正にしない）**
   - `SpawnWithFlightPlanDto`（シナリオの各機）に、任意フィールド `procedureId` と、必要なときだけ `transitionId` を加算する。指定があるときは、既存の `assign-procedure` と同じマッパーで飛行計画ルート（と SID なら VA 制約）を載せる。パス表の KIAS は今どおり対地速度の `speed` に入れない。
   - `procedureId` を省略した既存 JSON は、今日と同じく `flightPlan.route` を使う。必須フィールドは変えない。未知の ID は 400 または 404（既存の割当 API に合わせ、テストで固定する）。
   - 既定サンプルは `procedureId` を書き、`route` は空でもマッパー任せでよい。ゴールデンテストは、ロード後の飛行計画がカタログどおりであること（例: ANA101 が `XAC, ANZAC, TT450, TT451, TT452, TT453, WANDA, WEDGE, ARLON`、ANZAC / WANDA が AT 13000、WEDGE が AT 8000）。

3. **シナリオ読込で ATIS の使用滑走路を揃えられるようにする**
   - `ScenarioLoadDto` に任意の ATIS シードを加算する（例: `atis`: `{ "icao": "RJTT", "runwayInUse": "34L" }` の配列、または同等の形）。キーを省略した既存ボディは、今日どおり RJTT / RJAA とも 16R で始まる。
   - 既定の STAR サンプルは **RJTT を 34L** にする（34R でもよいが、仕様とゴールデンを一方に固定する。STAR 見出しは 34L/R 両方）。RJAA は触らない。QNH や風は本スライスで必須にしない（省略時は現行の欠測／無風）。
   - `AtisService.DEFAULT_RUNWAY`（16R）自体は変えない。シナリオを読まないセッションの既定を壊さない。
   - 識別文字の進め方は、既存の使用滑走路更新規則に従う（値が変わるなら識別を進めてよい）。

4. **古い空ルートのサンプルは残し、既定からは外す**
   - 現行の空 `route` サンプルは、別名でリポジトリに残す（例: `Frontend/public/samples/quickstart-arrival-open-vector.json`、または `Frontend/data/` 配下の非既定ファイル）。トップの既定ボタンは新しい STAR サンプルだけを呼ぶ。JSON アップロードでは旧ファイルも今日どおり読める。
   - `scripts/verify-quickstart.sh` が読む `Frontend/data/quickstart-arrival-sample.json` は新しい既定を指す。機数 5・ロード 200・位置が動く、README 先頭の `dev-up` と `START WITH SAMPLE` 契約は維持する。位置フィンガープリントはファイル内容に依存するので、新しい座標でも「2 秒で動く」ことだけを見ればよい。

5. **退行を出さない（No-regression）**
   - 既存の `POST /api/scenario/load` の必須フィールド、`GET` / `PUT /api/atis`、`assign-procedure`、`DEPART`、位置 API、手続きカタログの内容を破壊的に変えない。
   - `procedureId` も `atis` シードも無い既存シナリオ（`haneda-template.json`、`tokyo-approach-scenario.json`、アップロード済みの旧サンプル）のロード結果は、ATIS 既定とルートの扱いを今日と同一にする。
   - 既存テストは、本機能用の期待値追加を除き、契約を緩めて通してはならない。CI 4 ジョブがマージ SHA で合格する。

6. **同じ PR の文書修正**
   - ルート / Frontend README のクイックスタート説明: 既定サンプルが RJTT RNAV STAR（34L/R）の到着 5 機であり、使用滑走路がそれに揃うこと。割当パネルと未実装（VISIP、公示ホールド自動、KIAS 自動適用、VAMOS を既定サンプルに含めないこと）を、実装済みのように書かない。出典一行は既存のとおり README とデータファイルに留め、UI バナーは出さない。
   - `spec/20261017-terminal-radar-gaps/spec.md` の「SID／STAR としての公開手続き（公式 AIP 待ち、#114）」を、第一スライスは #139 で実装済み、#114 は残手続きのためにオープン、本シナリオで既定サンプルがそれを使う、と更新する。

### 検討した他案（Alternatives Considered）

- **SID／STAR 第二スライス（残る RJTT RNAV SID や RJAA）を今やる**: 優先度 2 としては自然だが、手元の PDF は RJTT の OSHIMA / AKSEL / AROSA / POLIX / GODIN / VAMOS / VISIP だけである。残りは SWIM の追加ダウンロードが要る（本仕様末尾）。今は既定シナリオを先に直し、チャートが揃ってから第二スライスに進む。
- **ルートだけを JSON に手書きし、API は変えない**: カタログと二重管理になり、ATIS 16R も残る。アップロードしたコピーでも滑走路が揃わない。任意の `procedureId` と ATIS シードの方が小さい。
- **既定ボタンを増やして旧サンプルと併記する**: 手数と説明が増える。優先度 1 は既定の 1 クリックを STAR にする。旧 JSON は残す。
- **既定 ATIS 定数を 34L に変える**: シナリオを使わない経路や既存テストの前提（16R）を壊す。
- **方位目盛（コンパスローズ）**: ギャップ台帳の残件だが、#139 直後に最初のシナリオが手続きを見せない問題は解かない。本スライスの次点。

## 非目標（Non-goals）

- 新しい SID／STAR 手続きの抽出・追加、VISIP の飛行、RJAA のカタログ。
- 既定サンプルへの VAMOS / `DEPART` 出発機、公示ホールディングの自動飛行、KIAS の自動適用、到達勾配の強制。
- 既定 ATIS（16R）のグローバル変更、QNH・風の必須化、高度計換算。
- IAP や `JOIN FINAL` の自動化。
- 出典を示す UI バナー。
- #114 を本 PR だけでクローズすること（残手続きがある）。

## 完成条件（Success Criteria）

1. **Backend**
   - `procedureId` 付きのシナリオ（またはスポーン）をロードすると、飛行計画ルートが `assign-procedure` と同じマッピングになる。ゴールデン: 上記 5 機のうち少なくとも OSHIMA 1A のフィックス順と高度制約。KIAS は `speed` に入らない。
   - 未知の `procedureId` は 400 または 404。
   - STAR 既定サンプルをロードすると、`GET /api/atis` の RJTT 使用滑走路が仕様で固定した 34L（または 34R）である。シードの無い既存シナリオでは 16R のまま。
   - 既定サンプルは 10 シミュレーション分間、conflict violations 空・MSAW クリーン（統合テスト）。
   - 既存のシナリオ読込・ATIS・割当テストは契約を緩めずに通る。
2. **Frontend**
   - 型検査（または既定の静的検査）と Frontend ビルドが通る。
   - トップの START WITH SAMPLE は新しい STAR サンプルを使う。追加の割当クリックなしで、Controller レーダーに 5 機の経路折れ線が見える（スクリーンショットまたはブラウザ確認）。警告バナーは無い。
   - 既定レーダー中心は RJTT 付近とし、既定 400 km レンジで 5 機の IF が視野に入る。
3. **クイックスタート**
   - `dev-up` → START WITH SAMPLE の手順は変わらない。
   - `scripts/verify-quickstart.sh --readme-only` および（サーバ起動時の）`--live` が合格する（5 機、位置が動く、README 先頭契約）。
4. **文書**
   - README が既定サンプルの最終 STAR セット（OSHIMA1A / AKSEL1K / AROSA2C / AKSEL2C / GODIN2A）と使用滑走路を説明する。ギャップ台帳の SID／STAR 行を更新する。未実装を実装済みと書かない。
5. **CI**
   - 4 ジョブがマージ SHA で合格する。

## 実装の手がかり

- カタログとマッパー: `aip/rjtt-sid-star-20261101.json`、`ProcedureFlightPlanMapper`、`POST /api/aircraft/{callsign}/assign-procedure`。IF 座標は `waypoints.json` の既存値を使う。
- シナリオ: `ScenarioLoadDto`、`SpawnWithFlightPlanDto`、`Frontend/types/scenario.ts`、`Frontend/data/quickstart-arrival-sample.json`、`Frontend/app/page.tsx`、`scripts/verify-quickstart.sh`。
- ATIS シードは既存の `PUT /api/atis/{icao}` と同じ検証（知っている滑走路）に載せる。
- テストはカタログとのゴールデン、ATIS シードの有無、既存 JSON の退行に絞る。画面の網羅より型検査とビルド、verify-quickstart を優先してよい。

## README

- クイックスタート冒頭: 既定サンプルは RJTT RNAV STAR（RWY 34L/R）の到着 5 機で、使用滑走路がそれに揃うこと。
- Operator の手動割当（VAMOS とトランジションを含む）の説明は残す。既定サンプルに VAMOS が無いことを混同しない。
- 出典一行は既存のまま（README とデータファイルのみ）。

## 第二スライス用に足りない公式 PDF（本スライスでは取得しない）

第二スライス（残る RJTT RNAV SID および／または RJAA）は、SWIM AIP ビューア（AIRAC 1 Nov 2026）の追加ダウンロードが必要で、今は選ばない。ベース:

`https://web.swim.mlit.go.jp/f2eapr/AIP/html/20261029/eAIP/20261101/pdf/`

**既に手元にある（再取得不要）**

- `JP-AD-2.24.12-RJTT-en-JP.pdf`（VAMOS FOUR）
- `JP-AD-2.24.26-RJTT-en-JP.pdf`（OSHIMA）
- `JP-AD-2.24.27-RJTT-en-JP.pdf`（AKSEL）
- `JP-AD-2.24.32-RJTT-en-JP.pdf`（POLIX）
- `JP-AD-2.24.53-RJTT-en-JP.pdf`（AROSA）
- `JP-AD-2.24.71-RJTT-en-JP.pdf`（GODIN）
- `JP-AD-2.24.88-RJTT-en-JP.pdf`（VISIP ONE）

**第二スライスの前に必要なファイル（ファイル名を推測で埋めない）**

手元に RJTT / RJAA の AD 2.24 一覧が無いので、残プレートの番号はここで確定できない。次を公式ビューアから取得できれば、一覧を読んで残りの個別チャートを特定できる。

- `JP-AD-2-RJTT-en-JP.pdf`（羽田 AD 2 一式。2.24 の目次と残 SID／STAR 名のため）
- `JP-AD-2-RJAA-en-JP.pdf`（成田 AD 2 一式。同様）

それに加えて、上記 7 枚に含まれないすべての

- `JP-AD-2.24.<n>-RJTT-en-JP.pdf`
- `JP-AD-2.24.<n>-RJAA-en-JP.pdf`

（`<n>` は目次のプレート番号。ソースメモでは RJTT は SID 12・STAR チャート 26、RJAA は SID 10・STAR 10 とあるが、個別ファイル名の根拠には使わない。）
