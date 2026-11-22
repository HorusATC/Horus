# Hold / タワー移管の風一貫ガイダンス（PR-A）

## メタデータ

- **Status**: Done
- **Author**: Cloud Agent
- **Date**: 2026-11-23
- **関連**: [spec/20261122-uniform-wind/spec.md](../20261122-uniform-wind/spec.md)、[spec/20261017-tower-handoff/spec.md](../20261017-tower-handoff/spec.md)、[spec/20261122-hold-racetrack-overlay/spec.md](../20261122-hold-racetrack-overlay/spec.md)、[spec/20261122-hold-inbound-leg-turns/spec.md](../20261122-hold-inbound-leg-turns/spec.md)、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)
- **リポジトリ配置**: `spec/20261123-hold-handoff-wind/spec.md`
- **検証根拠**: 外部レビュー #117–#139 の検証レポート（N1, ②, ③, ④, ⑧）

## 概要

待機（Hold）とタワー移管予測を、一様風モデルと矛盾しない単一の運動／ガイダンス経路に揃える。ティックごとにちょうど一つの誘導種（針路飛行／コース維持／フィックスへ舵）を型で表し、コース維持にだけ WCA と cross-track 収束を掛け、フェーズ遷移の判定は誘導が使う同じ目標と比較する。ライブティックと移管予測は同じ純関数の運動ステップを共有する。Hold / JOIN FINAL / tower-handoff の API 契約は変えない。

## 背景・課題

### 現状

- [uniform-wind](../20261122-uniform-wind/spec.md) §5 は待機のインバウンド／アウトバウンド直線レグで一次 WCA を要求する。実装は `OUTBOUND_LEG` で `holdingOutboundTarget != null` のとき WCA を落とすため、通常レーストラックでは常に WCA 無しになる（欠陥 ②）。
- 風下のアウトバウンドずれに加え、`INBOUND_LEG` は「コース + WCA」だけでフィックスへ収束しないため、0.25 NM 通過閾値に届かず 1–2 周でフィックスから離れ続ける（欠陥 N1。5 kt でも 30 分で約 60 NM）。
- 旋回の誘導目標は course+WCA、フェーズ捕捉は生コース ±2° で、目標が微後退すると強制旋回が ~360° 回り続ける（欠陥 ③）。
- タワー移管の `maintainedTrackReachesThreshold` は針路×GS・無風で進み、ライブは TAS+風→トラック。受理後に 0.5 NM に届かない／拒否だが実際は届く、の両方が起きる（欠陥 ④）。受理後は進入指示が 409 のため機体が孤児化する。
- `HelicopterFlightBehavior` は `heading` で位置を進め、`FighterJet.performTacticalManeuver` は 3 引数 `AircraftVector` で風状態を落とす。いずれも製品の生成経路からは到達しない死コードだが、風対応の罠として残る（欠陥 ⑧）。
- 既存の待機・進入テストは無風のみ。移管予測の「風は含まない」は [tower-handoff](../20261017-tower-handoff/spec.md) の受容制約に書かれていたが、一様風導入後は誤りになる。

### 課題（Problem Statement）

- 風を 1 kt でも入れると Hold がレーストラックを保てない。
- 移管受理とライブ到達が一致せず、受理機が指示不能のまま消えないことがある。
- WCA の適用条件がフェーズ表と null チェックの組み合わせで、悪い状態が表現できてしまう。

### なぜ今か（Motivation）

検証で N1 は P1、かつ ATIS 経由で任意の非静穏風が全機に掛かるため到達性が高い。PR-A はユーザー可視の運動正しさを先に直す。

---

## 方針

### 決定方針（Decision）

1. **誘導は sealed 型でティックあたりちょうど一つ**
   - `Guidance = FlyHeading(headingDeg) | TrackCourse(courseDeg, anchorFix) | SteerTo(fix)`
   - **WCA は `TrackCourse` のときだけ**適用する。フェーズ表の `shouldApplyWindCorrectionAngle` は廃する。
   - `TrackCourse` は cross-track を閉じる: 望むトラック = course − clamp(k · xtk, −30°, +30°)、そのトラックを保つ見出しを WCA で求める（xtk はコース右が正 → 右ずれは左へ舵）。
   - `SteerTo` はフィックスへの方位を針路にする（現行 Direct To。WCA なし）。
   - `FlyHeading` は指示針路そのもの（ベクトル指示・待機旋回）。

2. **待機フェーズ → Guidance の写像**
   - `OUTBOUND_LEG` → `TrackCourse(outbound, turnExit)`（`holdingOutboundTarget` の有無に依存しない）
   - `INBOUND_LEG` → `TrackCourse(inbound, fix)`
   - エントリの直線レグ（teardrop / parallel leg）→ `FlyHeading`（AIM 進入形状の無風テストを維持。レーストラック直線レグだけ `TrackCourse`）
   - 旋回（outbound / inbound / entry turns）→ `FlyHeading`（強制旋回方向付き）
   - フィックス到達前の ENTRY → `SteerTo(fix)`
   - 確立済み最終進入（`finalApproachCourseHeading`）→ `TrackCourse(course, …)`
   - 通常 `HEADING` → `FlyHeading`
   - Direct To / フライトプラン追従 → `SteerTo(next)`

3. **フェーズ捕捉は誘導と同じ目標**
   - 旋回は `FlyHeading(course)` なので、完了は `|heading − course| ≤ 2°`（誘導見出しと同一）。トラックだけの捕捉は使わない（風下でトラックが先にコースへ入り、OUTBOUND_LEG 計時が始まらないため）。
   - 強制旋回ヘルパーで、目標が微後退して残り角が >180° になる場合は到達とみなす（359.94° オーバーシュートを除去）。

4. **インバウンドのフィックス通過**
   - 半径 0.25 NM ではなく、フィックスのアビーム線の along-track 交差: 直前 along-track &lt; 0 かつ今回 ≥ 0、かつ `|xtk| ≤ 1.0 NM`。
   - 初回 ENTRY 到着は `SteerTo` のまま近づくため、距離閾値を 1.0 NM に広げ「閾値内かつ遠ざかり」で通過とする（風下でも取りこぼさない）。

5. **単一の運動ステップ**
   - 純関数 `KinematicStep.next(state, guidance, wind, targets, limits, turnConstraint, dt)` が「誘導見出し → 旋回 → TAS／風三角形 → トラック方向の位置」を返す。
   - ライブティックの水平運動と `maintainedTrackReachesThreshold` はこれを共有する。移管予測はライブと同じ風・WCA・トラックを見る。
   - 受理 ⇔ 同一初期条件でライブが水平 0.5 NM 以内に入る、を性質としてテストする。

6. **⑧ 死コードの整理**
   - `HelicopterFlightBehavior` は `motionDirectionDeg()`（トラック）で水平移動する。
   - `performTacticalManeuver` は同じ風三角形で track/TAS を更新する（削除せず共有経路へ寄せる）。

7. **無風の意図的差分（ビット一致の例外）**
   - 無風でも直線レグは `TrackCourse`（従来の `SteerTo(outboundEnd)` / `SteerTo(fix)` ではない）。xtk≈0 ならコース＝従来の針路に近く、既存の進入セクター／形状テストは通す。
   - インバウンド通過判定と ENTRY 閾値の変更は無風でも効くが、レーストラック周回は維持する。
   - `NoWindKinematicsGoldenTest` の HEADING 運動ゴールデンは変えない。Hold 周回の位置列がビット一致しない場合は本節を根拠とする。

8. **API**
   - Hold / JOIN FINAL / tower-handoff / ATIS / 位置 API の要求・応答形は変えない。

### 検討した他案（Alternatives Considered）

- **案 A: `shouldApplyWindCorrectionAngle` の OUTBOUND 条件だけ直す。** 採用しなかった理由: INBOUND の平行ずれ（N1）と移管予測の二重実装が残る。
- **案 B: インバウンドだけ SteerTo(fix) に戻す。** 採用しなかった理由: コース維持＋WCA の契約と矛盾し、オーバーレイと機影がずれる。
- **案 C: 移管予測に経験的な横風マージンを足す。** 採用しなかった理由: ライブと別式のままドリフトする。同一ステップが必要。

### トレードオフ（Trade-offs）

- **メリット**: 悪い状態（OUTBOUND で WCA 無し、予測とライブの不一致）が型と単一路径で表現しにくくなる。風あり Hold が訓練可能になる。
- **デメリット / 受容する制約**: 無風直線レグの誘導が SteerTo から TrackCourse に変わる（上記 §7）。レグ時間の公式風補正は依然非目標。PR-C の LateralMode 統合は行わない。

### 非目標（Non-goals）

- ATIS 編集の下書き／revision（PR-B）
- Resume/VA・拒否コマンドの不変性・LateralMode 統合（PR-C）
- レーダー表示・並行滑走路選定（PR-D）
- 待機レグ時間の公式風補正、公示ホールディング、EAT／EFC

---

## 完了条件（Success Criteria）

- [x] `spec/20261123-hold-handoff-wind/spec.md` があり、実装後 Status が Done
- [x] 30 シミュレーション分、RIGHT/LEFT × 時間 1 分 / 距離 5 NM × 風 {欠測, 090/05, 090/30, 270/30, 360/30}:
  - 周回数 ≥ 5
  - フェーズが循環する
  - フィックスからの最大距離 ≤ 9 NM
  - アウトバウンド直線レグの平均トラックがコース ±2° 以内
  - 各直線レグ終端の |xtk| ≤ 0.3 NM
- [x] タワー移管: 受理 ⇔ ライブが 0.5 NM 以内に到達（クロス風の「受理して外す」「拒否だが届く」の両 repro が反転）。孤児化しない
- [x] 無風: 既存 hold 形状・進入テストと `NoWindKinematicsGoldenTest` が合格。意図差分は §7 に記載
- [x] ⑧: ヘリはトラック方向へ進む。戦術機動は風対応ベクトルを保つ
- [x] 組み合わせ: Hold 割当 → 風設定 → ティック；風変更を OUTBOUND 中に行ってもレーストラック維持；DEPART/STAR 機の Hold＋風
- [x] API 契約不変（`ApiContractTest`）
- [x] Backend README（必要なら root）を同じ変更で更新。未実装の主張を書かない
- [x] Gates: Backend spotless + test、Frontend format/typecheck/lint/tailwind/Jest/build、`scripts/verify-quickstart.sh --readme-only` と `--all`、CI 4/4 on `da79459`
- [x] 検証レポートの HoldTrace / ReviewRepro（N1, ②, ③関連, ④, ⑧）を恒久回帰テストへ反転して配置

---

## 影響範囲

- **Backend**: `Guidance`、`KinematicStep`、`AircraftBase`（Hold／WCA／移管予測）、`HelicopterFlightBehavior`、`FighterJet`、関連テスト、`Backend/README.md`
- **spec**: 本ファイル、[tower-handoff](../20261017-tower-handoff/spec.md) の「風は含まない」を改訂、[spec/spec.md](../spec.md)／ギャップ台帳の参照更新
- **Frontend / API**: 契約変更なし（README に挙動追記がある場合のみ）

---

## 実装計画

### Phase 1（Must-have）

1. spec を置き、repro を `reviewrepro` パッケージへコピーして現状（バグ）を赤／緑確認できる状態にする。
2. `Guidance` と `KinematicStep` を追加し、移管予測を共有ステップへ切替。
3. Hold 誘導・フェーズ捕捉・フィックス通過・強制旋回の 180° 超えを修正。N1/②/③/④ の assertion を反転。
4. ヘリ／戦術機動を風対応へ。組み合わせ回帰を追加。
5. README・tower-handoff spec・gates・CI。

---

## 検証

- [x] 上記 Success Criteria のパラメータ行列テスト
- [x] 移管同値グリッド（距離 2–10 NM、横ずれ ±1.5 NM、針路 ±15°、風 0–30 kt・8 方位の代表点）
- [x] 無風ゴールデンと既存 Hold シミュレーション
- [x] `./gradlew spotlessCheck test`、Frontend 静的チェック、`verify-quickstart.sh`

---

## 未解決事項（Unresolved Questions）

- XTK ゲイン k（°/NM）の最終値は実装テストで 0.3 NM 収束を満たすよう選ぶ（初期案 25°/NM、上限 30°）。
- PR-C で LateralMode に吸収するまでのあいだ、Guidance は毎ティック再計算の値オブジェクトとする。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20261122-uniform-wind/spec.md](../20261122-uniform-wind/spec.md)
- [spec/20261017-tower-handoff/spec.md](../20261017-tower-handoff/spec.md)
- [Backend/README.md](../../Backend/README.md)
