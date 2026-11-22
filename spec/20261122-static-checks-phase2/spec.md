# Static Checks Phase 2

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/20261122-static-checks-phase1/spec.md](../spec/20261122-static-checks-phase1/spec.md)、[spec/20261122-precommit-hooks/spec.md](../spec/20261122-precommit-hooks/spec.md)、[spec/20261122-controller-stale-target-altitude/spec.md](../spec/20261122-controller-stale-target-altitude/spec.md)、[docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)、PR #120 / #121 / #123

## 概要

Phase 1 で導入した静的検査を一段厳しくする。Frontend で `noUncheckedIndexedAccess` を有効化し、型情報付き ESLint を「1 PR で直し切れる範囲」で入れる。Backend の `ApiContractTest` は重複キーを正規化して潰し、Phase 1 仕様の「30+ Backend paths」という誤記を直す。あわせて、PR #123 の `applyPolledFields` が残した穴（Aircraft クラスだけ、または DTO だけにフィールドを足しても `tsc` が通る）を型の分割で塞ぎ、#123 仕様の言い過ぎを訂正する。API の動作と応答形式は変更しない。

## 背景・課題

### 現状（main `b295454`、#124 マージ直後。計測は 2026-11-22 14:47–14:50 JST (Asia/Tokyo)）

- **Phase 1（#120）は完了済み。** Frontend は `strict: true` に加え `noImplicitOverride` / `noFallthroughCasesInSwitch` / `noImplicitReturns` / `noUnusedLocals` / `noUnusedParameters` が有効。`npx tsc --noEmit` は 0 件。`noUncheckedIndexedAccess` は Phase 2 へ延期されたまま。
- **`noUncheckedIndexedAccess` をフラグだけで足すと、エラーは 63 件・16 ファイル。** 内訳は TS2532 が 34、TS2345 が 11、TS18048 が 10、TS2322 が 5、TS2339 が 3。多いファイルは `RoutePreviewMap.tsx` 12、`radarCanvas.tsx` 12、`routePreviewGeometry.ts` 11、`flight-plan-setup/page.tsx` 6。現行 `npx tsc --noEmit`（フラグなし）は 0 件。
- **型情報付き ESLint は未導入。** 現行 `.eslintrc.json` は `next/core-web-vitals` + `next/typescript` と `no-explicit-any` のみ。`npm run lint`（`app/` `components/` `utility/` `context/` `types/`）は 0 件。`@typescript-eslint/eslint-plugin` 8.10.0 で `recommended-type-checked-only` を載せ、さらに `switch-exhaustiveness-check` を error にした計測（現行 lint 対象ディレクトリ、`--no-eslintrc` のため既存 `eslint-disable react-hooks/exhaustive-deps` が 3 件ノイズになるが、集計からは除外）:
  - 実違反 **55 件**（本番 25 / テスト 30）
  - `unbound-method` 19（すべて `routeRenderer.test.ts`）
  - `no-misused-promises` 17（すべて本番。`onClick` 等へ async を直接渡している）
  - `require-await` 7（すべてテスト。主に squawk `route.test.ts` のモック）
  - `no-unsafe-assignment` 7（本番 3: `location.ts` / `instructionMemo.tsx` / `msawAlertBanner.tsx` の `response.json()` 系。テスト 4）
  - `no-floating-promises` 3（本番。`flightPlanDisplay.tsx`、`radarCanvas.tsx`）
  - `no-unnecessary-type-assertion` 2（本番）
  - `switch-exhaustiveness-check` **0 件**（無料で有効化できる）
  - 比較: `strict-type-checked-only` は約 222 件（`no-confusing-void-expression` 87、`restrict-template-expressions` 52、`no-unnecessary-condition` 26 が主）。Phase 2 には入れない。
- **`ApiContractTest` の Backend 側キーが重複する。** `extractBackendTestCalls` は `Map.put(method + " " + path, file)` で、テストにベタ書きされたコールサイン（`/api/aircraft/JAL123/squawk` など）を正規化しない。main では生抽出 **41 件**、ユニークなリテラルキー **22 件**（重複キー 5 種、余分な出現 19）。コールサインを `{callsign}` に潰すと **14 件**の一意マッピングになる。Phase 1 仕様の完了条件は「29 Frontend BFF paths, **30+ Backend paths**」と書いており、29 は今も正しいが、30+ は 41 でも 22 でもなく誤記。
- **Frontend BFF 側は 27 個の `route.ts`、exported handler 29、ユニーク呼び出し 29** で、handler 数との一致アサートは既に効いている。
- **PR #123 の型ガードは閉じた輪が狭い。** `PolledAircraftServerFields = Pick<Aircraft, …>`（手書きの 13 キー）と `POLLED_AIRCRAFT_SERVER_FIELD_KEYS satisfies Record<keyof PolledAircraftServerFields, true>`、および `applyPolledFields` 内のオブジェクトリテラル `satisfies` は、**その Pick と代入の間**だけを守る。Aircraft クラスだけにフィールドを足しても、DTO（`AircraftLocationDto`）だけにフィールドを足しても、`tsc` は通る。#123 仕様は「DTO/型にフィールド追加しても取りこぼせない」「同期漏れを tsc で検知できる」と書いており、言い過ぎである。
- **lint-staged のファイル引数は #121 で直済み。** ルート `lint-staged.config.mjs` の `prettierWrite` / `eslintFix` は staged ファイルを引数として `prettier --write` と `eslint --fix` に渡す。Phase 2 のスコープから外す。
- Java 21 / Spring Boot 4.1.3 / Gradle 8.14.x を維持。force-push や履歴の書き換えは行わない。

### 課題（Problem Statement）

- 配列・インデックスアクセスの `undefined` を型が無視するため、実行時に落ちる穴が `tsc` を通る
- Promise の未処理・イベントハンドラへの誤渡し、および `switch` の非網羅が CI で止まらない
- 契約テストが同じエンドポイントをコールサイン違いの別キーとして数え、仕様の件数も間違っている
- 位置ポーリングのサーバ所有フィールドを、Aircraft と DTO の片方にだけ足しても型が検知しない
- #123 仕様の文言が、実際の型の守備範囲より広く読める

### なぜ今か（Motivation）

- Phase 1 が main に入り、残作業として明示的に延期されていた `noUncheckedIndexedAccess` と型付き lint をやる番である
- #123 の既知の型の穴と、Phase 1 仕様の誤記は、次の機能 PR が増やす前に塞いだ方が安い
- 計測済みで、1 PR に全部詰める必要はなく、直せる単位に分割できる

---

## 方針

### 決定方針（Decision）

1. **`noUncheckedIndexedAccess` を `Frontend/tsconfig.json` で有効化する。** 発生した 63 件は、型の絞り込み・早期 return・`for…of`・長さチェック・ユーザー定義型ガードで直す。`as` や非 null 断言（`!`）で黙らせることはしない（`as const` や `import … as` は対象外）。
2. **型情報付き ESLint は `plugin:@typescript-eslint/recommended-type-checked-only` に、`@typescript-eslint/switch-exhaustiveness-check`（error）を足す。** `strict-type-checked` は約 222 件あり、本 Phase では採用しない。テストファイル（`**/*.{test,spec}.ts(x)`）でのみ `unbound-method` を off にする（本番 0 件。19 件はすべて `routeRenderer.test.ts` の Jest メソッド参照で、テストでの実用価値が低い）。残る **36 件**（本番 25 + テストの `require-await` / `no-unsafe-assignment` 11）を同じ変更で直す。lint 対象ディレクトリは現行のまま。
3. **`ApiContractTest` の Backend 抽出を正規化して重複キーを潰す。** テスト URL のコールサイン等を Spring のパス変数（`{callsign}` など）に正規化し、同じマッピングを 1 キーにする。`Map.put` による黙った上書きだけに頼らず、生抽出件数と一意キーを区別してアサートする。main 時点の期待値は生 41 / 正規化後一意 **14**。Frontend 側の 29 は維持。誤ったパスを壊せば今まで通り失敗すること。
4. **Phase 1 仕様の誤記を直す。** `spec/20261122-static-checks-phase1/spec.md` 完了条件の「30+ Backend paths」を、訂正後の実数（Frontend 29 一意、Backend は正規化後 14 一意。必要なら生 41・正規化前リテラル 22 も併記）に書き換える。
5. **Aircraft と `AircraftLocationDto` のフィールドを閉じた分割にする。** Aircraft のすべてのキーを `client`（`label`、`positionHistory`） / `identity`（`callsign`、`type`、`model`、出発・到着空港） / `polled`（現行 `PolledAircraftServerFields` の 13 キー。`geoPosition` と `indicatedAirspeedKnots` は DTO から導出）のいずれかに分類し、`satisfies Record<keyof Aircraft, …>` の双方向で、クラスにだけ足したフィールドは `tsc` エラーにする。DTO のすべてのキーも同様に分類し（`position` は canvas の `position` と `geoPosition` へ、`instructedVector.indicatedAirspeed` は `indicatedAirspeedKnots` へ、など）、DTO にだけ足したフィールドも `tsc` エラーにする。`applyPolledFields` の網羅チェックは `polled` 側に残す。identity は構築時に DTO から入れ、ポーリングでは上書きしない（挙動は変えない）。#123 仕様の該当文は、守っている範囲（`PolledAircraftServerFields` と代入の間だけだったこと、本 Phase でクラス/DTO まで閉じたこと）に訂正する。
6. **lint-staged のファイル引数はスコープ外**（#121 で完了）。
7. **API の動作・応答形式は変更しない。** Backend の本番コードは触らない（契約テストとドキュメントのみ）。Java 21 を維持。

### 検討した他案（Alternatives Considered）

- **案 A: `noUncheckedIndexedAccess` と型付き ESLint と契約テストを 1 PR に全部入れる。** 採用しなかった理由: 63 件のインデックス修正は「キャストで黙らせていないか」のレビューが主眼で、ESLint 36 件や契約テストの正規化と混ぜると差分の意味が薄れる。後述のとおり分割する。
- **案 B: `strict-type-checked` まで一度に有効化する。** 採用しなかった理由: 約 222 件あり、1 PR で直すには多い。`no-confusing-void-expression` やテンプレート文字列の制限は価値もあるが Phase 3 以降とする。
- **案 C: 型付きルールを `no-floating-promises` / `no-misused-promises` / `switch-exhaustiveness-check` の 3 つだけにする。** 一部は妥当だが、`recommended-type-checked-only` の残り（本番の `no-unsafe-assignment` 3 と不要な断言 2、テストの `require-await` 等）も 1 PR に収まる。テストの `unbound-method` だけ例外とする方が、セットの説明が単純。
- **案 D: identity フィールドもポーリングのたびに上書きする。** 採用しなかった理由: 表示の不具合は報告されておらず、API 挙動を変える。本 Phase は分類を型で固定するだけにする。
- **案 E: lint-staged を再修正する。** 採用しなかった理由: #121 でファイル引数は既に渡っている。

### トレードオフ（Trade-offs）

- **メリット**:
  - インデックスの `undefined` と Promise の取りこぼしを CI で止められる
  - `switch` の非網羅が enum / union 追加時にすぐ分かる
  - 契約テストの件数が実際のマッピングと一致し、仕様も正しい数を書く
  - 位置 DTO と Aircraft のドリフトを `tsc` で止められる
- **デメリット / 受容する制約**:
  - `noUncheckedIndexedAccess` で既存コードにガードが増える（実行時動作は変えない）
  - テストでは `unbound-method` を見ない（本番は error のまま）
  - `strict-type-checked` と `exactOptionalPropertyTypes` は本 Phase の対象外
  - OpenAPI からの型生成は Phase 1 どおり Phase 3

---

## 完了条件（Success Criteria）

測定はリポジトリの Frontend / Backend ディレクトリで行う。期待はコマンドの終了コードと、明示した出力である。

- [x] `spec/YYYYMMDD-static-checks-phase2/spec.md` が本形式でリポジトリにあり、実装中は Status が In Progress（Done は PdM レビュー後）
- [x] `Frontend/tsconfig.json` の `compilerOptions.noUncheckedIndexedAccess` が `true`。`cd Frontend && npx tsc --noEmit` の終了コードが 0。差分に、このフラグを黙らせるための `!` や値の型を変える `as` を新たに増やしていない（PR B）
- [x] 上記を入れる前の main（`9da904b` / #125 直後）では、同じコマンドに `--noUncheckedIndexedAccess` を足すとエラー **63 件・16 ファイル**だったことが、PR または本 spec に残っている（PR B で再計測）
- [x] Frontend の ESLint が `recommended-type-checked-only` と `switch-exhaustiveness-check`（error）を含む。テストファイルでのみ `unbound-method` が off。`cd Frontend && npm run lint` の終了コードが 0（PR C）
- [x] 型付きルールの証明: 対象ソースに `Promise.resolve()` を await / void / catch なしで置く（または `switch` から union の 1 値を外す）と `npm run lint` が 0 以外で失敗し、取り除くと再び 0 になる。そのログを残す（PR C）
- [x] `ApiContractTest` が Backend の生抽出と正規化後の一意キーを区別してアサートする。`b295454` 相当のソースでは一意キー 14（生 41）。誤ったパスを 1 箇所書き換えると当該テストが失敗する（PR A / #125）
- [x] `spec/20261122-static-checks-phase1/spec.md` に「30+ Backend paths」が残っておらず、Frontend 29 一意・Backend 正規化後 14 一意（必要なら生件数も）が書いてある（PR A / #125）
- [x] Aircraft の全キーと `AircraftLocationDto` の全キーが、それぞれ閉じた分類（client / identity / polled および DTO 側の対応）に入り、次のプローブで `cd Frontend && npx tsc --noEmit` が 0 以外になる: (a) `Aircraft` にだけフィールドを 1 つ足す (b) `AircraftLocationDto` にだけフィールドを 1 つ足す。足した行を戻すと再び 0。ログを残す（PR A / #125）
- [x] `spec/20261122-controller-stale-target-altitude/spec.md` の「DTO/型にフィールド追加しても取りこぼせない」「同期漏れを tsc で検知できる」に相当する文が、#123 時点では Pick と `applyPolledFields` の間だけを守っていたこと、本 Phase でクラスと DTO まで閉じたことを書くように訂正されている（PR A / #125）
- [x] API の動作と応答形式に変更がない（Backend 本番ソースを変えない。`git diff origin/main -- Backend/src/main` が空）
- [x] ローカルゲートがすべて終了コード 0:
  - Frontend: `npm run format:check && npx tsc --noEmit && npm run lint && npm run tailwind:check && npm test && npm run build`
  - Backend（Java 21）: `./gradlew spotlessCheck test`（PR A で確認。PR B/C は Backend 差分なし）
- [x] CI の 4 ジョブ（Detect、Backend、Frontend、Docker Compose）がその head で success（#125 / #126 済み。本 PR C は CI 待ち→緑で確定）

---

## 影響範囲

- **Frontend `tsconfig.json`**: `noUncheckedIndexedAccess`
- **Frontend の 16 ファイル**: 上記フラグの 63 件を型で修正
- **Frontend `.eslintrc.json`（および必要なら overrides）**: 型情報付きルールとテストの `unbound-method` 例外。対象ディレクトリは現行どおり
- **Frontend の ESLint 修正**: 本番 25 件 + テスト 11 件（`unbound-method` の 19 件はルール例外）
- **Frontend `aircraftClass.ts` / `location.ts`（および関連テスト）**: フィールド分割とプローブで守られる対応表
- **Backend `ApiContractTest.java`**: パス正規化と件数アサート（本番コードは変更しない）
- **Docs**: 本 spec、Phase 1 spec の誤記、#123 spec の文言。README は事実と違う箇所があれば同じ PR で直す
- **CI / lint-staged / フック**: 変更不要（既存の `tsc` / lint / Gradle test がゲートになる）

---

## 実装計画

**1 PR にはしない。** 差分の性質が違う（契約テストと文書、インデックスの型、Promise/unsafe の lint）ため、次の 3 PR に分ける。前の PR が main に入ってから次を始める。

### PR A（最初にやる）

契約テストの正規化、Phase 1 の誤記訂正、Aircraft / DTO の分割、#123 仕様の文言訂正。件数が少なく、機能フラグも ESLint も触らない。#123 の既知の穴を先に閉じ、契約テストの正解件数を仕様に固定する。

### PR A（完了: #125）

契約テストの正規化、Phase 1 の誤記訂正、Aircraft / DTO の分割、#123 仕様の文言訂正。

### PR B（完了: #126）

`noUncheckedIndexedAccess` の有効化と、63 件の型修正。レビューの主眼は「`!` / `as` で黙らせていないか」。あわせて `applyPolledFields` が identity（type / model / origin / destination）を上書きしないことの単体テストを追加（#125 レビューの can-wait）。

### PR C（本 PR）

型情報付き ESLint（上記のセット）と残違反の修正。main `e99c950`（#126 直後）での再計測は、テストの `unbound-method` 例外後 **39 件**（`no-misused-promises` 17、`require-await` 7、`no-unsafe-assignment` 7、`no-floating-promises` 3、`no-unnecessary-type-assertion` 2、`no-duplicate-type-constituents` 3。後者 3 は PR A の partition 交差型から）。

---

## 検証

- [x] PR A: 正規化後 14 キーのアサート、誤パスで契約テストが失敗すること、DTO/Aircraft 片方追加の `tsc` 失敗ログ、Phase 1 / #123 の文言（#125）
- [x] PR B: `npx tsc --noEmit` が 0。差分に新たな `!` / 値の `as` がないこと。有効化前の再計測は 63 件・16 ファイル（main `9da904b`）
- [x] PR C: `npm run lint` が 0。未処理 Promise（または非網羅 `switch`）の失敗ログ
- [x] 各 PR で Frontend / Backend のゲートと CI 4/4（#125 / #126 済み。本 PR C はローカルゲート済み・CI 待ち）
- [x] 各 PR で `git diff origin/main -- Backend/src/main` が空（PR B/C: `git diff origin/main -- Backend` も空）

---

## 未解決事項（Unresolved Questions）

なし。テストの `unbound-method` を off にする範囲、`strict-type-checked` を本 Phase に入れないこと、identity フィールドをポーリングで上書きしないことは、上記 Decision で決めた。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20261122-static-checks-phase1/spec.md](../spec/20261122-static-checks-phase1/spec.md)
- [spec/20261122-precommit-hooks/spec.md](../spec/20261122-precommit-hooks/spec.md)
- [spec/20261122-controller-stale-target-altitude/spec.md](../spec/20261122-controller-stale-target-altitude/spec.md)
- [Frontend/README.md](../../Frontend/README.md)
- [Backend/README.md](../../Backend/README.md)
- [AGENTS.md](../../AGENTS.md)
