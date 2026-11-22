# リーダー線・データブロックの自動配置

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md)、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、[spec/20260318-data-block-display-items/spec.md](../20260318-data-block-display-items/spec.md)
- **リポジトリ配置**: `spec/20261122-leader-line-auto-placement/spec.md`

## 概要

レーダー上のデータブロック（ラベル）は、いま全機が同じクライアント側オフセット `(50, 50)` で始まり、サンプル開始直後に重なる。本スライスでは、新規表示時に 8 方位のリーダー方向から重なりの少ない位置を割り当て、以後も未固定のラベルだけが重なったときに自動でずらす。手動ドラッグは残し、一度動かしたラベルは固定する。API は変更しない（ラベルは既存どおりクライアント所有）。風モデル、SID／STAR、方位目盛は範囲外とする。

付随タスク: [spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md) の古い導入文で、ATIS を未着手のロードマップ項目のように書いている箇所を、6-2（使用滑走路と ATIS）実装済みに合わせて直す。

## 背景・課題

### 現状（main `9580dab` で確認）

- `Aircraft.label` は `{ x, y }` のキャンバスオフセットで、所有は `client`。位置ポーリング（`applyPolledFields`）では上書きされない。新規機体のコンストラクタと `Frontend/utility/api/location.ts` は、常に `labelX=50`, `labelY=50` を渡す。
- 描画は `airplanePosition + (label.x, -label.y)`。ヒット判定の幅は `DATA_BLOCK_HIT_WIDTH_PX = 70`、上余白 20、行高 15（`drawAircraft.ts`）。Controller のレーダーでドラッグでき、RBL ツールオン中はドラッグを開始しない。
- 自動配置も、固定／未固定の区別も無い。ギャップ台帳の「レーダー画面の残り」は、ATIS・使用滑走路が Done のあと、リーダー線の自動配置、方位目盛、セクター境界（Deferred）、風モデル（6-1）を残している。

### 課題（Problem Statement）

- ターミナルレーダーでは、データブロックがターゲットに付き、識別を維持できなければならない。全機が同じオフセットだと、開始直後や接近時に表示が重なり、便名・高度・速度が読めない。
- 手動ドラッグだけで回避するのは、研究者が最初のシナリオを開いた時点では間に合わない。

### なぜ今か（Motivation）

- 優先度 1 は #128 で済んでいる。優先度 2 のうち、待機は 3 PR、使用滑走路と ATIS は #132 で入った。次の画面ギャップとして、ラベル重なりはサンプル開始ですぐ見える。
- 配置は Frontend に閉じ、既存 API を変えなくてよい。風モデル（GS・IAS・待機への波及）や SID／STAR のカタログより、1 PR に収まり、テストとブラウザで検証しやすい。

---

## 方針

### 決定方針（Decision）

1. **根拠となる一次資料**
   - **日本**: 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』（JAPA 2025-03 公開、改正 145、国空制第 591 号、令和 7.3.20 適用）。
     - 定義「データブロック (Data block)」: レーダー画面上に表示される航空機の識別符号、対地速度等を内容とする情報の表示群。
     - (Ⅳ)３(２)ｃ・ｄ（ページ (Ⅳ)－3－1 付近）: TEPS、TAPS または ARTS では、ターゲットにデータブロックが自動的に付加されたことを確認する方法があり、付加されているデータブロックを識別の維持に使う（コースト状態の略号がある場合を除く）。
   - **国土交通省航空局**『[航空保安業務の概要（2026）](https://www.mlit.go.jp/koku/content/002013001.pdf)』: TAPS／TEPS の表示例で、ターゲットから離れたデータブロックと、それを結ぶ線が示される。本スライスは、TAPS が自動オフセットを持つとは主張しない（その手順は当該資料に書かれていない）。
   - **FAA Order JO 7110.65BB Change 3**（効力 2026-07-09）:
     - [5-3-4 Terminal Automation Systems Identification Methods](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_3.html): データブロックが適切に見えていることを識別の前提の一部とする。「Use the data block to maintain target identity unless it is in a coast status or displaced from the appropriate target。」「A displaced data block must be updated at all times。」
     - [5-3-9 DATA BLOCKS (TERMINAL)](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_3.html): データブロックを適切なターゲットシンボルに関連付けたまま保持し、識別を維持する。最低限、便名と高度を表示する。
     - [5-14-8 TRACK SUSPEND FUNCTION](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_14.html)（STARS-Terminal）: 待機経路上や最終進入付近でデータブロックが重なり、運用できない状態になるときに限りトラックサスペンドを使う、とする。重なりが運用上の問題であることが一次資料で示されている。本スライスはサスペンド機能自体を実装しない。
   - **FAA Order JO 6191.5** [STARS Maintenance Technical Handbook (G4)](https://www.faa.gov/documentLibrary/media/Notice/JO_6191.5_Standard_Terminal_Automation_Replacement_System_(STARS)_Maintenance_Technical_Handbook_(MTHB)_Generation4_(G4).pdf)（2015-11-07）: TRACON の TCW 機能に hand-offs、altitude filter limits、**auto offset**、select configuration、quick look 等が含まれる。自動オフセットの詳細アルゴリズムは公開本文に無い。本スライスは STARS のビット一致実装ではなく、同じ目的（重なった表示群をずらして読めるようにする）の、テスト可能な配置規則とする。

2. **配置規則（単一の Frontend 定義源。API は変えない）**
   - オフセットはターゲットからの相対座標のまま（既存の `label.x` / `label.y`）。リーダー長の基準は現行の 50 px。方向は 8 方位（N, NE, E, SE, S, SW, W, NW）。ターゲット直上（中央）には置かない。
   - データブロックの矩形は、既存のヒット判定寸法（幅 70 px、上余白 20 px、行高 15 px、および現行の行数から求まる高さ）を使う。新しい寸法定数を散在させない。
   - **新規表示時**（位置ポーリングで初めてリストに乗る機体）: コールサインの安定した順で 1 機ずつ、8 方位を固定順に試し、既存ラベル矩形および他機ターゲット（シンボル半径は描画側の既存値）と重ならない最初の方位を選ぶ。全て重なるときは、重なり面積が最小の方位を選ぶ（同点は方位順の先）。
   - **自動再配置**: 未固定（一度もドラッグしていない）ラベルだけを対象にする。矩形が他ラベルまたは画面外と重なったときにだけ再計算する。固定済みラベルは動かさない。毎フレーム無条件に動かさない（ちらつきを防ぐ）。
   - **手動ドラッグ**: 現行どおり移動でき、その時点で当該ラベルを固定する。RBL ツールオン中にドラッグを開始しない規則は維持する。固定を解除して再自動配置する操作（ダブルクリックまたは「Reset label offsets」）を 1 つ用意する。
   - Controller と Operator のレーダーの両方に適用する。配置コードは `utility/aircraft/` など 1 モジュールに置き、描画コンポーネントから呼ぶ。

3. **API**
   - **変更しない。** 位置 DTO、Hold、ATIS、`JOIN FINAL` 等にフィールドを足さない。`label` はクライアント所有のまま。
   - 固定フラグ（例: `labelPinned`）を `Aircraft` に足す場合は `client` 所有とし、`AIRCRAFT_FIELD_PARTITION` を更新する。ポーリングでは上書きしない。

4. **テストで固定する数値例**
   - 同一キャンバス座標に 2 機（コールサイン順 A なら B）: 異なる 8 方位が割り当てられ、ヒット矩形が重ならない（交差面積 0）。
   - 3 機以上でも、可能な限り交差面積 0。どうしても足りない場合だけ最小交差。
   - 1 機を固定したあと再配置しても、そのオフセットは変わらない。
   - 同じ入力（位置・コールサイン集合）なら、同じオフセットになる（決定論）。
   - 製品コード（配置モジュールとヒット寸法）をテストが直接呼ぶ。

### 検討した他案（Alternatives Considered）

- **案 A: 風モデル（6-1）を先にする。** 採用しなかった理由: 均一風だけでも IAS→GS、針路とトラック、待機のレーストラックと時間レグ、ATIS 本文（#132 の `WIND AND WEATHER NOT AVAILABLE`）に波及する。既存の無風前提テストを広く直す必要があり、1 PR に収めるには切りすぎるか、中途半端になる。
- **案 B: SID／STAR の第 1 スライス。** 採用しなかった理由: 公示経路のカタログと制約が必要で、サイズが 1 PR を超える。
- **案 C: 方位目盛（コンパスローズ）。** 採用しなかった理由: シミュレータの針路は磁偏差なしの測地フレームであり、磁気方位の目盛を出すと誤解を招く。研究上の急務ではない。
- **案 D: 毎ティック全機を自動再配置し、ドラッグを一時的なだけにする。** 採用しなかった理由: 管制官が置いた位置がすぐ戻ると使いにくい。STARS でも手動方向指定と自動オフセットは区別される。
- **案 E: 連続的な自由角度オフセット（8 方位に限らない）。** 採用しなかった理由: テストが曖昧になる。第 1 版は 8 方位に固定する。

### トレードオフ（Trade-offs）

- **メリット**: サンプル開始からラベルが読める。ドラッグは残る。API を壊さない。
- **デメリット / 受容する制約**: STARS／TAPS の実装そのものではない。8 方位と固定長なので、混雑時は完全に重なりゼロにできない場合がある。風や SID／STAR は進まない。

### 非目標（Non-goals）

- 風モデル（6-1）、ATIS 本文への風の記入
- SID／STAR、公示待機、方位目盛、セクター境界
- STARS のトラックサスペンド、コンフリクト中のドラッグ禁止、ビット一致の auto offset
- リーダー長の可変 UI、9 キーによる手動方位入力（第 1 版はドラッグで足りる）
- 位置 API やその他 Backend 契約の変更

### 付随タスク（ギャップ台帳の ATIS 表記）

`spec/20261017-terminal-radar-gaps/spec.md` で、ATIS を未着手のように読める導入文を、6-2 実装済みに合わせる。実装コードは変えない。少なくとも次を直す。

- 「セクター境界、シミュレーション速度の可変、セッション記録、複数セクター、風、ATIS はロードマップ上の別項目である。」→ ATIS を未着手の列挙から外し、ATIS・使用滑走路（6-2）は [20261122-atis-runway-in-use](../20261122-atis-runway-in-use/spec.md) で実装済みであること、風（6-1）は残課題であることを書く。
- 課題の「使用滑走路と風がない。」→ 使用滑走路と ATIS は実装済みとし、残るのは風モデルであることに直す。同じ箇条に残っている、既に実装済みの MSAW・緊急スクオーク・進入端距離を「ない」と書いている場合は、その範囲でも現状に合わせる（台帳本文の全面改稿はしない）。
- 「既存ロードマップ項目（…風、ATIS、ハンドオフ…）」→ ATIS を未着手扱いの括弧から外し、6-2 済みを一文で示す。

レーダー画面の残りの箇条（既に ATIS・使用滑走路 → Done と書いてある箇所）は、矛盾が無ければそのままとする。

---

## 完了条件（Success Criteria）

- [x] `spec/20261122-leader-line-auto-placement/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている
- [x] 位置 API、Hold、ATIS、`JOIN FINAL`／復行／タワー移管を含む既存 API の差分が無い（契約を足す必要もない）
- [x] 配置の定義源が Frontend の 1 モジュールにあり、上記の数値テスト（2 機の非交差、固定後の不変、決定論）が製品コードを直接呼ぶ
- [x] 新規機体は `(50, 50)` 固定ではなく、自動配置された 8 方位オフセットを持つ。既存機のポーリングでオフセットがリセットされない
- [x] ドラッグで動かしたラベルは、その後の自動再配置で動かない。固定解除の操作が 1 つあり、解除後は再配置される
- [x] RBL ツールオン中にラベルドラッグが始まらない規則が維持される
- [x] `Aircraft` に固定フラグを足す場合は `client` 所有で、フィールド分割の型チェックが通る
- [x] Frontend のテストとビルド、Backend のテスト（回帰）が合格する
- [ ] CI の 4 ジョブが、マージするコミットの SHA で全部合格する
- [x] Frontend README に、自動配置・8 方位・手動固定と解除、API を変えていないことを書く。Backend／ルート README に変更があれば同じ PR に含める
- [x] ギャップ台帳の付随タスク（ATIS の古い導入文）が直っている。台帳の「レーダー画面の残り」から、本スライス完了後のリーダー線自動配置を外す
- [x] ブラウザ確認とスクリーンショット: サンプルを開始し、複数機のラベルが同じ右下に重なっていない。1 機をドラッグして位置が保持される。固定を解除すると再配置される

---

## 影響範囲

- **Frontend**: 配置モジュール、`location.ts` の新規機初期化、`aircraftClass.ts`（必要なら固定フラグと分割）、`radarCanvas.tsx` のドラッグ／解除、`drawAircraft.ts` の寸法再利用、Jest テスト
- **README**: Frontend（ルート・Backend は変更が無い限りそのままでよい）
- **spec/20261017-terminal-radar-gaps/spec.md**（ATIS 導入文と、完了後のリーダー線行）
- **spec/spec.md**: 言及があれば更新
- **Backend / API**: 変更しない

---

## 実装計画

### Phase 1（Must-have）

1. 配置モジュールとヒット矩形の数値テストを先に書く。
2. 新規表示時の割当と、未固定ラベルの重なり時再配置、ドラッグ固定と解除を入れる。
3. README とギャップ台帳（ATIS 導入文を含む）を更新する。

### Phase 2（このスライスの外）

- リーダー長の可変、キーによる方位指定
- 風モデル、SID／STAR、方位目盛

---

## 検証

- [x] 配置テストが未実装では失敗し、実装後は成功する
- [x] API を変える差分が PR に含まれていない
- [x] ギャップ台帳の導入文に、ATIS を未着手とする表現が残っていない
- [x] ブラウザで非重なり・ドラッグ固定・解除を確認し、スクリーンショットを残す
- [ ] CI 4 ジョブがマージ SHA で合格する

---

## 未解決事項（Unresolved Questions）

- 画面外に出た未固定ラベルを、重なりが無くても内側の方位へ戻すか。推奨は戻す（識別維持のため）。
- Operator と Controller で配置状態を共有するか。ラベルはクライアント所有なので、第 1 版は画面ごとに独立でよい。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)
- [spec/20260318-data-block-display-items/spec.md](../20260318-data-block-display-items/spec.md)
- 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』: [JAPA 公開 PDF（2025-03）](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)（データブロックの定義、(Ⅳ)３(２)ｃ・ｄ）
- 国土交通省航空局『航空保安業務の概要（2026）』: [PDF](https://www.mlit.go.jp/koku/content/002013001.pdf)（TAPS／TEPS の表示例）
- FAA Order JO 7110.65BB Change 3: [5-3 Radar Identification](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_3.html)（5-3-4、5-3-9）、[5-14 STARS-Terminal](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_14.html)（5-14-8）
- FAA Order JO 6191.5: [STARS MTHB G4](https://www.faa.gov/documentLibrary/media/Notice/JO_6191.5_Standard_Terminal_Automation_Replacement_System_(STARS)_Maintenance_Technical_Handbook_(MTHB)_Generation4_(G4).pdf)（TCW の auto offset）
- [Frontend/README.md](../../Frontend/README.md)
