# 最小手順でのクイックスタート実装

## メタデータ

- **Status**: Done
- **Date**: 2026-11-21

## 概要

研究者が Horus を clone してから最初のシナリオで機体を管制するまでの手順を最小化する。Docker Compose による一命令での起動と、トップ画面からのサンプルシナリオ起動により、セットアップのハードルを下げ、研究者の体験を向上させる。

## 背景・課題

### 現状

- Backend と Frontend をそれぞれ別々に起動する必要がある
- Java や Node.js の環境構築が必要
- シナリオの読み込みに複数の手順（Swagger または Flight Plan Setup ページ）が必要
- 初めてのユーザーがシミュレーションを開始するまでの手順が多く、ハードルが高い

### 課題（Problem Statement）

- 研究者が clone してからシミュレーションを体験するまでの手順が多すぎる
- 環境構築の複雑さが、研究者の参入障壁となっている
- シナリオ読み込みの手順が分かりにくい

### なぜ今か（Motivation）

Horus はオープンソースの管制シミュレーターとして、研究者コミュニティへの普及を目指している。現状では最初のセットアップと起動の複雑さが参入障壁となっており、これを解決することで多くの研究者に利用してもらえる可能性が高まる。

---

## 方針

### 決定方針（Decision）

1. **Docker Compose による統合起動**: Backend と Frontend を一つの `docker compose up` コマンドで起動できるようにする
2. **トップ画面からの簡易起動**: トップページに「サンプルで始める」ボタンを配置し、到着機が複数含まれる現実的なサンプルシナリオを自動で読み込んでシミュレーションを開始する
3. **シナリオ JSON のアップロード機能**: トップ画面から手元のシナリオ JSON ファイルを読み込める機能を追加する（roadmap 1-5 の実装。小さく収まる場合のみ）

### 検討した他案（Alternatives Considered）

- **案 A（スクリプトによる起動）**: `start.sh` などのシェルスクリプトで Backend と Frontend を起動
  - 採用しなかった理由: プラットフォーム依存性があり、Docker の方が環境の再現性が高い
- **案 B（既存の Flight Plan Setup を改善）**: トップページからのボタンは追加せず、Flight Plan Setup ページのみを改善
  - 採用しなかった理由: 初回ユーザーが最初の一歩を踏み出すハードルを下げるには、トップページからのワンクリック起動が効果的

### トレードオフ（Trade-offs）

- **メリット**: 
  - 環境構築の手間がほぼゼロになる
  - 最初のシミュレーション体験までの時間が大幅に短縮される
  - Docker によりプラットフォーム依存性が低くなる
- **デメリット / 受容する制約**: 
  - Docker のインストールが新たな前提条件となる
  - Docker イメージのビルドに時間がかかる（初回のみ）
  - 開発時の変更が即座に反映されないため、開発者は従来の方法（`./gradlew bootRun` / `npm run dev`）を引き続き使用する必要がある

---

## 完了条件（Success Criteria）

### 変更前の手順数

1. Java 21 のインストールと環境設定
2. Node.js のインストール
3. Backend ディレクトリで `./gradlew bootRun`
4. Frontend ディレクトリで `npm install` と `.env.local` の設定
5. Frontend で `npm run dev`
6. ブラウザで `http://localhost:3333` を開く
7. Flight Plan Setup ページまたは Swagger で機体を作成
8. シミュレーション開始

**合計: 8 手順（環境構築含む）**

### 変更後の手順数

1. Docker のインストール（初回のみ）
2. リポジトリを clone
3. `docker compose up --build`
4. ブラウザで `http://localhost:3333` を開く
5. 「サンプルで始める」ボタンをクリック

**合計: 5 手順（環境構築含む）、実質 3 手順（Docker がインストール済みの場合）**

### 測定可能な完了条件

- [ ] Backend のすべてのテストが合格している
- [ ] Frontend のビルドとテストが合格している
- [ ] CI がすべて合格している
- [ ] `docker compose up --build` で Backend (:8080) と Frontend (:3333) が起動する
- [ ] ビルドと起動の所要時間を記録する
- [ ] ブラウザで以下を確認し、スクリーンショットを `/opt/cursor/artifacts/` に保存:
  - (a) `/` で「サンプルで始める」ボタンが表示される
  - (b) ボタンを押すと `/controller` に遷移し、複数の機体が表示される
  - (c) 12 秒以上の間隔を空けた 2 枚のスクリーンショットで、機体の位置が変わっている（md5 も異なる）
  - (d) `/operator` で 1 機を選び、初期値と異なる高度・速度・針路を指示する
  - (e) 指示の前と 15〜30 秒後の `/aircraft/location/all` の `instructedVector` と `vector` の値で、機体が指示した値に近づいていることを示す
  - (f) ブラウザのコンソールと Backend のログに ERROR がない

---

## 影響範囲

- **Backend**: Dockerfile の作成。既存の API や動作は変更しない
- **Frontend**: 
  - Dockerfile の作成
  - トップページ (`app/page.tsx`) に「サンプルで始める」ボタンと JSON アップロード機能を追加
  - サンプルシナリオ JSON の作成
  - 環境変数の設定（Docker 環境用）
- **ルート**: 
  - `docker-compose.yml` の作成
  - `README.md` にクイックスタートセクションを追加
  - `Backend/README.md` と `Frontend/README.md` の更新

---

## 実装計画

### Phase 1: サンプルシナリオの作成

- [ ] 到着機が 4〜6 機含まれる現実的なサンプルシナリオ JSON を作成する
  - 羽田（RJTT）または成田（RJAA）への到着機
  - 異なる高度、速度、針路を持つ機体
  - コンフリクトの可能性がある配置（訓練用）

### Phase 2: Docker 環境の構築

- [ ] `Backend/Dockerfile` を作成
  - Java 21 の JRE をベースイメージとする（Java 22, 24 は使用しない）
  - Gradle でビルドし、JAR を実行する
  - ポート 8080 を公開
- [ ] `Frontend/Dockerfile` を作成
  - Node.js 18 以上をベースイメージとする
  - 依存関係をインストール
  - Next.js をビルドし、production モードで起動
  - ポート 3333 を公開
  - 環境変数 `BACKEND_SERVER_IP` と `BACKEND_SERVER_PORT` を設定
- [ ] `docker-compose.yml` を作成
  - Backend と Frontend のサービスを定義
  - Backend を先に起動し、Frontend はそれを待つ
  - ネットワークを設定し、サービス間の通信を確立

### Phase 3: フロントエンドの変更

- [ ] トップページ (`app/page.tsx`) に「サンプルで始める」ボタンを追加
  - ボタンをクリックすると、サンプルシナリオを読み込み（`POST /api/scenario/load`）
  - シミュレーションを開始（`POST /api/simulation/start`）
  - `/controller` ページに遷移
- [ ] トップページから JSON ファイルをアップロードできる機能を追加（roadmap 1-5）
  - ファイル選択 → `parseScenarioJson` → `loadScenarioAndStart` の流れ
  - エラーハンドリングとユーザーへのフィードバック

### Phase 4: テストと検証

- [ ] Backend のテストを実行し、すべて合格することを確認
- [ ] Frontend のビルドとテストを実行し、すべて合格することを確認
- [ ] Docker 環境でのビルドと起動を確認
- [ ] Playwright を使用してブラウザでの動作を確認し、スクリーンショットを保存

### Phase 5: ドキュメントの更新

- [ ] ルートの `README.md` に「クイックスタート（Docker）」セクションを追加
- [ ] `Backend/README.md` を Docker に関連する情報で更新
- [ ] `Frontend/README.md` を Docker に関連する情報で更新
- [ ] 実装していない機能については記載しない

---

## 検証

- [ ] Backend のテストが通る（`./gradlew test`）
- [ ] Frontend のビルドが通る（`npm run build`）
- [ ] Frontend のテストが通る（`npm test`）
- [ ] Docker 環境でビルドと起動が成功する（`docker compose up --build`）
- [ ] ブラウザでの手動動作確認
  - 「サンプルで始める」ボタンの動作
  - シミュレーションの開始と機体の移動
  - 管制指示の反映
  - エラーログがないこと
- [ ] スクリーンショットと値の記録（完了条件参照）

---

## 未解決事項（Unresolved Questions）

- サンプルシナリオの具体的な内容（機体数、配置、コンフリクトの有無）
- JSON アップロード機能の実装範囲（roadmap 1-5 が小さく収まらない場合は別の目標に分ける）
- Docker イメージの最適化（ビルド時間の短縮、イメージサイズの削減）

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/spec.md](../spec.md) の roadmap 1-5
- [Backend/README.md](../../Backend/README.md)
- [Frontend/README.md](../../Frontend/README.md)
- [GitHub Issue #48](https://github.com/Futty93/Horus/issues/48)
