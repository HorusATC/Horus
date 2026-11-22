# Horus: Agent Development Guidelines

このドキュメントは Cursor Agent や AI アシスタントが Horus プロジェクトで開発を行う際の指針を提供します。

## プロジェクト概要

- **名称**: Horus - ATCレーダーシミュレーションシステム
- **Backend (Uranus)**: Java 21 + Spring Boot 4.1.3
- **Frontend (Omotenus)**: TypeScript + Next.js 14.2.15
- **Build Tool**: Gradle 8.14.8
- **Architecture**: Clean Architecture, DDD, SOLID principles

## 重要な制約

### Java バージョン

**Java 21 のみを使用してください。Java 22 および Java 24 は互換性の問題があるため禁止されています。**

```bash
# Java バージョン確認
java --version

# 必要に応じて JAVA_HOME を設定
export JAVA_HOME=/path/to/java21
```

### バージョン情報

- **Java**: 21
- **Spring Boot**: 4.1.3
- **Gradle**: 8.14.8
- **Node.js**: 20.x
- **Next.js**: 14.2.15

## 開発前のチェックリスト

### 1. spec の作成

新機能・リファクタリング・設計変更を実施する前に **必ず spec を作成**してください。

```bash
npm run spec:new <slug>
```

詳細は [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) を参照。

### 2. 依存関係のインストール（フックも自動有効化）

```bash
# Backend
cd Backend
./gradlew build

# Frontend（prepare がリポジトリルートの .husky を core.hooksPath に設定する）
cd Frontend
npm ci
# または npm install
```

`Frontend` での `npm ci` / `npm install`、またはルートでの `npm ci` / `npm install` のたびに Git hooks（pre-commit / pre-push）が自動インストールされる。Cloud Agent も `.cursor/environment.json` の `install`（`cd Frontend && npm ci`）で同様に有効化される。**`--no-verify` は使わない。** CI が最終ゲートである。

### 3. 静的チェックの実行

```bash
# Frontend
cd Frontend
npm run typecheck    # TypeScript 型チェック
npm run lint         # ESLint (app, components, utility, context)
npm run format:check # Prettier フォーマットチェック
npm run tailwind:check # Tailwind content カバレッジ
npm test            # Jest テスト
npm run build       # ビルド確認

# Backend
cd Backend
./gradlew spotlessCheck  # コードフォーマット
./gradlew test          # JUnit テスト
```

## PR 作成前のチェックリスト

以下をすべて満たしてから PR を作成してください：

### コード品質

- [ ] **テストは製品コードをインポート**している（ロジックをコピーしていない）
- [ ] **@Disabled / it.skip / describe.skip / .only がない**
- [ ] **ドメイン定数は単一の型付きソース**を使用
  - TypeScript: union types + satisfies + type guards
  - Java: enums または records
- [ ] **API 境界で入力を検証**（`@Valid` など）

### ドキュメント同期

- [ ] **README (root/Backend/Frontend) を同じ変更で更新**
  - API エンドポイント変更
  - 環境変数追加
  - 新機能説明

### 静的チェック実行

```bash
# Frontend (すべて pass すること)
cd Frontend
npm run typecheck
npm run lint
npm run format:check
npm run tailwind:check
npm test
npm run build

# Backend (すべて pass すること)
cd Backend
./gradlew spotlessCheck
./gradlew test

# 契約チェック
./gradlew test --tests ApiContractTest
```

### Git 操作

```bash
# 変更をステージング
git add -A

# 意味のあるコミットメッセージ（pre-commit が lint-staged を実行する）
git commit -m "feat(backend): add new endpoint for X

- Implement Y
- Add tests for Z"

# Push（pre-push が変更範囲に応じて tsc / format:check / spotlessCheck を実行する）
git push -u origin <branch-name>
```

**禁止**: `git commit --no-verify` / `git push --no-verify`。フックをスキップせず、失敗したら修正してから再実行する。CI（Prettier / tsc / ESLint / Spotless / test / build）が最終ゲート。

## コーディング規約

### Backend (Java)

1. **Clean Architecture** に従う
   - domain → application → interface → infrastructure
   - 依存関係は内側に向かう
2. **DDD 原則** を適用
   - Entity, Value Object, Repository の区別を明確に
3. **SOLID 原則** を遵守
4. **Spotless** による自動フォーマット
   - `./gradlew spotlessApply` で自動整形
5. **main sources は警告ゼロ** (`-Xlint:all -Werror`)
6. **Validation** を API 境界で実施 (`@Valid`)

### Frontend (TypeScript)

1. **厳格な型チェック** (tsconfig strict flags)
   - noImplicitReturns, noUnusedLocals, etc.
2. **ESLint** ですべてのソースをカバー
3. **Prettier** でフォーマット統一
4. **Tailwind CSS** の ATC テーマを使用
5. **クラス文字列は content globs 内**に配置

## API 契約

### Backend → Frontend 契約

Frontend BFF routes (`Frontend/app/api/**/route.ts`) が呼び出すすべての backend エンドポイントは、Spring の request mapping に存在する必要があります。

契約違反は `ApiContractTest` で検出されます。

### 正しいエンドポイント

- `/aircraft/location/all` (NOT `/api/aircraft/location/all`)
- `/api/scenario/load`
- `/simulation/start`

詳細は `Backend/src/test/java/.../ApiContractTest.java` を参照。

## よくあるミス

### ❌ 避けるべきパターン

1. **BFF で直接 fetch を使用**
   ```typescript
   // ❌ 間違い: BACKEND_BASE_URL を使った直接 fetch
   const url = `${BACKEND_BASE_URL}/api/aircraft/${callsign}/squawk`;
   fetch(url, { method: "POST", ... })
   
   // ❌ 間違い: 変数に代入したパス
   const path = `/api/aircraft/${callsign}/squawk`;
   proxyToBackend(path, ...)
   
   // ✅ 正しい: proxyToBackend にパスを直接渡す
   proxyToBackend(`/api/aircraft/${callsign}/squawk`, { method: "POST", ... })
   ```

2. **テストの無効化**
   ```java
   // ❌ 理由なき無効化
   @Disabled("Will fix later")
   
   // ✅ 環境変数での条件付き無効化のみ許可
   @DisabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "...")
   ```

3. **型の any**
   ```typescript
   // ❌ 型を諦めない
   function process(data: any) { }
   
   // ✅ 具体的な型を定義
   function process(data: AircraftLocation) { }
   ```

4. **マジックナンバー**
   ```typescript
   // ❌ ハードコードされた定数
   if (altitude > 1000) { }
   
   // ✅ 名前付き定数
   const MIN_ALTITUDE_FT = 1000;
   if (altitude > MIN_ALTITUDE_FT) { }
   ```

## トラブルシューティング

### Backend ビルド失敗

```bash
# Java バージョン確認
java --version

# Clean build
./gradlew clean build --no-daemon

# Spotless 適用
./gradlew spotlessApply
```

### Frontend ビルド失敗

```bash
# 依存関係再インストール
rm -rf node_modules package-lock.json
npm install

# キャッシュクリア
npm run dev:clean
```

### テスト失敗

```bash
# Backend: 単一テスト実行
./gradlew test --tests <TestClassName>

# Frontend: 単一テスト実行
npm test -- <test-file-name>
```

## 参考ドキュメント

- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) - 開発方針・spec 作成
- [README.md](README.md) - プロジェクト概要
- [Backend/README.md](Backend/README.md) - Backend 詳細
- [Frontend/README.md](Frontend/README.md) - Frontend 詳細
- [spec/spec.md](spec/spec.md) - 実装計画一覧

## ライセンス

MIT License - 研究・教育目的で自由に利用可能
