# Horus: ATCレーダーシミュレーションシステム

## クイックスタート（ローカル・Docker 不要）

Java 21 と Node.js 18 以上があれば、次だけで最初のサンプルシナリオまで到達できます。

```bash
git clone https://github.com/HorusATC/Horus.git
cd Horus
./scripts/dev-up.sh
```

スクリプトが Backend と Frontend を起動し、準備完了後に URL を表示します。

1. ブラウザで [http://localhost:3333](http://localhost:3333) を開く
2. 「**START WITH SAMPLE (サンプルで始める)**」をクリック

RJTT RNAV STAR（`OSHIMA1A` / `AKSEL1K` / `AROSA2C` / `AKSEL2C` / `GODIN2A`、RWY 34L/34R カタログ）を割り当てた到着機 5 機が読み込まれ、ATIS の使用滑走路は 34L に揃い、シミュレーションが始まり、Controller レーダーへ遷移します。追加の手続き割当は不要です。停止は Ctrl-C（両方のプロセスを止めます）。

**VAMOS 出発サンプル（別シナリオ）**: 到着既定はそのままです。出発を試すときはトップの「**START VAMOS DEPARTURE SAMPLE (VAMOS出発サンプル)**」を押すか、`Frontend/data/quickstart-departure-sample.json`（`VAMOS4.RWY34L_34R` + TATEYAMA、使用滑走路 34L）を「LOAD SCENARIO JSON FILE」で読み込みます。SID/STAR カタログは `aip/rjtt-sid-star-20261101.json`（スライス 1）と `aip/slice3-20261101.json`（スライス 3: RJTT RNAV SID 66・RJAA RNAV SID/STAR 48）を結合して読みます。Operator から RJTT / RJAA を選んで割当できます。VISIP と従来方式プレートは未コード（不足プレートの残りは `aip/slice2-20261101.md`）。

Derived from AIP Japan (AIRAC 1 Nov 2026), may not be current, edited by the Horus project and not government-made.

> Java 21 が見つからない場合は、[Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) を入れ、`JAVA_HOME` を指すか `~/jdk21` に展開してください。Java 22 / 24 は使えません。

Windows では WSL 上で同スクリプトを使うか、下の Docker 経路を使ってください。

## クイックスタート（Docker・代替）

Docker がある場合は、依存のインストールなしで試せます。

```bash
git clone https://github.com/HorusATC/Horus.git
cd Horus
docker compose up --build
```

初回はイメージビルドに時間がかかります（約 5〜10 分）。準備ができたら [http://localhost:3333](http://localhost:3333) を開き、「**START WITH SAMPLE (サンプルで始める)**」をクリックします。停止は `docker compose down`。

## システム概要

**Horus**は、日本のレーダー管制システムを対象としたオープンソースのシミュレーターです。バックエンドは**ウラノス** (Uranus) という愛称で、Java言語で実装されています。フロントエンドは**オモテノス** (Omotenus) という愛称で、TypeScriptで書かれています。Horusという名前はエジプト神話の天空の神ホルスに由来し、ウラノスはギリシャ神話の天空の神ウラノスに由来しています。

### 開発背景

欧州にはデルフト工科大学が開発した管制レーダーシミュレーターのオープンソースモデルがありますが、日本では利用が難しい現状があります。さらに、日本には誰でも気軽に利用できるオープンソースのレーダー管制シミュレーターが存在しないため、研究者は安全性向上のための実験を行う際に独自のシミュレーターを作成する必要があります。これが研究のスピードを鈍化させる原因となっています。

このような背景から、Horusは研究者の負担を軽減し、研究のスピードを向上させることを目的に開発されました。Horusを利用することで、日本のレーダー管制システムに関する安全性実験や研究をより効率的に進めることができます。

## システム構成

システムは以下の2つの主要コンポーネントで構成されています：

1. **ウラノス (Uranus)** - バックエンド

   - Java 21 + Spring Boot 4.1.3で実装
   - クリーンアーキテクチャに基づく設計
   - 主要コンポーネント：
     - Domain Layer: 航空機、空域、経路などのドメインモデル
     - Application Layer: 航空機レーダーサービス、シナリオサービス
     - Interface Layer: RESTful API（OpenAPI 3.0.2準拠）
     - Infrastructure Layer: インメモリリポジトリ実装
   - 詳細は `Backend/README.md` を参照

2. **オモテノス (Omotenus)** - フロントエンド
   - TypeScript + Next.js 14.2.15で実装（BFF として Java バックエンドへプロキシ）
   - Tailwind CSSによるモダンなUI
   - 2つの主要画面（役割分担を想定した訓練シミュレーション）：
     - **Controller（管制官役）**: レーダーを見ながら音声で指示を出す。自分の発出指示をメモとして入力・反映する。航空機の直接操作（API 送信）は行わない。
     - **Operator（パイロット役）**: 管制官の音声指示を復唱しながら入力し、バックエンドへ送信。実際の航空機操作（高度・速度・針路、Direct To、Resume（SID VA 中は VA 維持）、JOIN FINAL／復行／DEPART 初期上昇／タワー移管（拒否時は DEPART 状態を残す）、RJTT / RJAA SID/STAR 割当（空港切替可。VAMOS は TATEYAMA / DRAKY トランジション可。再割当は JOIN FINAL 進入コースを消す））を行う。レーダーの `GA` / `DEP` / `TWR` は位置 API 射影のまま。
   - 最初のシナリオはトップの「START WITH SAMPLE (サンプルで始める)」で開始できる（既定サンプルは RJTT RNAV STAR `OSHIMA1A` / `AKSEL1K` / `AROSA2C` / `AKSEL2C` / `GODIN2A`・使用滑走路 34L。レーダー既定中心は RJTT）。VAMOS FOUR 出発は別ボタン／`quickstart-departure-sample.json` から読込（到着既定は変えない）。スライス 3 の RJTT / RJAA RNAV 手続きもカタログ割当可。VISIP・従来方式プレートは未コード（`aip/slice2-20261101.md`）。独自シナリオは Flight Plan Setup（`/flight-plan-setup`）または JSON アップロードで投入する。
   - 詳細は `Frontend/README.md` を参照

## システムの特徴

- **リアルタイムシミュレーション**: 航空機の位置、高度、速度などをリアルタイムで計算・表示
- **管制指示機能**: 高度、針路、速度などの管制指示を航空機に送信可能
- **セーフティネット（簡易）**: STCA（航空機間コンフリクト）に加え、訓練用区域高度に基づく MSAW（`MSA` / `LOW`）。区域値は公式 MVA ではない。出発初期上昇（カタログ滑走路・`DEPART`）中の出発廊内では MSAW を抑制する
- **シナリオベース**: 事前定義されたシナリオファイルを使用して様々な状況をシミュレーション
- **オープンソース**: 研究・教育目的で自由に利用可能
- **拡張性**: モジュール化された設計により、新機能の追加が容易
- **双方向インターフェース**: 管制官役（Controller）とパイロット役（Operator）の役割分担。音声による指示出しと復唱のシミュレーションを想定

## 開発環境のセットアップ

コンポーネント単位の詳細は `Backend/README.md` / `Frontend/README.md` を参照してください。最短起動はルートの `./scripts/dev-up.sh` です。

### Git hooks（自動）

`Frontend` で `npm ci`（または `npm install`）すると `prepare` がリポジトリルートの `.husky` を有効化する。ルートで `npm ci` / `npm install` しても同様。pre-commit は staged ファイルに Prettier / ESLint / Spotless をかけ、pre-push は変更があった側で `tsc`・`format:check` または `spotlessCheck` を走らせる。`--no-verify` は使わない。CI が最終ゲート。

## 開発方針

新機能・リファクタリングを実施する際は **必ず spec を作成**してください。形式は `spec/YYYYMMDD-xxxxxx/spec.md`。テンプレートから作成するには `npm run spec:new <スラッグ>` を実行。詳細は [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) を参照。

実装計画・改善項目（フェーズ・技術的負債）の一覧は [spec/spec.md](spec/spec.md) を参照してください。

## ライセンス

このプロジェクトは [MIT License](LICENSE) の下で公開されています。

## ブラウザからフロントエンドにアクセス

フロントエンドの開発サーバー（`npm run dev` または `./scripts/dev-up.sh`）起動後、ブラウザで [http://localhost:3333](http://localhost:3333) を開く。

| 画面                 | URL                  | 役割                                         |
| -------------------- | -------------------- | -------------------------------------------- |
| トップ（オモテノス） | `/`                  | システム概要、サンプル開始、各画面へのリンク |
| フライトプラン設定   | `/flight-plan-setup` | シナリオ作成・編集・「これで始める」         |
| Operator             | `/operator`          | 管制官指示の入力・航空機操作                 |
| Controller           | `/controller`        | レーダー表示・管制官役（指示メモ）           |

レーダー（Controller / Operator 共通）では、進入廊内の到着機に進入端までの沿コース距離・概算時間をデータブロックへ出す（平行滑走路で廊が重なるときは最寄り中心線＝`|xtk|` 最小）。測距線（RBL）ツールで 2 点間の距離と真方位を測れる（詳細は [Frontend/README.md](Frontend/README.md)、[spec/20261122-runway-distance-rbl/spec.md](spec/20261122-runway-distance-rbl/spec.md)、[spec/20261123-frontend-polling-runway/spec.md](spec/20261123-frontend-polling-runway/spec.md)）。データブロックは新規表示時に 8 方位のリーダーオフセットを自動割当し、手動ドラッグで固定できる（API 変更なし。[spec/20261122-leader-line-auto-placement/spec.md](spec/20261122-leader-line-auto-placement/spec.md)）。使用滑走路と ATIS（情報識別つき本文。既定は風・QNH 欠測。Controller が真方位の風と空港ごとの QNH hPa を設定可能。高度モデルは変えない。GET に加算 `revision`、PUT の任意 `expectedRevision` 不一致は 409。Controller パネルは未保存下書きをポーリングで消さない）はレーダー右上パネルで共有する（[spec/20261122-atis-runway-in-use/spec.md](spec/20261122-atis-runway-in-use/spec.md)、一様風 [spec/20261122-uniform-wind/spec.md](spec/20261122-uniform-wind/spec.md)、QNH [spec/20261122-atis-qnh/spec.md](spec/20261122-atis-qnh/spec.md)、revision [spec/20261123-atis-revision/spec.md](spec/20261123-atis-revision/spec.md)）。滑走路カタログは羽田 A/B/C/D・成田 A/B の 12 末端（真方位・変位進入端。値は非公式 AD 2.12 写し／AirBinder AIRAC 2611 由来で公式 AIP in force 未照合。RJTT 05 は着陸不可）（[spec/20261122-runway-catalog/spec.md](spec/20261122-runway-catalog/spec.md)）。

> シミュレーションを動作させるにはバックエンドも必要です。`./scripts/dev-up.sh` は両方を起動します。

# Java環境のセットアップの実行手順

> [!CAUTION]
> このシステムは Java 21 で動作します。Java 22 および Java 24 では互換性の問題が発生するため使用しないでください。

1. **Javaのインストール**

   - [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)（推奨）または [Oracle JDK 21](https://www.oracle.com/jp/java/technologies/downloads/#java21) をインストールします。
   - インストール後、`JAVA_HOME` を Java 21 に合わせるか、`~/jdk21` に展開すると `./scripts/dev-up.sh` が自動検出します。

2. **Javaの環境設定**

   - `java --version` で major が 21 であることを確認します（22 / 24 は不可）。
   - 複数バージョンがある場合は `JAVA_HOME` を Java 21 に設定してください。

3. **依存関係のダウンロード**

   - Gradle 8.14.8 を使用しています。実行時に自動的にダウンロードされます。

4. **コマンドラインで実行**

`./scripts/dev-up.sh` で Backend + Frontend をまとめて起動するか、手動なら `cd ./Backend` と `./gradlew bootRun` でバックエンドのみ起動します。

> [!NOTE]
> Java 22/24がシステムのデフォルトになっている場合は、`JAVA_HOME`環境変数を設定してJava 21を指定してください：
> `JAVA_HOME=/path/to/java21 ./gradlew bootRun`

API リファレンス（開発者向け）は <http://localhost:8080/docs.html> で確認できます。初回のシナリオ開始にはトップの「START WITH SAMPLE」を使ってください。

## 開発コマンド

### Frontend

```bash
cd Frontend

npm run dev          # 開発サーバー起動 (http://localhost:3333)
npm run build        # プロダクションビルド
npm run lint         # ESLint type-aware (app, components, utility, context, types)
npm run format       # Prettier で自動フォーマット
npm run format:check # Prettier チェック (CI用)
npm run typecheck    # TypeScript 型チェック (tsc --noEmit)
npm run tailwind:check # Tailwind content カバレッジチェック
npm test            # Jest テスト実行
```

### Backend

```bash
cd Backend

./gradlew bootRun        # アプリケーション起動 (http://localhost:8080)
./gradlew build          # ビルド
./gradlew test           # テスト実行
./gradlew spotlessApply  # コードフォーマット適用
./gradlew spotlessCheck  # フォーマットチェック (CI用)
```

### PR 作成前のチェック

すべてのチェックが pass することを確認してください：

```bash
# Frontend
cd Frontend && npm run typecheck && npm run lint && npm run format:check && npm test && npm run build

# Backend
cd Backend && ./gradlew spotlessCheck && ./gradlew test
```

詳細は [AGENTS.md](AGENTS.md) の PR チェックリストを参照してください。
