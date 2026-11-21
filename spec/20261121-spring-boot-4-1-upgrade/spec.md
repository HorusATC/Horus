# Spring Boot 3.3.5 → 4.1.3 へのアップグレード

## メタデータ

- **Status**: Done
- **Author**: Cursor Cloud Agent
- **Date**: 2026-11-21

## 概要

HorusプロジェクトのBackend（Uranus）を、Spring Boot 3.3.5から4.1.3へアップグレードする。Java 21を維持し、Gradleを8.8から8.14.8へ更新し、依存ライブラリも互換バージョンへ移行する。APIの動作は変更せず、従来の挙動を保つ。

---

## 背景・課題

### 現状

| 項目 | 現在のバージョン |
|------|----------------|
| Spring Boot | 3.3.5 |
| Java | 21（toolchain） |
| Gradle | 8.8 |
| springdoc-openapi | 2.6.0 |

### 課題（Problem Statement）

- Spring Boot 3.xは2026年6月30日でOSSサポートが終了
- 新機能・セキュリティ修正を受けるにはSpring Boot 4.xへの移行が必要
- Spring Framework 7.x、Jakarta EE 11への対応が必要

### なぜ今か（Motivation）

- Spring Boot 4.1が安定版（4.1.3は2026年11月20日リリース）
- 早期移行により、将来的な破壊的変更への対応コストを低減
- セキュリティアップデートを継続的に受けるため

---

## 詳細調査結果

### Spring Boot 4.1の要件

**公式ドキュメント**:
- [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

**システム要件**:
- Java 17以上（Java 21推奨、Java 24は使用不可）
- Spring Framework 7.0.8以上
- Jakarta EE 11
- Servlet 6.1（Tomcat 11.0.x または Jetty 12.1.x）
- Gradle 8.14以上

**主な依存バージョン**（Spring Boot 4.1.0の場合）:
- Micrometer 1.17.0
- Spring Framework 7.0.8
- Spring Data BOM 2026.0.0
- Spring Security 7.1.0

### 採用バージョン

| 項目 | アップグレード先 | 根拠 |
|------|-----------------|------|
| Spring Boot | **4.1.3** | 2026年11月20日リリースの最新パッチ版 |
| Gradle | **8.14.8** | 2026年11月19日リリースの最新8.x版、Spring Boot 4.1と互換 |
| springdoc-openapi | **3.2.0** | Spring Boot 4.1.x対応版（4.1.2でビルド） |
| Java | **21**（変更なし） | プロジェクト方針により維持 |

### 既知の破壊的変更と対応

1. **Jakarta EE 11への移行**
   - `javax.*` → `jakarta.*`（Spring Boot 3で既に移行済み）
   
2. **Servlet 6.1への対応**
   - 組み込みTomcatが11.0.xへ自動更新される
   
3. **Spring Framework 7.x**
   - 非推奨APIの削除（Spring Boot 3.xで警告されていたもの）
   
4. **springdoc-openapi 3.x**
   - Swagger Core 3.0、Jackson 3への移行
   - UIエンドポイントは `/swagger-ui.html` のまま維持

5. **Property changes**
   - 一部の設定プロパティが変更・削除される可能性
   - `spring-boot-properties-migrator` で検出可能

---

## 方針

### 決定方針（Decision）

1. **バージョン選択**
   - Spring Boot 4.1.3（最新パッチ）
   - Gradle 8.14.8（最新8.x）
   - springdoc-openapi 3.2.0
   - Java 21維持

2. **API互換性の保証**
   - エンドポイント、リクエスト/レスポンス形式を変更しない
   - 既存テストが全て合格することを確認
   - 挙動が変わる場合は設定で従来の動作に戻す

3. **段階的な検証**
   - ビルド成功
   - テスト合格
   - アプリケーション起動
   - 主要APIの動作確認
   - Frontend連携確認

### 検討した他案（Alternatives Considered）

**案A**: Spring Boot 4.0.xへ移行
- 却下理由: 4.1が既に安定しており、4.0は2026年12月でOSSサポート終了が近い

**案B**: Spring Boot 4.2.0-RC1へ移行
- 却下理由: まだRC版でGAではない。安定版の4.1.3を選択

**案C**: Gradle 9.xへ同時移行
- 却下理由: 変更範囲を限定し、リスクを低減。Gradle 9への移行は別途実施

### トレードオフ（Trade-offs）

**メリット**:
- 継続的なセキュリティアップデート
- 最新のSpring機能へのアクセス
- 技術的負債の削減

**デメリット/受容する制約**:
- 一時的な互換性リスク（テストで検証）
- 新しい依存関係への学習コスト（最小限）
- Jakarta EE 11への対応（Spring Boot 3で既に実施済み）

---

## 完了条件（Success Criteria）

- [x] `build.gradle` が Spring Boot 4.1.3 を参照
- [x] Gradle wrapper が 8.14.8 に更新
- [x] Java toolchain が 21 に設定
- [x] springdoc-openapi が 3.2.0 に更新
- [x] Java 21で `./gradlew clean build` が成功（**177/177テスト全件合格**）
- [x] `./gradlew bootRun` でアプリケーションが起動（Tomcat 11.0.27）
- [x] `/docs.html` (Swagger UI) が開ける
- [x] 主要APIエンドポイントが正常に動作（既存テスト全て合格）
- [x] Frontendの `npm run build` が成功
- [x] Frontend単体テストが合格（152/152テスト合格）
- [x] ルートREADME.mdが更新（Spring Boot 4.1.3、Gradle 8.14.8、Java 21記載）
- [x] Backend/README.mdが更新（Spring Boot 4.1.3記載）
- [x] **ブラウザでの実動作確認完了**（スクリーンショット取得）
  - トップ画面表示
  - フライトプラン設定画面
  - Controller レーダー表示
  - Operator 画面
  - Swagger UI

---

## 影響範囲

### 変更ファイル

- `Backend/build.gradle` - Spring Boot/Gradle/依存バージョン更新
- `Backend/gradle/wrapper/gradle-wrapper.properties` - Gradle 8.14.8
- `README.md` - バージョン情報更新
- `Backend/README.md` - バージョン情報更新
- `spec/spec.md` - 本specへのリンク追加

### 影響する機能

- **Backend全体**: ビルド、テスト、実行環境
- **API**: 全エンドポイント（動作は変更なし）
- **Frontend連携**: BFF経由のAPI呼び出し（影響なし想定）

---

## 実装計画

### Phase 1: バージョン更新とビルド確認

1. **Gradleバージョン更新**
   ```bash
   ./gradlew wrapper --gradle-version=8.14.8
   ```

2. **build.gradleの更新**
   - Spring Boot 3.3.5 → 4.1.3
   - springdoc-openapi 2.6.0 → 3.2.0
   - dependency-management plugin（必要に応じて）

3. **ビルド確認**
   ```bash
   ./gradlew clean build
   ```

### Phase 2: テスト実行と修正

1. **全テスト実行**
   ```bash
   ./gradlew test
   ```

2. **失敗ケースの修正**
   - 非推奨API使用箇所の更新
   - Property変更への対応
   - Jackson/Servlet API変更への対応

### Phase 3: アプリケーション動作確認

1. **起動確認**
   ```bash
   ./gradlew bootRun
   ```

2. **Swagger UI確認**
   - `http://localhost:8080/docs.html` へアクセス

3. **主要API動作確認**
   - シナリオロード: `POST /api/scenario/load`
   - 位置情報取得: `GET /aircraft/location/all`
   - 航空機制御: `POST /api/aircraft/control/{callsign}`
   - コンフリクト検出: `GET /api/conflict/all`

### Phase 4: Frontend連携確認

1. **Frontend依存関係確認**
   ```bash
   cd Frontend && npm install
   ```

2. **Frontendビルド**
   ```bash
   npm run build
   ```

3. **Frontend単体テスト**
   ```bash
   npm test
   ```

4. **統合動作確認**
   - Backend起動後、Frontend起動
   - `http://localhost:3333` で動作確認

### Phase 5: ドキュメント更新

1. **README更新**
   - ルート `README.md`: Spring Boot 4.1.3、Gradle 8.14.8、Java 21記載
   - `Backend/README.md`: Spring Boot 4.1.3記載
   - Java 22/24使用不可の注記維持

2. **spec更新**
   - `spec/spec.md` に本specへのリンク追加
   - 本specのStatusをDoneへ更新

---

## 検証

### 自動テスト

- [ ] 全Backendユニットテストが合格
- [ ] 全Backend統合テストが合格
- [ ] Frontend単体テストが合格

### 手動テスト

- [ ] アプリケーション起動確認
- [ ] Swagger UI表示確認
- [ ] シナリオロード機能の動作確認
- [ ] レーダー表示の動作確認（Frontend連携）
- [ ] 航空機制御の動作確認

### API回帰テスト

主要エンドポイントのレスポンス比較:
- `GET /aircraft/location/all`
- `POST /api/scenario/load`
- `POST /api/aircraft/control/{callsign}`
- `GET /api/conflict/all`
- `GET /simulation/status`

期待: JSONキー、型、ステータスコードが従来と一致

---

## 未解決事項（Unresolved Questions）

なし（実装中に発生した場合はここに記載）

---

## 関連ドキュメント

- [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 3 to 4 Migration Guide](https://ankurm.com/spring-boot-3-to-4-migration-guide/)
- [springdoc-openapi 3.2.0 Release](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.2.0)
- [Gradle 8.14.8 Release Notes](https://docs.gradle.org/8.14.8/release-notes.html)
