# pre-commit / pre-push フックの自動有効化

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22

## 概要

リポジトリを clone した直後から、および Cursor Cloud Agent の新規 VM を含むすべての開発環境で、lint / format / type の Git フックが自動的に有効になるようにする。CI は最終ゲートとして維持し、ローカル・エージェントが `--no-verify` なしで同一の静的チェックを早期に実行できるようにする。

## 背景・課題

### 現状

- ルート `package.json` に husky / lint-staged と `prepare` が存在するが、Frontend の lint-staged コマンド（`npx prettier --write`、`npx next lint --fix --file`）は staged ファイルを正しく受け取れない。
- フックは環境によってインストールされず、Cloud Agent は `core.hooksPath` 未設定のまま push し、CI の Prettier / Spotless で繰り返し失敗している。
- `.cursor/environment.json` がなく、Build の `install` でフック有効化が保証されていない。

### 課題（Problem Statement）

- staged ファイルへの Prettier / ESLint 適用が壊れており、フォーマット違反が commit をすり抜ける
- 新規環境・Cloud Agent でフックが自動有効にならない
- エージェントが CI で初めて静的チェック失敗に気づき、手戻りコストが高い

### なぜ今か（Motivation）

静的チェック（Prettier / tsc / ESLint / Spotless）は CI に入った。ローカル・エージェント側のゲートが壊れたままだと、同じ失敗が繰り返し CI に流れ、レビューと CI 枠を浪費する。フックを「入れれば動く」状態から「入れなくても入る」状態へ変える必要がある。

---

## 方針

### 決定方針（Decision）

1. **husky + lint-staged** をリポジトリルートで管理し、`prepare`（ルートおよび Frontend）で `core.hooksPath=.husky` をリポジトリルート基準に設定する
2. **pre-commit**: staged のみ。Frontend は Prettier + ESLint `--fix`（ファイル引数を正しく渡す）。Backend は Spotless（適用をステージ範囲で実行）
3. **pre-push**: 変更があった側のみ。Frontend は `tsc --noEmit` と `format:check`。Backend は `spotlessCheck`
4. **`.cursor/environment.json`**: `install` で `cd Frontend && npm ci` を実行し、`prepare` 経由でフックを有効化
5. **AGENTS.md / README**: フックは自動インストール、`--no-verify` 禁止、CI が最終ゲートであることを明記
6. 製品挙動は変更しない。Java 21 / Spring Boot 4.1.3 / Gradle 8.14.8 を維持

### 検討した他案（Alternatives Considered）

- **案 A（committed `.githooks` + `git config` のみ）**: husky なしで hooksPath を切替。採用しなかった理由: 既存の husky / lint-staged 資産があり、`prepare` との親和性で husky の方が運用が単純
- **案 B（CI のみで静的チェック）**: ローカルゲートを捨てる。採用しなかった理由: 失敗のフィードバックが遅く、エージェントの手戻りが大きい
- **案 C（フルテストを pre-commit で実行）**: 採用しなかった理由: 速度要件に反する。テストは CI と明示的なローカル実行に任せる

### トレードオフ（Trade-offs）

- **メリット**:
  - clone / `npm ci` だけでフックが有効
  - staged 単位の高速フィードバック
  - CI 失敗（Prettier / Spotless / tsc）の事前検知
- **デメリット / 受容する制約**:
  - pre-push で Frontend 変更時に tsc / format:check が走る（数十秒程度）
  - Spotless は Gradle 起動コストがある（`--no-daemon` でも初回は遅い）
  - フックを無効化する逃げ道（`--no-verify`）は文書上禁止し、CI で最終検知する

---

## 完了条件（Success Criteria）

- [ ] `spec/20261122-precommit-hooks/spec.md` が本形式で存在し Status は In Progress（Done は PdM レビュー後）
- [ ] ルートまたは Frontend の `prepare` により `npm ci` / `npm install` 後に `git config core.hooksPath` がリポジトリの `.husky` を指す
- [ ] pre-commit が staged Frontend `*.{ts,tsx,js,jsx,json,css,md}` に Prettier、`*.{ts,tsx}` に ESLint `--fix` をファイル引数付きで実行する
- [ ] pre-commit が staged Backend `*.java` に Spotless を適用（または違反をブロック）する
- [ ] pre-push が Frontend 変更時に `tsc --noEmit` と `format:check`、Backend 変更時に `spotlessCheck` を実行する
- [ ] `.cursor/environment.json` の `install` が `cd Frontend && npm ci` である（Cursor 公式スキーマのフィールドのみ）
- [ ] AGENTS.md にフック自動インストール・`--no-verify` 禁止・CI 最終ゲートを記載
- [ ] ルート / Frontend / Backend README の開発セットアップ記述が事実と一致
- [ ] 証明: 新規 clone → `cd Frontend && npm ci` でフック有効、誤フォーマット `.ts`・未使用 Java import・tsc エラーでそれぞれブロックまたは自動修正
- [ ] 製品挙動変更なし。push 前ゲート（Frontend 全静的チェック + test + build、Backend spotlessCheck + test）が通る

---

## 影響範囲

- **ルート**: `package.json`（lint-staged）、`lint-staged.config.*`、`.husky/pre-commit`、`.husky/pre-push`、`scripts/install-git-hooks.sh`、`.cursor/environment.json`、`AGENTS.md`、`README.md`
- **Frontend**: `package.json` の `prepare`、README のセットアップ記述
- **Backend**: README のフック記述のみ（製品コード変更なし）
- **CI**: 変更なし（最終ゲートとして維持）

---

## 実装計画

### Phase 1: 仕様とフック基盤

- [ ] 本 spec を作成（Status: In Progress）
- [ ] `scripts/install-git-hooks.sh` でリポジトリルートの `.husky` を `core.hooksPath` に設定
- [ ] ルート / Frontend の `prepare` から同スクリプト（または husky）を呼ぶ

### Phase 2: pre-commit / pre-push

- [ ] lint-staged 設定を関数化し、staged ファイルを Prettier / ESLint / Spotless に正しく渡す
- [ ] `.husky/pre-commit` → lint-staged
- [ ] `.husky/pre-push` → 変更検知付きで tsc / format:check / spotlessCheck

### Phase 3: 環境・ドキュメント

- [ ] `.cursor/environment.json` に `install: "cd Frontend && npm ci"`
- [ ] AGENTS.md / README 群を更新

### Phase 4: 証明と PR

- [ ] 新規 clone でのフック有効化・誤フォーマット・未使用 import・tsc エラーの証明ログを取得
- [ ] ゲートコマンド実行後に PR を main 向けに作成（spec は Done にしない）

---

## 検証

- [ ] 新規 clone で `cd Frontend && npm ci` 後、`git config core.hooksPath` が `.husky`
- [ ] 誤フォーマットの `.ts` を commit → フックが整形またはブロック
- [ ] 未使用 import の `.java` を commit → フックが修正またはブロック
- [ ] tsc エラーを含む変更の push → pre-push がブロック
- [ ] Frontend: `npm run format:check && npx tsc --noEmit && npm run lint && npm run tailwind:check && npm test && npm run build`
- [ ] Backend: `./gradlew spotlessCheck test`
- [ ] CI 全ジョブ成功

---

## 未解決事項（Unresolved Questions）

- Cloud Agent ランタイムが `core.hooksPath` をプラットフォーム用パスへ上書きする場合の共存方法（install 後の再設定が必要か）

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [AGENTS.md](../../AGENTS.md)
- [README.md](../../README.md)
- [Frontend/README.md](../../Frontend/README.md)
- [Backend/README.md](../../Backend/README.md)
- [Cursor Cloud Environment Setup](https://cursor.com/docs/cloud-agent/setup)
