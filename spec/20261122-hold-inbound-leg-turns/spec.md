# 待機指示のインバウンド・レグ・左旋回

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md) フェーズ 3-2、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、[spec/20260509-phase3-holding-pattern/spec.md](../20260509-phase3-holding-pattern/spec.md)

## 概要

既存の Hold At Fix（フィックスへ向かい、その後レーストラックを周回する初期実装）に、管制方式基準が求める待機指示の残り要素を足す。Operator は入方向コース、出方向レグ（分または海里）、旋回方向（右／左）を指定でき、機体はその指示どおりに周回する。公示された待機経路のカタログ、EAT／EFC、セクター進入（ダイレクト／パラレル／ティアドロップ）は本スライスの外とする。既存の `POST /api/aircraft/{callsign}/hold` を加算拡張し、新しい欄を省略した要求は現行どおり右旋回・出方向 4.0 NM で受け付ける。

## 背景・課題

### 現状（main `d0dba95` で確認）

- `POST /api/aircraft/{callsign}/hold` は `fixName` 必須、`turnDirection` 任意。DTO と列挙型には `LEFT` / `RIGHT` があるが、`FlightPlanController` は `RIGHT` 以外を 400 で拒否する。Frontend の `holdAtFix` は常に `turnDirection: "RIGHT"` を送る。README も「初期版は右旋回固定」と書いている。
- 入方向は指示できない。`AircraftBase.calculateDeterministicInboundBearing` はフィックス名の `hashCode` を 360 で割った余りを方位に使う（名前が空なら緯度経度のハッシュ）。Backend README は、同一 Fix なら侵入方向に依らず同じトラック向きになると説明している。運用上の入方向コースではない。
- 出方向レグは定数 `HOLD_OUTBOUND_LEG_DISTANCE_NM = 4.0` のみ。時間指定も、任意の海里指定もできない。
- 飛行は、まずフィックスへ向かい（`holdingEntryCompleted` まで）、その後フィックスと出方向目標点を往復する。右旋回を保つための `shouldForceRightTurnInHolding` はあるが、左旋回の対称処理は無い。
- データブロックは `navigationMode=HOLDING` のとき `HLD` を出す。Resume で待機を終了できる。
- 子 spec [20260509-phase3-holding-pattern](../20260509-phase3-holding-pattern/spec.md) は Status が Draft のままである。ギャップ台帳は「ホールドのレグ時間・インバウンド・左旋回・公示パターン」を到着・出発の残りに置いている。

### 課題（Problem Statement）

- ターミナルレーダーの到着処理では、フィックスでの待機が間隔と遅延の基本手段である。入方向もレグも指定できず、左旋回も拒否される状態では、規程どおりの待機指示を出しても、レーダー上の周回が指示と一致しない。
- 現行の入方向がフィックス名のハッシュであるため、同じ指示を繰り返しても、公示や「フィックスへ向かっていたコース」とは無関係な向きで回る。

### なぜ今か（Motivation）

- 滑走路・最終進入・復行・タワー移管・MSAW・進入端距離は揃った。残る到着の飛行操作のうち、既に経路と UI の受け皿があるのが待機である。不足は指示パラメータと周回幾何であり、新規のドメインを起こす SID／STAR や風モデルより 1 PR に収まりやすい。
- 研究者が「指定したコースとレグで、右または左に待たせる」実験を、画面で直接確認できるようになる。

---

## 方針

### 決定方針（Decision）

1. **根拠となる一次資料**
   - **日本（現行手続き）**: 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』（JAPA が 2025-03 に公開した版。改正 145、国空制第 591 号、令和 7.3.20 適用）(Ⅱ)６(１)ｂ【待機指示】。待機の指示は次を含む。ただし公示経路では「公示どおり」と明示して (c) 以下を省略できる。
     - (a) 待機フィックスからの関係方位
     - (b) 待機フィックス（承認限界点と同一なら省略可）
     - (c) 入方向経路、またはその放射方位・コース・ベアリング・航空路・経路
     - (d) 出方向距離（DME 使用時）または分を単位とする飛行時間
     - (e) 旋回方向（右旋回は省略可）
     - 用語例: `HOLD〔direction〕OF〔fix〕ON〔specified inbound track,radial, course, bearing, airway or route〕〔number〕MINUTE / MILE LEG, LEFT / (RIGHT) TURNS.`
     - 同項の例: `Hold northeast of ARMOR on inbound track 209 degrees one and a half minutes leg, left turns.` / `Hold west of Oshima VORTAC on 275 radial one minute leg.` / `Hold north of KASMI on Choshi VORTAC 347 radial 7 mile leg, left turns.`
     - 公示どおりの省略用語と、EAT／EFC（「進入予定〔時刻〕です」「追加管制承認予定〔時刻〕です」）は同項にあるが、本スライスでは実装しない（非目標）。
   - **FAA**: Order JO 7110.65BB Change 3（効力 2026-07-09）4-6-4 Holding Instructions。指示に含めるのは、フィックスからの方向、フィックス、乗る radial/course/bearing/track/airway 等、DME/RNAV なら海里のレグ（分はパイロットの要求または必要時）、左旋回のとき（または要求・必要時）の旋回方向。用語は `HOLD (direction) OF (fix/waypoint) ON (specified …) (number of minutes/miles) MINUTE/MILE LEG. LEFT/RIGHT TURNS.` 4-6-1 は公示パターンなら `AS PUBLISHED` で省略できるとする（本スライスではカタログを持たないため使わない）。
   - **タイミングの対照**: FAA Aeronautical Information Publication ENR 1.5 は、時間指定の標準を「14,000 ft MSL 以下はインバウンド 1 分、超えは 1.5 分。最初のアウトバウンドも同じ時間で飛び、以後はインバウンドが所定時間になるよう調整する」と書く。ICAO Doc 8168（PANS-OPS）は国際的な待機方式の出典だが、本文は有償のため本 spec では段落番号を断定しない。第 1 版の時間レグは、指示された分数だけを出方向距離に換算する（風なし、対地速度は指示時点または各出方向開始時点）。14,000 ft での 1 分／1.5 分は、Operator UI の既定値にだけ使う。
   - **公示が無く指示も欠けたとき**: JAPA E-journal Vol.045（2024-04-17）が AIP Japan ENR 1.5-1.5.1 を引用し、「公示の待機経路がない場合は、指定された高度を維持し、当該機が当該地点へ飛行してきたコース上で新しい指示を受理するまで標準待機を行う」とする。AIS Japan の原文は今回取得していない。本スライスの Operator は入方向を必ず送り、この「指示なし」の標準は API の省略時フォールバックには使わない。

2. **API は既存 `POST /api/aircraft/{callsign}/hold` の加算拡張に限る。** 位置 DTO と他エンドポイントの動きは変えない。
   - 既存: `fixName`（必須）、`turnDirection`（`RIGHT` / `LEFT`、省略時 `RIGHT`）。
   - 追加（いずれも任意）:
     - `inboundCourseDeg`: 0 以上 360 未満の入方向コース（シミュレータの針路と同じ測地フレーム。磁偏差は掛けない）。
     - `legMinutes`: 正の分数（例: 1 と 1.5）。出方向距離は、その時点の対地速度 [kt] × (`legMinutes` / 60) 海里。
     - `legNm`: 正の海里。`legMinutes` と同時指定は 400。
   - **省略時の互換**: `turnDirection` 省略は右旋回のまま。`legMinutes` も `legNm` も省略なら出方向は現行どおり 4.0 NM。`inboundCourseDeg` を省略したときは、現行のフィックス名ハッシュによる入方向を維持する（既存クライアントと「同一 Fix なら同じ向き」の説明を壊さない）。この省略時の向きは運用コースではないことを README に書く。Operator の新しい UI は入方向を省略せず送る。
   - `turnDirection=LEFT` は 200 で受け、左回りで周回する。契約テスト（`FlightPlanApiIntegrationTest` の LEFT が 400 であるケース）は更新する。`UranosAPI.yml` にフィールドと制約を追記する。

3. **飛行幾何**
   - 入方向コース `I` に対し、出方向目標はフィックスから方位 `I+180`、距離は上記のレグ。既存の「まずフィックスへ向かい、その後フィックスと出方向点を往復する」流れは維持する。
   - 右旋回の強制を左旋回でも対称に実装する（最短回りで逆方向へ切れないようにする）。
   - 時間レグは風も減速プロファイルも見ない。対地速度が 1 kt 未満のときは 400 とするか、レグを受け付けずエラーにする。

4. **Frontend（Operator）**
   - Fix 選択の Hold に、入方向（度）、レグ（分または NM）、旋回（右／左）の入力を足す。既定は右旋回、レグは現在高度が 14,000 ft 以下なら 1 分、超えなら 1.5 分、入方向は選択中機体からその Fix への前方方位を初期表示し、編集できる。
   - `holdAtFix` はこれらの値を API へ送る。Controller 側に新しい指示 API は足さない（役割分担は現状のまま）。
   - レーダーの `HLD` 表示は維持する。入方向やレグをデータブロックへ出すことは Must にしない。

5. **単一の定義源とテスト**
   - 出方向点の計算（コース、距離、時間×対地速度）は Backend の製品コードに置き、ユニットテストがそのコードを呼ぶ。例（機械的に固定する）:
     - 入方向 090°、180 kt、1.00 分 → 出方向距離 3.00 NM、出方向方位 270°
     - 入方向 209°、1.5 分、200 kt → 出方向距離 5.00 NM
     - 7.00 NM 指定なら速度に依らず 7.00 NM
     - 左旋回と右旋回で、同じ入方向・同じレグなら出方向点は一致し、フィックス到達後の旋回符号が逆になる
     - `inboundCourseDeg` なしの要求は、従前どおり同じ `fixName` なら同じ入方向になる（ハッシュ互換）
   - 許容差は距離 0.05 NM、方位 1°（平面オフセットの既存実装を使う場合はその実装に対して固定し、式を変えるならテストも同じ定義源に合わせる）。

### 検討した他案（Alternatives Considered）

- **案 A: 公示待機（AS PUBLISHED）のカタログから入る。** 採用しなかった理由: AIP の待機経路データを整備する必要があり、1 PR を超える。指示要素がまだ飛べない状態でカタログだけあっても訓練に使えない。
- **案 B: 省略時の入方向を「フィックスへ向かっていたコース」に変える。** 採用しなかった理由: 既存 API の「同一 Fix なら向きが同じ」を壊す。指示なしの標準待機は非目標とし、UI が入方向を必ず送る。
- **案 C: セクター進入（ダイレクト／パラレル／ティアドロップ）まで入れる。** 採用しなかった理由: 周回そのものの指示要素より実装と検証が大きい。第 1 版は「フィックスへ入ってからレーストラック」を維持する。
- **案 D: 風・ATIS・使用滑走路、またはリーダー線の自動配置を先にする。** 風は対地速度・待機・進入の計算全体に波及する。リーダー線は画面改善としては小さいが、待機指示のような管制手順の再現にはならない。

### トレードオフ（Trade-offs）

- **メリット**: 既存の Hold API とレーストラック実装を再利用し、規程が列挙する指示要素のうち飛行に必要なものを 1 PR で揃えられる。レーダー上の周回が見える。
- **デメリット / 受容する制約**: 公示経路、EAT／EFC、進入セクター、待機速度制限、風修正は無い。省略時の入方向はハッシュのままなので、古いクライアントだけでは運用コースにならない。方位は磁偏差を掛けない（既存の針路と同じ）。

### 非目標（Non-goals）

- AIP の公示待機カタログと `AS PUBLISHED`
- EAT／EFC／「遅延時間未定」の時刻管理
- ダイレクト／パラレル／ティアドロップの進入セクター
- 待機の最大速度、高度帯ごとの速度表
- 風の修正、磁偏差
- 位置 DTO の変更、Controller からの Hold API
- SID／STAR、離陸、セッション記録

---

## 完了条件（Success Criteria）

- [x] `spec/20261122-hold-inbound-leg-turns/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている
- [x] `POST /api/aircraft/{callsign}/hold` 以外のエンドポイントの動きが変わっていない。本スライスの API 変更はこのエンドポイントへの加算フィールドと、`LEFT` の受理に限る
- [x] `UranosAPI.yml` に `inboundCourseDeg` / `legMinutes` / `legNm` / `turnDirection` の制約が書かれ、契約テストがある
- [x] `fixName` と `turnDirection=RIGHT` だけの要求は、従来どおり 200 で `HOLDING` になり、出方向 4.0 NM、入方向は同一 `fixName` なら従来と同じ（ハッシュ互換）
- [x] `turnDirection=LEFT` は 200 で受理され、左回りで周回する（既存の 400 期待テストは更新する）
- [x] `legMinutes` と `legNm` の同時指定、範囲外の方位・非正のレグは 400
- [x] ユニットテストが製品コードの出方向計算を、上記の数値例（3.00 NM / 5.00 NM / 7.00 NM、左右の旋回符号）に対して固定する
- [x] Operator の Hold UI から入方向・レグ・旋回を送れ、既定の 1 分／1.5 分と「機体から Fix への方位」初期値が入る
- [x] Frontend のテストとビルド、Backend のテストが合格する
- [x] CI の 4 ジョブが、マージするコミットの SHA で全部合格する
- [x] ルート README に変更があれば同じ PR に含める。Backend README と Frontend README の「右旋回固定」「ハッシュで同一向き」の説明を、加算フィールドと省略時互換に更新する
- [x] `spec/spec.md` の 3-2 とギャップ台帳のホールド行を、公示パターンが残課題であることを残して更新する
- [x] ブラウザ確認: サンプルを開始し、Operator で到着機を既知の Fix に、入方向と 1 分レグ・左旋回で Hold させる。レーダーでフィックス到着後に左のレーストラックが見え、Resume で待機が終わる。同様に右旋回でも周回できる

---

## 影響範囲

- **Backend**: `HoldRequestDto`、`FlightPlanController`、`AircraftBase`（入方向・レグ・左旋回）、`UranosAPI.yml`、`FlightPlanApiIntegrationTest` と幾何のユニットテスト
- **Frontend**: `utility/api/flightPlan.ts`、`selectFixMode.tsx`（およびそのテスト）、必要なら Hold 用の小さな入力 UI
- **README**: Backend / Frontend（ルートに Hold の説明があれば同様）
- **spec/spec.md**、**spec/20261017-terminal-radar-gaps/spec.md**、Draft のままの 3-2 子 spec への参照更新
- **位置 API**: 変更しない

---

## 実装計画

### Phase 1（Must-have）

1. 出方向点の計算を指示パラメータ付きで実装し、数値テストを先に置く。
2. API の加算フィールド、LEFT の受理、バリデーション、OpenAPI、契約テスト。
3. 左旋回の周回、Operator UI、README と台帳の更新。

### Phase 2（このスライスの外）

- 公示待機カタログと `AS PUBLISHED`
- EAT／EFC
- 進入セクター
- データブロックへの入方向・レグ表示

---

## 検証

- [x] 完了条件の数値テストと契約テストが、未実装では失敗し、実装後は成功する
- [x] 既存の `fixName` + 右旋回のみの要求が互換のまま通る
- [x] ブラウザで左右のレーストラックと Resume を確認する
- [x] 位置 API を変える差分が PR に含まれていない

---

## 未解決事項（Unresolved Questions）

- 時間レグの対地速度を「指示時点で固定する」か「各出方向の開始時に取り直す」か。速度指示が待機中に変わる研究をするなら後者がよい。第 1 版は各出方向の開始時とする案を推奨する。
- 関係方位（northeast 等）を入力欄にするか、入方向と旋回から表示だけするか。飛行には不要なので、第 1 版は表示任意とする。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20260509-phase3-holding-pattern/spec.md](../20260509-phase3-holding-pattern/spec.md)
- [spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)
- 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』: [JAPA 公開 PDF（2025-03）](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)（(Ⅱ)６(１)ｂ 待機指示）
- JAPA E-journal Vol.045（2024-04-17）: [PDF](https://www.japa.or.jp/wp-content/uploads/2018/07/47af0677ab415e16ce357fd084f6d50a.pdf)（AIP Japan ENR 1.5-1.5.1 の引用。AIS 原文は未取得）
- FAA Order JO 7110.65BB Change 3: [4-6 Holding Aircraft](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap4_section_6.html)（4-6-1、4-6-4）
- FAA AIP ENR 1.5: [Holding, Approach, and Departure Procedures](https://www.faa.gov/air_traffic/publications/atpubs/aip_html/part2_enr_section_1.5.html)（時間レグの 14,000 ft / 1 分 / 1.5 分。米国 AIP であり、AIP Japan の条文そのものではない）
- ICAO Doc 8168 (PANS-OPS): 待機方式の国際的な出典。本文は有償のため、本 spec では段落番号を置かない
- [Backend/README.md](../../Backend/README.md)
- [Frontend/README.md](../../Frontend/README.md)
