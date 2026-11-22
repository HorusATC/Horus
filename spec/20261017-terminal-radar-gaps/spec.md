# ターミナルレーダーとして不足している能力

## メタデータ

- **Status**: Accepted
- **Date**: 2026-10-17
- **関連ロードマップ**: [spec/spec.md](../spec.md)

## 概要

Horus を進入・出発のターミナルレーダーとして使うときに、現行実装では再現できない能力を整理する。本 spec は実装そのものではなく、次に子 spec を切る順序の基準である。針路・高度・地速、Direct To、ホールド（入方向・レグ・左右旋回）、スクオーク、5海里／1,000フィートの STCA は既にあるものとして扱う。

## 背景・課題

### 現状

- レーダー表示は航跡、データブロック、履歴ドット、レンジリング、速度ベクトル、フィックスと RNAV／ATS 線、ラベルのドラッグまで持つ。
- 操縦目標は針路・高度・地速。経路操作は Direct To、ホールド（入方向・レグ・左右旋回）、Resume。スクオーク割当とフィルタ強調、管制メモ行がある。
- コンフリクトは水平 5海里・垂直 1,000フィート固定（`RiskAssessment`）。
- フライトプランのウェイポイントには高度制約のデータ型があるが、機体の飛行更新はフィックスへの針路更新が中心で、通過高度・速度制限には従わない。
- ホールド API は入方向・レグ（分/NM）・左右旋回を受け付ける（[20261122-hold-inbound-leg-turns](../20261122-hold-inbound-leg-turns/spec.md)）。標準率レーストラック飛行と指示経路オーバーレイは [20261122-hold-racetrack-overlay](../20261122-hold-racetrack-overlay/spec.md)。進入セクター（DIRECT／TEARDROP／PARALLEL）は [20261122-hold-entry-sectors](../20261122-hold-entry-sectors/spec.md)。一様風のレグ WCA・GS／トラックは [20261122-uniform-wind](../20261122-uniform-wind/spec.md) で実装済み。公示ホールディング（AS PUBLISHED）・EAT／EFC・**待機レグ時間の公式風補正**は未実装。
- 経路末尾の `REMOVE_AIRCRAFT` は機体削除であり、シナリオ終端の削除として残す。タワー移管は別 API として実装済み（[tower-handoff](../20261017-tower-handoff/spec.md)、PR [#112](https://github.com/HorusATC/Horus/pull/112)）。
- セクター境界、シミュレーション速度の可変、セッション記録、複数セクターはロードマップ上の別項目である。ATIS・使用滑走路（6-2）は [20261122-atis-runway-in-use](../20261122-atis-runway-in-use/spec.md) で実装済み（PR [#132](https://github.com/HorusATC/Horus/pull/132)）。一様風・GS／トラック（6-1）は [20261122-uniform-wind](../20261122-uniform-wind/spec.md) で実装済み（シアー・高度別風・待機時間補正は残課題）。

### 課題（Problem Statement）

- 到着機をファイナルへ誘導し、滑走路まで届ける一連の指示が出せない。
- 速度が地速のため、ターミナルで使う指示対気速度（250／210／180／160 など）を出せない。
- 水平間隔が 5海里固定で、レーダーサイト近傍の 3海里や後方乱気流間隔がない。到着順序の訓練に使えない。
- 最低安全高度警報・緊急スクオーク（7500／7600／7700）・滑走路までの距離・時間・使用滑走路と ATIS、一様風による GS／トラック（6-1）は実装済み。残る気象系は高度別風・シアー・待機時間補正等。

### なぜ今か（Motivation）

フェーズ 1〜5 の土台（シナリオ開始、レーダー表示、スクオーク、ホールド、STCA、速度倍率）が main に入っている。次の機能を個別に足す前に、ターミナル管制として何が欠けているかを一枚に固定しないと、子 spec の優先順位が都度ぶれる。

---

## 方針

### 決定方針（Decision）

1. 本ドキュメントはギャップの台帳とする。実装は子 spec に分割し、1 PR にまとめない。
2. 最初の実装スライスは次の四つに限る。この四つが揃うと、到着レーダーの一席が成立する。
   - 指示対気速度による速度管制（地速の直接指定から切り離す）
   - ウェイポイントの高度・速度制限を飛行に適用する
   - 水平間隔の 3海里と、後方乱気流カテゴリに応じた海里
   - 滑走路と最終進入（延長線上への会合まで。進入復行は次段）
3. 既存ロードマップ項目（セクター境界、MSAW、風、ハンドオフ、複数セクター、記録・採点）は重複して新 Issue を切らず、本台帳から参照する。ATIS・使用滑走路（6-2）は実装済み。
4. ホールドの指示要素は [20261122-hold-inbound-leg-turns](../20261122-hold-inbound-leg-turns/spec.md)、標準率レーストラック＋経路描画は [20261122-hold-racetrack-overlay](../20261122-hold-racetrack-overlay/spec.md)、進入セクターは [20261122-hold-entry-sectors](../20261122-hold-entry-sectors/spec.md) で実装済み。公示パターン・EAT／EFC・風は後続。

### 検討した他案（Alternatives Considered）

- **案 A: ギャップを一度に実装する。** ドメイン（性能・間隔・進入）と表示が同時に変わり、レビューと退行の範囲が広すぎるため却下。
- **案 B: 画面（RBL、方位目盛、リーダー線）から入る。** 見た目は近づくが、速度・制限・間隔・ファイナルが無いと訓練シナリオが成立しないため、最初のスライスにはしない。
- **案 C: ロードマップの未着手行だけを優先する。** セクター境界や Mach はターミナル到着のボトルネックではない。本台帳の四つを先にする。

### トレードオフ（Trade-offs）

- **メリット**: 次の子 spec の範囲が「到着の一席」に揃う。既存の 5海里 STCA や地速指示を、置き換えるまで残せる。
- **デメリット / 受容する制約**: 本 PR では飛行も画面も変わらない。対気速度には風か高度換算が要るが、風モデル（フェーズ 6-1）が無い間は無風換算でよい、と子 spec で明示する必要がある。3海里の適用距離はレーダーサイト位置が未定義なので、子 spec で仮の中心を決めるまで数値を固定しない。

---

## 完了条件（Success Criteria）

本 spec の Done は「台帳として合意され、最初の子 spec が切られている」ことである。四機能の実装完了は各子 spec の完了条件とする。

- [x] 不足一覧が、到着・出発、間隔・警報、レーダー画面、訓練運用の四区分で `spec/spec.md` から辿れる
- [x] 最初の実装順が「対気速度、ウェイポイント制限、3海里と後方乱気流、滑走路と最終進入」に固定されている
- [x] 上記四つそれぞれに子 spec のパスが本ファイルからリンクされている

### Phase 1 子 spec

1. [指示対気速度](20261017-indicated-airspeed-instruction/spec.md)
2. [ウェイポイント高度・速度制限](20261017-waypoint-constraints/spec.md)
3. [3海里・後方乱気流間隔](20261017-terminal-separation-wake/spec.md)
4. [滑走路と最終進入](20261017-runway-final-approach/spec.md)

---

## 影響範囲

- **本 PR**: 本 spec と、ロードマップ `spec/spec.md` からの参照のみ
- **後続**: 速度の値オブジェクトと指示 API、フライトプラン制約の適用、`RiskAssessment` の水平基準、空港・滑走路のレーダー描画。各子 spec で範囲を切る

---

## 実装計画

### Phase 0（本 spec）

台帳を置き、実装はしない。

### Phase 1（子 spec ごと）

1. 指示対気速度。地速指示との併存期間と、無風時の換算を子 spec に書く。
2. ウェイポイントの高度・速度制限を、通過判定と操縦目標へ反映する。
3. 3海里を適用する条件と、後方乱気流カテゴリ（ヘビー／ミディアム／ライト）の海里表を子 spec に書く。既存の 5海里／1,000フィートは条件外の既定として残す。
4. 滑走路シンボル、最終進入コース、延長線への会合。進入許可と進入復行は別スライスにする。

### 台帳（Phase 1 の外）

到着・出発の残り。

- 進入復行 — **実装済み**（[go-around](../20261017-go-around/spec.md)、PR [#110](https://github.com/HorusATC/Horus/pull/110)）。タワー移管 — **実装済み**（[tower-handoff](../20261017-tower-handoff/spec.md)、PR [#112](https://github.com/HorusATC/Horus/pull/112)。`REMOVE_AIRCRAFT` はシナリオ終端の削除として残す）。ホールド指示要素 — **実装済み**（[hold-inbound-leg-turns](../20261122-hold-inbound-leg-turns/spec.md)）。標準率レーストラック＋経路オーバーレイ — **実装済み**（[hold-racetrack-overlay](../20261122-hold-racetrack-overlay/spec.md)）。進入セクター — **実装済み**（[hold-entry-sectors](../20261122-hold-entry-sectors/spec.md)）。離陸から初期上昇 — **実装済み**（[initial-climb](../20261122-initial-climb/spec.md)、Issue [#115](https://github.com/HorusATC/Horus/issues/115)）。残り: SID／STAR としての公開手続き（公式 AIP 待ち、[#114](https://github.com/HorusATC/Horus/issues/114)）、ホールドの公示パターン・EAT／EFC、待機レグ時間の公式風補正。

間隔・警報の残り。

- MSAW（ロードマップ 4-4、Done: [20261122-msaw](../20261122-msaw/spec.md)）。7500／7600／7700 の緊急表示は Done（[20261121-emergency-squawk](../20261121-emergency-squawk/spec.md)）。

レーダー画面の残り。

- 測距線と滑走路までの距離・時間 → Done（[20261122-runway-distance-rbl](../20261122-runway-distance-rbl/spec.md)）。ATIS・使用滑走路 → Done（[20261122-atis-runway-in-use](../20261122-atis-runway-in-use/spec.md)）。一様風・GS／トラック → Done（[20261122-uniform-wind](../20261122-uniform-wind/spec.md)。シアー・待機時間補正は残る）。滑走路カタログ拡張（羽田 A/B/C/D・成田 A/B）→ Done（[20261122-runway-catalog](../20261122-runway-catalog/spec.md)）。リーダー線の自動配置 → Done（[20261122-leader-line-auto-placement](../20261122-leader-line-auto-placement/spec.md)）。方位目盛、セクター境界（ロードマップ 2-5、Deferred）は残課題。

訓練運用の残り。

- セッション記録と採点（5-2、5-3）、出発席と進入席の分割、セクター間ハンドオフ（3-4、7-2）。

---

## 検証

- [ ] 本 PR は Markdown のみで、バックエンドテストとフロントエンドビルドの対象変更がない
- [ ] `spec/spec.md` のリンク先ファイルが存在する

---

## 未解決事項（Unresolved Questions）

- 3海里を「レーダーサイトから何海里以内」とするか。サイト座標がシナリオに無い。
- 対気速度の第1版を無風の換算だけにするか、風（6-1）と同時にするか。
- 後方乱気流の海里表を、日本のレーダー間隔に合わせるか、訓練用の簡略表にするか。
- 最終進入の第1版は針路会合までか、グライドパスを含めるか。

---

## 関連ドキュメント

- [spec/spec.md](../spec.md)
- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
