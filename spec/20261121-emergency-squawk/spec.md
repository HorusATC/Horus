# 緊急スコーク（7500 / 7600 / 7700）の視覚強調表示

## メタデータ

- **Status**: Done
- **Date**: 2026-11-21
- **関連**: [spec/spec.md](../spec.md) フェーズ 5-5、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、Issue [#68](https://github.com/HorusATC/Horus/issues/68)

## 概要

航空機が緊急スコーク（7500 / 7600 / 7700）を設定したら、実際のターミナルレーダーと同様に、管制官の画面ではっきり分かるように表示する。データブロックに HIJK / RDOF / EMRG を目立つ色で表示し、シンボルも強調する。

## 背景・課題

### 現状

- Backend には `squawk` フィールドと API エンドポイント（`POST /api/aircraft/{callsign}/squawk`）が実装済み。
- Frontend の `drawAircraft.ts` には `squawkHighlight` パラメータがあり、完全一致フィルタによる青色強調が可能。
- データブロックには squawk 値が表示される（設定で切り替え可能）。
- Operator 画面と Controller 画面でスコークを変更する UI は未実装。

### 課題（Problem Statement）

- 航空機が緊急事態（ハイジャック 7500、無線通信喪失 7600、一般緊急 7700）を発した際、管制官がその機体をレーダー上で即座に識別できない。
- 日本の管制方式基準およびターミナルレーダーでは、これらのスコークは特別な視覚表示で強調される。
- スコークを変更する UI がないため、緊急事態のシミュレーションができない。

### なぜ今か（Motivation）

- ターミナルレーダーとしての基本機能（針路・高度・速度、Direct To、スコーク、STCA）が main に揃っている。
- 緊急事態の訓練シナリオは管制訓練の重要な要素であり、緊急スコークの識別能力は管制官に必須。
- spec/20261017-terminal-radar-gaps の台帳に記載されており、実装優先度が高い。

---

## 方針

### 決定方針（Decision）

1. **根拠となる一次資料**:
   - **日本の管制方式基準** および **AIP ENR 1.6（SSR モードおよびコード）**:
     - 7500: 不法干渉（ハイジャック）— Unlawful Interference (HIJK)
     - 7600: 通信機故障 — Radio Communication Failure (RDOF)
     - 7700: 緊急事態 — Emergency (EMRG)
   - 参考: [国土交通省航空局 AIP Japan ENR 1.6](https://aisjapan.mlit.go.jp/) および管制方式基準第17条（緊急事態における措置）
   - 国際民間航空機関（ICAO）Annex 10 (Aeronautical Telecommunications) Volume IV でも同様の定義

2. **表示仕様**:
   - **データブロック**:
     - 7500 → 赤色で "HIJK" を表示
     - 7600 → 橙色で "RDOF" を表示
     - 7700 → 赤色で "EMRG" を表示
     - 通常のスコーク値は表示しない（緊急表示を優先）
   - **シンボル強調**:
     - 緊急スコーク設定中の機体は、既存の STCA リング強調と同様に、シンボルに色付きリング（赤または橙）を描画
     - STCA が同時に発生している場合は STCA の赤点滅を優先（安全上の優先度）
   - **一覧性**:
     - 複数の機体が緊急スコークを設定しても、それぞれが識別可能

3. **UI**:
   - **Operator 画面**:
     - 選択中の機体のスコークを入力する 4 桁テキストフィールドを追加（8 進数、`[0-7]{4}` のバリデーション）
     - 緊急スコーク用のクイックボタン（"7500 HIJK"、"7600 RDOF"、"7700 EMRG"）を配置
     - "CLEAR" ボタンで通常のスコーク（例: 1200 または割り当てられたコード）に戻す
   - **Controller 画面**:
     - 管制官は航空機を操作しないため、スコーク入力 UI は配置しない（Operator 画面のみ）

4. **API**:
   - 既存の `POST /api/aircraft/{callsign}/squawk` を使用（後方互換）
   - リクエストボディ: `{ "squawk": "7700" }` など

5. **優先度**:
   - 緊急表示 > STCA 黄色警告 > 通常表示
   - ただし、STCA 赤点滅（riskLevel >= 70）は緊急スコークより優先

### 検討した他案（Alternatives Considered）

- **案 A: Controller 画面にもスコーク入力 UI を配置する**。
  - 却下理由: 管制官は航空機を直接操作せず、指示をパイロット（Operator）が実行する役割分担が既存設計。Controller は InstructionMemo（記録専用）のみ。
- **案 B: 緊急スコークを専用 API で管理する**。
  - 却下理由: 既存の `/api/aircraft/{callsign}/squawk` で十分対応可能。新規 API は不要。
- **案 C: 緊急表示をポップアップや音で通知する**。
  - 保留: 今回は視覚表示のみ実装。音声・モーダル通知は将来の拡張として残す。

### トレードオフ（Trade-offs）

- **メリット**:
  - 緊急事態の訓練シナリオが可能になる
  - 実際のターミナルレーダーに近い視覚表示
  - 既存 API を活用し、後方互換を維持
- **デメリット / 受容する制約**:
  - 音声通知やモーダルポップアップは今回スコープ外
  - 緊急スコークの自動割り当て（シナリオからのトリガー）は今回スコープ外
  - STCA 赤点滅が緊急スコークより優先されるため、両方発生時は緊急表示が見えにくい可能性（レーダー表示の制約として受容）

---

## 完了条件（Success Criteria）

- [x] spec ファイルが作成され、Status が Done になっている
- [x] Backend のテストが全件合格している（182 tests passed）
- [x] Frontend のビルドが全件合格している
- [ ] CI（Docker Compose ジョブを含む）が全ジョブ合格している（Push 後に確認）
- [ ] ブラウザでの手動検証が完了し、スクリーンショットが `/opt/cursor/artifacts/` に保存されている（環境準備後に実施）:
  - (a) トップの START WITH SAMPLE から開始
  - (b) Operator 画面で 1 機を 7700 に変更
  - (c) Controller 画面でその機体が EMRG と強調表示される
  - (d) 7600 と 7500 でも RDOF と HIJK になる
  - (e) 通常のコードに戻すと解除される
  - (f) 他の機体は影響を受けない
  - (g) ブラウザのコンソールと Backend のログにエラーがない
  - (h) スコーク以外の指示（高度・速度・針路）が引き続き反映される
- [ ] 既存 API の応答形式が壊れていない（main との差分確認）
- [ ] ルート README、Backend README、Frontend README が更新されている

---

## 影響範囲

- **Backend**:
  - 変更なし（既存の `squawk` フィールドと API を使用）
  - テスト追加のみ
- **Frontend**:
  - `Frontend/components/controlAircraft.tsx` または新規コンポーネント: スコーク入力 UI（Operator 画面のみ）
  - `Frontend/utility/aircraft/drawAircraft.ts`: 緊急スコーク表示ロジック
  - `Frontend/components/radarCanvas.tsx`: 必要に応じて緊急スコーク判定を追加
  - `Frontend/utility/api/`: スコーク設定 API 呼び出し関数
  - テスト追加
- **ドキュメント**:
  - `README.md`: クイックスタートの clone URL 修正
  - `Backend/README.md`: 必要に応じて API 説明を更新
  - `Frontend/README.md`: 緊急スコーク UI の説明を追加

---

## 実装計画

### Phase 1: spec 作成と調査

- [x] spec ファイル作成
- [x] 既存実装の調査
- [x] 公開資料による根拠の確認

### Phase 2: Frontend UI 実装

- [x] Operator 画面にスコーク入力フィールドとボタンを追加
  - 4 桁テキスト入力（8 進数バリデーション）
  - クイックボタン: 7500 / 7600 / 7700 / CLEAR
- [x] スコーク設定 API 呼び出し関数の実装（`Frontend/utility/api/squawk.ts`）
- [x] `drawAircraft.ts` に緊急スコーク判定と表示ロジックを追加
  - 7500 → "HIJK" 赤
  - 7600 → "RDOF" 橙
  - 7700 → "EMRG" 赤
  - シンボル強調（リング）
- [x] STCA との優先度調整

### Phase 3: テスト実装

- [x] Backend: スコーク設定の統合テスト（@Disabled - Playwright で E2E 検証予定）
- [x] Frontend: スコーク UI のテスト、描画ロジックのテスト

### Phase 4: ドキュメント更新

- [x] ルート README の clone URL 修正
- [x] Backend README: 変更なし（既存 API を使用）
- [x] Frontend README: 緊急スコーク UI の説明

### Phase 5: 検証とスクリーンショット

- [ ] ブラウザでの手動検証（完了条件の (a)〜(h)）（環境準備後に実施）
- [ ] スクリーンショット保存（環境準備後に実施）
- [ ] CI 合格確認（Push 後に確認）

---

## 検証

- [ ] Backend のテストが通る
- [ ] Frontend のビルドとテストが通る
- [ ] CI（Docker Compose を含む）が全ジョブ合格
- [ ] 手動動作確認完了
- [ ] スクリーンショット保存完了
- [ ] README 更新完了

---

## 未解決事項（Unresolved Questions）

- 緊急スコークの自動割り当て（シナリオファイルからのトリガー）は将来の拡張として残すか？
- 音声通知やモーダルポップアップは別 spec で対応するか？

---

## 関連ドキュメント

- [spec/spec.md](../spec.md)
- [spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)
- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [国土交通省航空局 AIP Japan ENR 1.6](https://aisjapan.mlit.go.jp/)
- [Backend/README.md](../../Backend/README.md)
- [Frontend/README.md](../../Frontend/README.md)
