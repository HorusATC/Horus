# Controller レーダーの目標高度が古いまま残る不具合修正

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: PR #119 既知問題（Controller で古い目標高度が表示される）、main でも再現 / PR #123

## 概要

Operator が高度指示を送ったあと、Controller レーダーのデータブロック（目標高度行）がポーリング結果の `instructedVector.altitude` を反映せず、初回観測時の値のまま残る。パイロット操縦目標の単一情報源をポーリング DTO に揃え、Controller でも毎回同期する最小修正を行う。API 契約・バックエンド挙動は変更しない。

## 背景・課題

### 現状

| 項目 | 状態 |
|------|------|
| **Backend** | `POST /api/aircraft/control/{callsign}` で `instructedVector` を更新。`GET /aircraft/location/all`（BFF: `/api/aircraft/location/all`）は最新の `instructedVector.altitude` を返す |
| **Operator ポーリング** | `pathname === "/operator"` → `Aircraft.updateAircraftInfo`（`instructedVector` を含む） |
| **Controller ポーリング** | それ以外 → `Aircraft.updateAircraftLocationInfo`（**従来は `instructedVector` をコピーしない**） |
| **データブロック 2 行目** | 管制クリアランス高度が無ければ `instructedVector.altitude` vs 実測を表示（`drawAircraft.ts`） |

`drawAircraft.ts` には「パイロット目標の古い値で矢印が狂うのを防ぐ」ため、Controller ではクリアランス高度を優先するコメントがある。これは本不具合の存在を前提にした回避であり、クリアランス未記録時は依然として古い `instructedVector` が表示される。

### 再現手順（Repro）

1. Backend（Java 21）と Frontend を起動する
2. トップの **START WITH SAMPLE** でシナリオを開始し、Controller に遷移する
3. 任意機（例: `ANA404`）のデータブロック 2 行目の目標側 FL を控える（例: 実測と同値なら `190`）
4. Operator 画面で当該機を選択し、Altitude を明確に異なる値（例: `25000`）にして **EXECUTE COMMAND**（または同等の `POST .../control`）
5. Controller に戻り数秒待つ（位置ポーリング間隔）
6. **期待**: データブロックが新目標を反映（例: `250 ↑ 190`）
7. **実際（バグ）**: 目標側が初回のまま（例: 引き続き `190`）。`GET /api/aircraft/location/all` の `instructedVector.altitude` は既に `25000`
8. 2 回目の別高度指示でも Controller ラベルは更新されない。Controller をハードリロードすると航空機が DTO から再生成され、正しい目標が出る

### 課題（Problem Statement）

- Controller のレーダー状態がポーリング DTO のパイロット目標と乖離し、管制官役が誤った目標高度を見る
- 既存の「LocationInfo は instructedVector を触らない」分岐が、表示用の単一情報源を壊している
- PR #119 ブラウザ確認でも観測され、main でも残存している

### なぜ今か（Motivation）

- 既知バグとして明示されており、レーダー表示の信頼性に直結する
- 原因がフロントの状態マージ 1 箇所に限定でき、API 変更なしで直せる

### 根本原因（Root Cause）

`Frontend/utility/api/location.ts` の Controller 経路が `updateAircraftLocationInfo` を呼び、同メソッドが `instructedVector` を代入していなかった。Backend / BFF は正しく更新済み。単位（FL/ft）の取り違えではない（表示は従来どおり ft÷100 の FL）。メモ化や React state の取りこぼしが主因ではなく、**Aircraft インスタンス上のパイロット目標がポーリングで更新されないこと**が主因。

---

## 方針

### 決定方針（Decision）

1. Operator / Controller の二重メソッドを廃止し、`Aircraft.applyPolledFields` に統一する（ラベル位置・軌跡履歴はクライアント所有のまま）
2. サーバ所有フィールドを `PolledAircraftServerFields` + `POLLED_AIRCRAFT_SERVER_FIELD_KEYS satisfies Record<keyof …, true>` で型チェックする。**#123 時点ではこの Pick と `applyPolledFields` の代入の間だけ**を守っており、Aircraft クラスだけ／`AircraftLocationDto` だけにフィールドを足しても `tsc` は通った。クラスと DTO まで閉じた分割は [spec/20261122-static-checks-phase2/spec.md](../20261122-static-checks-phase2/spec.md)（Phase 2A）で行う
3. 回帰テストで poll 後の `instructedVector`（altitude / heading / groundSpeed）更新を固定する
4. API・DTO・Backend ロジックは変更しない

### 検討した他案（Alternatives Considered）

- **案 A**: Controller でも旧 `updateAircraftInfo` を呼ぶだけ。採用しなかった理由: 二重メソッドが残ると再び乖離しうる
- **案 B**: 描画時だけ毎回 DTO を参照する別キャッシュ。採用しなかった理由: 二重情報源が増え、ストリップ／選択状態との不整合が残る
- **案 C**: Backend 変更や新フィールド。却下: 不要。既存 `instructedVector` で足りる

### トレードオフ（Trade-offs）

- **メリット**: stale を防ぎ、#123 時点では `PolledAircraftServerFields` と `applyPolledFields` の間の同期漏れを tsc で検知できる（クラス／DTO 全体の閉じた分割は Phase 2A）
- **デメリット / 受容する制約**: 旧コメント／旧 spec（「Controller で instructedVector を上書きしない」）は本修正で改める。管制クリアランス行の優先表示自体は維持する

---

## 完了条件（Success Criteria）

- [x] 上記再現手順で、2 連続の高度指示のたびに Controller データブロックの目標側が API の `instructedVector.altitude` と一致する
- [x] 回帰テストが `npx jest --listTests` に含まれ、未修正コードでは失敗し、修正後は成功する
- [x] Frontend: `format:check` / `typecheck` / `lint` / `tailwind:check` / `test` / `build` が通る
- [x] Backend: `spotlessCheck` / `test` が通る（本修正は Frontend のみ想定）
- [x] Playwright で before（stale）/ after（更新）スクリーンショットを取得し、コンソールにエラーがない
- [x] API のリクエスト／レスポンス形状を変えていない

---

## 影響範囲

- **Frontend `Aircraft.applyPolledFields`**: サーバ所有フィールドをポーリング結果から同期（`instructedVector` 含む）
- **Frontend テスト**: `aircraftClass.test.ts`、`location.controllerPoll.test.ts`
- **ドキュメント**: 本 spec、`spec/spec.md`、Frontend README
- **Backend / BFF API**: 変更なし

---

## 実装計画

### Phase 1

1. 未修正コードで回帰テストを失敗させて証拠を残す
2. `instructedVector` 同期を含む poll merge を修正
3. `applyPolledFields` に統一し、サーバ所有フィールドを型で網羅チェック
4. 静的チェック・Playwright 検証・PR

---

## 検証

- [x] 未修正での失敗ログを保存
- [x] 修正後の Frontend / Backend チェック
- [x] Playwright before/after
- [x] CI 4/4 green

---

## 未解決事項（Unresolved Questions）

- なし（選択機サイドパネルの `SelectedAircraftContext.instructedVector` は Controller では入力 UI に使われない。必要なら後続でポーリング連動を検討）

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20260326-instruction-memo-radar-label/spec.md](../20260326-instruction-memo-radar-label/spec.md)（パイロット目標 vs 管制クリアランス）
- [Frontend/README.md](../../Frontend/README.md)
- [spec/spec.md](../spec.md)
