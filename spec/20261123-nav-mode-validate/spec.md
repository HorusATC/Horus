# 単一の航法モード型と、拒否された指示による部分更新の禁止

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-23
- **関連**: 外部レビューおよび検証（PR #117–#139）の PR-C（⑤、⑥、P-3）、[spec/20261122-sid-star/spec.md](../20261122-sid-star/spec.md)、[spec/20261122-initial-climb/spec.md](../20261122-initial-climb/spec.md)、[spec/20261122-tower-handoff/spec.md](../20261122-tower-handoff/spec.md)、[spec/20261017-runway-final-approach/spec.md](../20261017-runway-final-approach/spec.md)、[spec/20261017-go-around/spec.md](../20261017-go-around/spec.md)、[spec/20261123-hold-handoff-wind/spec.md](../20261123-hold-handoff-wind/spec.md)（PR-A / #141）、[spec/20261123-atis-revision/spec.md](../20261123-atis-revision/spec.md)（PR-B / #142）
- **リポジトリ配置**: `spec/20261123-nav-mode-validate/spec.md`
- **前提 main**: `4ce741f`（#142 マージ後）

## 概要

航空機の横方向の航法状態を、ばらばらの列挙と真偽フラグではなく、一つの判別可能なモード型（sealed / discriminated union）として持つ。矛盾した組み合わせ（例: `FLIGHT_PLAN` なのに VA が残る、経路再割当のあとに JOIN FINAL の進入コースが残る）を型として表せなくする。指示は、現在モードに対して検証してから、新しいモードを一度に代入する。拒否された指示は状態を一切変えない。VA 中の Resume は VA を維持する（SID の VA レグは自機航法である）。経路の再割当（`setFlightPlan` / `assignProcedureRoute`）では JOIN FINAL の最終進入状態を消す。

位置 API の既存キー（`navigationMode`、`goAround`、`initialClimb`、`towerHandoff` など）は、新しいモード型からの射影として維持する。破壊的な API 変更はしない。Hold の WCA・タワー移管予測（PR-A）、ATIS（PR-B）、位置ポーリングや経路描画（PR-D）は本スライスの対象外である。

## 背景・課題

### 現状

main `4ce741f`。PR-A（#141）で Hold / ハンドオフの風一貫は入っている。PR-B（#142）で ATIS の下書きと `revision` は入っている。航法状態はまだ `AircraftBase` の列挙 `NavigationMode`（`FLIGHT_PLAN` / `HEADING` / `DIRECT_TO` / `HOLDING`）に、独立したフラグと付随フィールドが載っている。

- `procedureVaActive`、`procedureVaCourseTrueDeg`、`procedureVaAltitudeFt`
- `initialClimbActive` と出発端・滑走路方位・延期針路
- `goAroundActive`
- `finalApproachCoursePending`、`finalApproachCourseHeading`
- `towerHandoffActive` と末端位置

これだけでも、検証が再現した矛盾が許される。

- **⑤（PARTIAL、状態そのものは確認済み）**: `setResumeNavigation()` は VA を消さないまま `navigationMode` を `FLIGHT_PLAN` にする。指示針路は VA の真方位のまま、ウェイポイント通過判定は飛行計画として動く。実 VAMOS（TT502 が上昇経路の東に約 7 NM）では指数は 0 のまま、VA 解除は 700 ft 超だった。最初のフィックスを VA 針路上 0.5 NM に置いた経路では、Resume 後に **700 ft 未満**で通過した（対照は解除後）。
- **⑥（CONFIRMED）**: `beginTowerHandoff` は距離・モード・予測の検証の前に `clearInitialClimbActive()` を呼ぶ。約 40 NM で拒否されても `initialClimbActive` は偽になり、滑走路方位と DER+400 ft の延期針路が消える。MSAW の出発廊抑制も失われる。
- **P-3（CONFIRMED）**: `setFlightPlan` / `assignProcedureRoute` は `clearFinalApproachInterceptState()` を呼ばない。JOIN FINAL で進入コースが付いたあと経路だけ載せ替えると、コースが残る。経路終了後の `HEADING` で、風下では進入用 WCA が素の指示針路にかかり、機首と指示がずれる。

`requireApproachControl()` はハンドオフ成立後の指示を拒むが、拒否ハンドオフの途中更新は防がない。VA 中の Resume を 409 にする案は採らない（後述）。

### 課題（Problem Statement）

- 相互に排他であるはずの航法状態が、独立フラグのため同時に成り立つ。⑤ はその一例である。
- 拒否される指示が、検証の前に状態を一部消す（⑥）。
- 経路を載せ替えても JOIN FINAL の進入コースが残り、のちの針路飛行を歪める（P-3）。

### なぜ今か（Motivation）

検証の推奨順は PR-A → PR-B → PR-C である。A と B はマージ済み。残るのは、矛盾状態を型で禁止し、拒否指示の部分更新と経路再割当後の進入コース残留を直すことである。運動の Guidance / 予測（#141）をやり直さず、モードの表現と指示のコミット規則に閉じられる。

---

## 方針

### 決定方針（Decision）

1. **横方向の航法は一つの判別可能なモード型にする（⑤ を型で不可能にする）**
   - `NavigationMode` 列挙と、上記の独立フラグを、一つの sealed なモード型に置き換える。検証 PR-C の例に合わせ、各バリアントは自分のデータだけを持つ。例:
     - `Heading(hdg, Optional<ApproachCourse>)`
     - `FlightPlan(index)`
     - `ProcedureVa(course, altFt, thenIndex)`
     - `DirectTo(...)`
     - `Holding(HoldState)`
     - `InitialClimb(...)`
     - `TowerHandoff(threshold)`
     - 復行は、`FlightPlan` や `ProcedureVa` と同時に立つ真偽フラグにはしない。復行専用のバリアントにするか、復行中だけが持つデータの中に閉じる。
   - 次の状態はコンストラクタ／ファクトリで作れないこと。静的に（型と到達不能コード、または生成物に対する検査で）示す。
     - `FlightPlan` かつ VA 活性
     - `FlightPlan`（または素の `Heading`）なのに JOIN FINAL の進入コースが残る
     - `InitialClimb` と `TowerHandoff` の同時成立
     - ハンドオフ成立後なのに出発フラグだけ残る、など、フラグの交差で起きていた組み合わせ
   - 位置 API と既存の飛行計画応答は、この型からの**射影**とする。既存キーは残す。
     - VA 中（Resume のあとも、高度到達までは）の `navigationMode` は、今日の割当直後と同じく `HEADING` として出してよい。`procedureVaActive`（飛行計画系の応答にある場合）は真のまま。
     - VA 解除後は `FLIGHT_PLAN`。
     - `goAround` / `initialClimb` / `towerHandoff` は、いまのレーダーラベル（`GA` / `DEP` / `TWR`）と同じ意味の派生真偽値とする。
     - 新しいモード文字列を必須にして既存クライアントを壊さない。足すなら加算に限り、既存 4 値とフラグの意味を有効な指示の経路では変えない。

2. **指示は検証してから一度に代入する。拒否なら無変更（⑥）**
   - 横方向の状態を変える指示は、`validate(current) → 拒否 | 次モード` とし、受理したときだけモード（と、その指示が正当に変える指示針路など）を一度に書き込む。拒否は例外または 4xx の現行契約を維持し、拒否の前後で機体のスナップショットが等しいこと。
   - 対象の指示（少なくとも）: 針路／高度／速度の指示ベクトル、Direct To、Hold、Resume、`JOIN FINAL`、復行、`DEPART`（`beginInitialClimb`）、タワー移管、`setFlightPlan`、`assignProcedureRoute`。
   - ⑥ の直接の修正: タワー移管は、距離・現行モード・予測軌道の検証がすべて通ってから `InitialClimb` を終える。拒否（遠すぎる、`HEADING` でない、予測が末端に届かない、復行中など）では出発状態（延期針路、滑走路方位、MSAW 抑制の根拠を含む）を残す。
   - 予測そのものの式は #141 のまま使う。本スライスで WCA や `KinematicStep` を再設計しない。

3. **VA 中の Resume は VA を維持する（⑤、採択した決定）**
   - SID の VA レグは自機航法である。VA 中の Resume は 409 にせず、受理する。モードは `ProcedureVa` のまま（射影は `HEADING` + VA 活性）。指示真方位と解除高度は変えない。次フィックス（VAMOS なら TT502）は飛行計画上の次地点として残し、高度条件を満たすまで通過判定で進めない。
   - 解除条件は現行どおり、公示の AT_OR_ABOVE に達したとき（700 ft 未満で早めに解除しない）。解除後に `FlightPlan` へ移る。
   - Resume が出発中や JOIN FINAL 中など、今日すでに VA 以外の状態を終えている経路の意味は、有効指示の退行トレーサ（後述）で固定する。本スライスで DEPART 中 Resume の意味を広げない。

4. **経路再割当では JOIN FINAL 状態を消す（P-3）**
   - `setFlightPlan` と `assignProcedureRoute` は、最終進入の待ち／進入コースを消してから新しい経路（および SID なら VA）を載せる。`assignProcedureRoute` が `setFlightPlan` を呼ぶなら、消去はそこに一度あればよい。
   - 経路が尽きて素の `Heading` になったとき、残った進入コースによる WCA を適用しない。

5. **有効な指示の運動は main とビット同一のトレースにする**
   - 本スライスが直す不具合経路（VA 中 Resume、拒否されたタワー移管、JOIN FINAL のあと経路再割当）を除き、有効な指示の運動と、位置 API に出るモード射影を変えない。
   - `4ce741f`（または本 PR のマージベース）で、固定 dt・乱数なしの**指示スクリプト**を走らせてトレースを録る。スクリプトは、針路、飛行計画、Direct To と Resume、Hold、延期針路付き DEPART（拒否ハンドオフなし）、VAMOS 割当を Resume せず自然解除するまで、JOIN FINAL を再割当なしで進入させるまで、受理されるタワー移管など、すでに成功する系列を含む。
   - PR 後の同じスクリプトは、録ったトレースと**ビット同一**であること（同一レコーダの同一フィールド。位置・針路・トラック・GS・高度、指示ベクトル、`navigationMode` と派生フラグ、ウェイポイント指数、VA 活性、出発の延期針路）。丸めを使うなら、ゴールデンを録ったときと同じ桁に固定し、そのバイト列または完全一致の比較をテストにする。不具合修正で意図的に変わる系列は、このゴールデンに入れず、反転再現テストで見る。

6. **退行を出さない（その他）**
   - 位置 API の既存キー、Hold / タワー移管の受理条件と予測（#141）、ATIS（#142）、SID/STAR カタログ、`DEPART` / `JOIN FINAL` / 復行の既存 4xx メッセージの意味を破壊的に変えない。
   - 既存テストは、本機能用の期待値追加（⑤⑥ P-3 の反転）を除き、契約を緩めて通してはならない。CI 4 ジョブがマージ SHA で合格する。

### 検討した他案（Alternatives Considered）

- **案 A: VA 中の Resume を 409 にする。** 検証が挙げた代替。SID の VA は自機航法なので、Resume で VA を切る／拒否するより、VA を維持して次フィックスを残す方を採る。
- **案 B: フラグを消す順序だけ直す（型は変えない）。** ⑥ と P-3 は直るが、⑤ のような矛盾状態をまた表現できてしまう。不採用。
- **案 C: 位置 API の `navigationMode` に `PROCEDURE_VA` を必須化する。** 既存のフロント（`HEADING` + フラグ、`DEP` / 飛行計画）を壊しうる。内部型は分け、外部は現行キーへの射影に留める。
- **案 D: 本スライスで Hold 誘導やハンドオフ予測を作り直す。** #141 の範囲であり、対象外とする。

### トレードオフ（Trade-offs）

- **メリット**: 矛盾状態を組み立て不能にできる。拒否指示が出発や進入状態を消さない。経路変更後に古い進入コースが残らない。VA 中 Resume の意味が仕様として固定される。
- **デメリット / 受容する制約**: `AircraftBase` の大きな内部整理になる。VA 中 Resume と、JOIN FINAL 後の経路再割当と、拒否ハンドオフのあとの運動は、意図して今日と変わる。位置 API の文字列は現行のままなので、フロントはモード型そのものを見ない。有効指示のビット同一トレースをレコーダで固定する必要がある。

### 非目標（Out of scope）

- **PR-D**: 位置取得失敗で機影が消えること、位置ポーリングの順序、平行滑走路の距離表示、MSAW の廊選択、残 1 フィックスの経路折れ線、フライトプランパネルの遅延上書き。
- **Hold / タワー移管の誘導と予測の再設計**（WCA、`KinematicStep`、受理 ⇔ 到達の同値。#141 済み）。本スライスは拒否時に出発状態を消さないことに限る。
- **ATIS** の下書き・`revision`・小数入力（#142 済み）。
- DEPART の指示対地速度の方針変更（検証の決定事項、README / 実装の 180 kt）、戦闘機・ヘリの風（⑧、製品経路から到達不能）。
- 位置 API の破壊的変更、フロントのモード型への必須移行。
- 公示ホールディング、EAT / EFC、IAP の新規実装。

---

## 完了条件（Success Criteria）

型と静的検査をテストより先に満たす（違法状態を表現不能にする）。そのあと、検証が指定した反転再現と、拒否の不変、組み合わせ、有効指示のビット同一トレースを残す。

1. **型・静的検査（先に）**
   - 横方向状態は一つの判別可能なモード型である。独立した「VA 活性」「出発活性」「進入コース」を、モードと矛盾した組み合わせでセットする公開 API が無い。
   - 決定方針 1 に挙げた違法状態を組み立てるコードは、コンパイルできないか、到達不能として検査される。
   - Backend のコンパイルと、既存フロントの型検査（位置 DTO のキーを壊さないこと）および Frontend ビルドが通る。
2. **反転再現テスト（必須）**
   - `/workspace/horus-ops/reviews/repro/backend/ReviewReproTest.java` のうち、本スライス対象を反転し、Backend の恒久テストとして残す（バグを断言する形から期待動作へ）。
     - **`claim5_resumeDuringVaLeavesVaActiveInFlightPlanMode_realVamos`（⑤）**: VAMOS 割当のあと Resume しても、`FLIGHT_PLAN` かつ VA 活性にはならない。VA は維持され、指示真方位は 330° のまま。解除は 700 ft 以上。
     - **`claim5_resumeDuringVaAdvancesWaypointBelowVaAltitude_contrived`（⑤）**: VA 針路上 0.5 NM の対照経路でも、Resume の有無にかかわらず 700 ft 未満で次フィックスへ進まない。
     - **`claim6_rejectedTowerHandoffClearsInitialClimb`（⑥）**: 遠距離のタワー移管は拒否され、`initialClimb` は真のまま、滑走路方位と延期針路が残る。DER+400 ft 相当の条件で延期針路（例: 090）へ転向する。
     - **`preexisting3_finalApproachStateSurvivesRouteAssignment`（P-3）**: JOIN FINAL のあと `setFlightPlan`（または STAR 割当）すると進入コースは残らない。経路終了後、風下でも機首は素の指示針路に追従する（残留 WCA で数度ずれない）。
   - ⑧ や DEPART 速度、Hold トレース、MSAW 重なり、ATIS 再現は本スライスで反転しない。
3. **拒否の不変（テーブル）**
   - 上記の横方向指示 × 各モードについて、拒否される組み合わせでは、呼出し前後の機体スナップショットが等しい（位置、指示ベクトル、モード、VA / 出発 / 進入 / 復行 / ハンドオフの派生状態を含む）。
4. **組み合わせ**
   - VAMOS 割当 → 300 ft で Resume → 以降のティックは 700 ft 以上まで VA、その後 TT502 へ進む。
   - 延期針路付き DEPART → 拒否されたハンドオフ → DER+400 ft で転向がなお起きる。
   - JOIN FINAL → STAR 割当 → 経路終了 → 風下で機首は指示針路に一致する（残留進入 WCA なし）。
5. **有効指示のビット同一トレース**
   - 決定方針 5 のスクリプトを `4ce741f`（またはマージベース）で録ったゴールデンと、PR の出力がビット同一である。不具合修正系列は含めない。
6. **文書**
   - Backend README、およびフロント / ルート README が Resume・VA・`DEPART`・タワー移管・JOIN FINAL と位置 API のモードを説明している箇所を、本スライスの契約に合わせて更新する（下記 README）。
7. **CI**
   - 4 ジョブがマージ SHA で合格する。

---

## 影響範囲

- **Backend ドメイン**: `AircraftBase` のモードと指示エントリ、`NavigationMode`（射影または移行）、VA / 出発 / 復行 / 最終進入 / ハンドオフの付随状態。
- **API 射影**: `AircraftLocationDto` の既存キー、飛行計画応答の `procedureVaActive` / `navigationMode`。キーの削除や意味の破壊はしない。
- **テスト**: 反転した `ReviewReproTest` の対象メソッド、指示×モードのテーブル、組み合わせ、トレースゴールデン。既存の SID VA・`DEPART`・ハンドオフ・JOIN FINAL テストは、⑤⑥ P-3 の期待値修正を除き契約を維持。
- **README**: Backend を主とし、フロント / ルートがモードや Resume に触れる箇所。
- **触らないもの**: ATIS、Hold / ハンドオフの誘導式、位置ポーリング、経路折れ線の描画、シナリオ JSON の形。

---

## 実装計画

### Phase 1（型）

- 判別可能なモード型を導入する。違法状態を組み立て不能にする。既存 API キーへの射影を先に固定する。コンパイルと型検査を通す。

### Phase 2（検証してから代入）

- 横方向指示を validate → 一度の代入に切り替える。`beginTowerHandoff` の消去を検証の後（受理時だけ）に移す。拒否スナップショットのテーブルを足す。

### Phase 3（⑤ と P-3 の意味）

- VA 中 Resume を `ProcedureVa` 維持にする。`setFlightPlan` / `assignProcedureRoute` で進入コースを消す。組み合わせテストを足す。

### Phase 4（検証と文書）

- `ReviewReproTest` の対象 4 件を反転して恒久化する。有効指示スクリプトのゴールデンを `4ce741f` から録り、同一比較を CI に入れる。README を更新する。

---

## 検証

- [x] モード型により、⑤ の矛盾状態（`FLIGHT_PLAN` + VA）を表現できない。
- [x] Backend コンパイル、Frontend 型検査とビルドが通る。
- [x] 反転した `claim5_*` 2 件、`claim6_*`、`preexisting3_*` が通る。
- [x] 拒否された指示がスナップショットを変えないテーブルが通る。
- [x] 組み合わせ 3 件（VAMOS+Resume、拒否ハンドオフ後の延期針路、JOIN FINAL→STAR 後の針路）が通る。
- [x] 有効指示スクリプトのトレースが `4ce741f` のゴールデンとビット同一である。
- [x] 既存の Hold / ハンドオフ / ATIS / SID / DEPART テストを、本機能用の期待値追加以外で緩めずに通す。
- [x] CI 4 ジョブがマージ SHA で合格する。

再現テストの置き場所（検証 README どおり、恒久化するときはバグ断言を逆にする）:

- `/workspace/horus-ops/reviews/repro/backend/ReviewReproTest.java` — `claim5_resumeDuringVaLeavesVaActiveInFlightPlanMode_realVamos`、`claim5_resumeDuringVaAdvancesWaypointBelowVaAltitude_contrived`、`claim6_rejectedTowerHandoffClearsInitialClimb`、`preexisting3_finalApproachStateSurvivesRouteAssignment`

## README

- **Backend README**: VA 中の Resume は VA を維持し、高度条件まで次フィックスを進めないこと。拒否されたタワー移管は `DEPART` 状態を消さないこと。飛行計画 / STAR の再割当は JOIN FINAL の進入コースを消すこと。位置 API の `navigationMode` と `goAround` / `initialClimb` / `towerHandoff` は内部モードの射影で、既存キーの意味は有効指示の経路で変えないこと。
- **Frontend README**（およびルート README が Resume / VA / `DEP` / `TWR` / `GA` に触れる箇所）: 上記と矛盾しない一言を足す。レーダーラベルの既存意味は維持する。Hold の WCA や ATIS の説明を巻き戻さない。

## 未解決事項（Unresolved Questions）

- 復行を独立バリアントにするか、復行中専用データの中に閉じるかは、違法状態を表せなければどちらでもよい。テストで射影（`goAround=true`、`navigationMode=HEADING`）を固定する。
- トレースゴールデンのフィールド集合と桁は、レコーダ実装時に固定してテストに書く。比較はビット同一（同一レコーダ出力）とする。
