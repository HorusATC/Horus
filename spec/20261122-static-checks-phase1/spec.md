# Static Checks Phase 1

## メタデータ

- **Status**: Done
- **Date**: 2026-11-22

## 概要

機械的にレビューで指摘される問題を CI で fail させ、テストや人手のレビューに依存しない品質ゲートを導入する。API 動作や応答形式は変更しない。Java 22/24 は禁止、Java 21 を維持。force-push や履歴の書き換えは行わない。

## 背景・課題

### 現状（main fcd1e65 時点での PdM 監査結果）

- **Frontend lint カバレッジ不足**: `next lint` は app/ と components/ のみをカバー。全 148 TS ファイルのうち 69 ファイル（utility/ 54、context/ 11）が lint されていない
- **lint-staged 不具合**: `next lint -- --fix` が失敗し、pre-commit フックが機能していない
- **Prettier が CI にない**: 約 11 ファイルが `prettier --check` で失敗
- **Backend URL プレフィックスの混在**: `/aircraft/...` と `/api/...` が混在。PR #119 でテストが誤ったパス（`/api/aircraft/location/all`）、誤った HTTP メソッド、誤ったシナリオ・ロード本体でコールしていた
- **@Disabled テストの放置**: PR #119 で 4 つの `@Disabled` テストが「CI uses Playwright」という架空の理由（repo に Playwright は存在しない）でマージされた
- **Tailwind content の過去の欠落**: 以前 utility/ が content から漏れ、クラスが本番 CSS から無言で削除された
- **Cursor rules と API spec のずれ**: `.cursor/rules`、`UranosAPI.yml` が Java 22、Boot 3.3.2、存在しない `/startGame` に言及。Agent がこれをコピーする
- **main のコンパイル警告**: main ソースは `-Xlint:all` で警告 0。テストは 2 クラスで約 100 警告。`createMultipleAircraft` に `@Valid` 欠如

### 課題（Problem Statement）

- 不完全な静的チェックによって、機械的に検出可能な問題（lint 違反、フォーマット崩れ、型エラー、存在しない API エンドポイントへの呼び出し、無効化されたテストの混入）が PR でレビュー・マージされ、後から発見・修正するコストが発生している
- ドキュメントと実装の乖離（Java version、Spring Boot version、エンドポイント）が Agent による自動生成コードの品質を下げている
- テストコードの警告が放置され、コード品質の基準が曖昧になっている

### なぜ今か（Motivation）

- PR #119 の問題（間違った API パス、無効化テスト）が象徴的な事例として明らかになった
- CI/CD パイプラインが整備され、追加の静的チェックを組み込む基盤が整っている
- ドキュメントと実装の乖離が Agent による開発の障害となっており、早期の是正が開発速度向上につながる

---

## 方針

### 決定方針（Decision）

**Phase 1 として以下を実装する：**

1. **Frontend lint の完全カバレッジ**: eslint 設定で app、components、utility、context およびその他すべての TS ソースディレクトリをカバーし、発見された違反を修正。lint-staged を修正し pre-commit フックを機能させる
2. **Prettier と tsc の CI 追加**: Frontend job に `prettier --check` と `tsc --noEmit` ステップを追加。既存のフォーマット違反ファイルを整形（フォーマット専用コミット、ロジック変更と分離）
3. **厳格な tsconfig フラグ**: 現在エラー 0 のフラグ（noImplicitOverride、noFallthroughCasesInSwitch、noImplicitReturns など）を有効化。各フラグを計測し、0 または些細に修正可能なもののみ有効化。`noUncheckedIndexedAccess` は Phase 2 に延期
4. **契約チェック**: Frontend BFF routes（`Frontend/app/api/**/route.ts` 内の backend fetch）および Backend 統合テスト（MockMvc/RestTemplate パス）で使用されるすべての backend URL+method を抽出し、実際の Spring request mapping と照合するテスト（JUnit または CI スクリプト）。現在の main で pass し、PR #119 の誤りを検出できたことを示す（ネガティブテストまたはローカルで意図的にパスを壊して失敗出力を報告）
5. **無効化テストの CI fail**: Backend テストの `@Disabled`（`@DisabledIfEnvironmentVariable` with reason は許可）、Jest の `it.skip` / `describe.skip` / `.only` を検出して CI fail（例: ArchUnit ルールまたは grep ステップ）
6. **Backend -Xlint:all -Werror**: main ソースで `-Xlint:all -Werror` を有効化。テストは `-Xlint:all` を警告として有効化し、カウントを記録。`createMultipleAircraft` に `@Valid` を追加（valid リクエストの動作を変えない場合のみ。invalid が 400 になる変更は仕様に記載された場合のみ許可。リスクがあれば保留し記録）
7. **Tailwind content ガード**: content 設定が class 文字列を含むすべてのディレクトリをカバーしていることを確認する安価なチェック（スクリプトまたはテスト）を追加。content globs 外の .ts/.tsx ファイルに Tailwind クラス文字列があれば fail。または単純に content を全ソースディレクトリに設定し文書化
8. **ドキュメント・ルール更新**: `.cursor/rules`、`docs/DEVELOPMENT.md`、`AGENTS.md`（存在しなければ作成）を Java 21 / Spring Boot 4.1.3 / Gradle 8.14.x、正しいエンドポイントに更新。「PR チェックリスト」セクションを追加（テストは製品コードをインポートし、コピーしたロジックを持たない。@Disabled/skip なし。ドメイン定数は単一の型付きソース［union types + satisfies + type guards; Java は enums/records］。API 境界で入力を検証。README（root/Backend/Frontend）は同じ変更で更新。push 前に tsc/lint/prettier/jest/gradle を実行）。`UranosAPI.yml` は明らかな誤記を修正するか outdated としてマーク（OpenAPI 生成は Phase 3）
9. **README 更新**: 新しい dev コマンド（lint、format、typecheck）を root / Backend / Frontend README に記載（同じ PR 内）

### 検討した他案（Alternatives Considered）

- **案 A: Husky + lint-staged のみで対応**: 採用しなかった理由: ローカル環境のみに依存し、CI で強制されないため、開発者が hook をスキップした場合に検出できない
- **案 B: 全ての tsconfig strict フラグを一度に有効化**: 採用しなかった理由: `noUncheckedIndexedAccess` などは既存コードで大量のエラーを生む可能性があり、Phase 1 のスコープを超える。段階的な有効化が現実的
- **案 C: OpenAPI schema からクライアントコード自動生成**: 採用しなかった理由: Phase 3 の範囲。Phase 1 ではまず手動で書かれた fetch 呼び出しと実装の乖離を検出する契約テストで対処

### トレードオフ（Trade-offs）

- **メリット**:
  - 機械的に検出可能な問題が CI で早期に fail し、レビュー・修正コストを削減
  - ドキュメントと実装の一貫性が保たれ、Agent 生成コードの品質が向上
  - テストコードの品質基準が明確化され、コードベース全体の品質が向上
  - Backend と Frontend の型安全性が強化される
- **デメリット / 受容する制約**:
  - 初回の静的チェック有効化で既存の違反を修正する作業が発生（計測済み）
  - 契約チェックテストの保守コスト（新規 API 追加時にテストも更新が必要）
  - `-Xlint:all -Werror` によって今後の main ソース変更でコンパイル警告が即座に fail となる（テストは警告のみで許容）

---

## 完了条件（Success Criteria）

**最終 head で以下がすべて満たされること：**

- [x] すべての CI job（Detect、Backend、Frontend［新 prettier/tsc/lint ステップ含む］、Docker Compose）が green
- [x] ローカルで以下がすべて pass:
  - `npx tsc --noEmit`（Frontend）
  - `npm run lint`（全ディレクトリカバー）
  - `npx prettier --check .`（Frontend）
  - `npm run test`（Jest: 31 suites, 168 tests）
  - `npm run build`（Frontend）
  - `./gradlew test`（Java 21、Backend: 184 tests）
- [x] 契約チェックが存在し、誤った API パスを検出することが示されている（ApiContractTest: 29 Frontend BFF paths, 30+ Backend paths extracted and verified）
- [x] API 動作に変更なし（squawk route fixed but API contract unchanged）
- [x] 実測値を報告（Frontend: 31 suites/168 tests including 4 new route handler tests, Backend: 184 tests pass）

---

## 影響範囲

- **Frontend**: eslint 設定、tsconfig、package.json scripts、lint-staged 設定、該当ファイルのフォーマット・lint 修正
- **Backend**: build.gradle（javac options）、バリデーション annotation、契約チェックテスト追加
- **CI**: .github/workflows/ci.yml に prettier / tsc / 無効化テスト検出ステップ追加
- **Docs**: `.cursor/rules/*`、`docs/DEVELOPMENT.md`、`AGENTS.md` 新規作成、root/Backend/Frontend README

---

## 実装計画

### Phase 1: 基盤整備

1. spec 作成（本ドキュメント）
2. ブランチ作成 `cursor/static-checks-phase1-2d3c`
3. Frontend lint 設定の完全カバレッジ化
4. lint-staged 修正
5. Prettier と tsc の CI 追加
6. tsconfig strict フラグの有効化（計測ベース）
7. 契約チェックテスト実装
8. 無効化テスト検出の CI 追加
9. Backend -Xlint:all -Werror 有効化
10. Tailwind content ガード実装
11. ドキュメント・ルール更新
12. README 更新
13. PR 作成、CI 確認
14. 最終報告（PR URL、head SHA、項目ごとのステータス、実測値、延期事項と理由）

---

## 検証

- [ ] CI の全 job が pass
- [ ] ローカルでの全静的チェック pass
- [ ] 契約チェックがネガティブケースを検出
- [ ] API 動作検証（Docker Compose による統合テスト pass）
- [ ] ドキュメント内容の正確性確認

---

## 未解決事項（Unresolved Questions）

なし。すべての項目が完了または判断されました。

## 実装時の決定事項（Decisions Made During Implementation）

- **`createMultipleAircraft` への `@Valid` 追加を保留**: 実装時の検証で、`@Valid` 追加により invalid input が 500 から 400 に変更され、API の動作（error response status code）が変わることが判明。仕様変更を伴うため Phase 1 の scope 外と判断し保留。契約テストは valid input のパス・メソッドの正しさを検証するため、この保留は Phase 1 の成功基準に影響しない
- **`noUncheckedIndexedAccess` など残りの strict フラグ → Phase 2 で対応**: 計画通り
- **OpenAPI ベースのコード生成 → Phase 3 で対応**: 計画通り
- **Route handler tests**: Next.js App Router の route.ts を jest でテストするため、`@jest-environment node` docblock を使用して node environment で実行。31 suites / 168 tests（jsdom 30 suites / 164 tests + node 1 suite / 4 tests）

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [.cursor/rules/project_wide.mdc](../../.cursor/rules/project_wide.mdc)
- [Backend README](../../Backend/README.md)
- [Frontend README](../../Frontend/README.md)
