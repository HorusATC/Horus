# Docker なしでも最少手順で最初のシナリオまで到達する

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md)、[spec/20261121-one-command-quickstart/spec.md](../20261121-one-command-quickstart/spec.md)、[docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)

## 概要

研究者が、Docker を持たない環境でも、ルート README の先頭手順だけを追って、最初のサンプルシナリオをレーダーで動かせるようにする。ローカル起動を 1 本のスクリプトにまとめ、README の古い「Swagger で機体を作る」記述と、clone だけの不完全なクイックスタートを消す。API の動きは変えない。

## 背景・課題

### 現状

- 2026-11-21 のクイックスタート（[20261121-one-command-quickstart](../20261121-one-command-quickstart/spec.md)、Done）で、`docker compose up --build` とトップの「START WITH SAMPLE (サンプルで始める)」は入った。ボタンはトップ中央にあり、押すと 5 機の到着サンプルを読み込み、シミュレーションを開始して `/controller` へ遷移する。トップからの JSON アップロードもある。
- 2026-11-22 に main `5e55040` を `/tmp` へ新規 clone して、ルート README をそのまま辿った監査結果は次のとおり。
  - この環境には Docker が無く、公式の「最も簡単な方法」は途中で止まった。
  - `java` は PATH に無く、README の確認コマンド `java --version` は失敗する。Java 21 は `~/jdk21`（Temurin 21.0.13.1）にあり、`JAVA_HOME` を自分で指定すれば動く。
  - ローカル経路で実際に打ったコマンドは 8 個。待ちは Backend `./gradlew bootRun` 約 8 秒、Frontend `npm ci` 約 8 秒、`npm run dev` 約 4 秒（このマシンは Gradle / npm のキャッシュ済み。初回の依存取得時間は未計測。Docker 初回の 5〜10 分も未検証）。判断は 3 つ（Docker が無い、`JAVA_HOME` の場所、`.env.local` を編集するか）。手操作はブラウザで `http://localhost:3333` を開き、サンプルボタンを押すことの 2 つ。
  - ボタン後、位置 API は ANA101 / JAL202 / SKY303 / ANA404 / JAL505（いずれも目的地 RJTT）を返し、数秒後に位置が更新されていた。Controller レーダーにも 5 機が出た。
- ルート README の先頭「クイックスタート」は `git clone` と `cd Horus` だけで終わる。その後にシステム概要が長く続き、「クイックスタート（Docker）」は後半にある。システム概要は今も「フライトプラン作成・航空機 spawn は現状 Swagger（`/docs.html`）で行う」と書いている。Backend README の連携確認は「シミュレーション開始 → 航空機作成」のままである。Frontend のローカル手順は `npm ci`、`cp .env.sample .env.local`、「ファイルを開き設定してください」と続くが、サンプルの値は `localhost:8080` で、編集は不要だった。
- レーダーの初期中心は `centerCoordinateContext` の (34.482, 138.614) で、`GLOBAL_SETTINGS` の東京座標とも、サンプルの RJTT 進入とも一致しない。表示幅 400 km では 5 機は画面内に入るが、中心の北東寄りに寄って見える。

### 課題（Problem Statement）

- 優先 1（少ない手順で最初のシナリオまで）の公式経路は Docker 前提である。Docker が無い研究者は、分散したローカル手順と、古い Swagger の記述に迷い込む。
- 先頭のクイックスタートが clone だけで終わるため、次に何をすればよいかが README から読み取れない。
- Java 21 が入っていても PATH に無いと、README どおりの確認で詰まる。`JAVA_HOME` の具体的な探し方はプレースホルダのままである。

### なぜ今か（Motivation）

- 機能面の到着一席（対気速度、制限、間隔、滑走路、復行、タワー移管、MSAW、緊急スコーク、進入端距離と RBL）は main に揃った。承認済みの実装キューも空である。
- 残っている参入障壁は、新しい管制能力ではなく、ドキュメントとローカル起動の手順である。ここを先に直さないと、優先 1 が「Docker がある人だけの最短経路」のままになる。

---

## 方針

### 決定方針（Decision）

1. **Docker は残す。ただし README の第一経路を、Docker なしでも完走できる手順にする。** Docker がある人向けの `docker compose up --build` は第二経路として残す。
2. **リポジトリルートにローカル起動スクリプトを置く**（`scripts/dev-up.sh`）。スクリプトは次だけを行う。
   - Java 21 を探す。優先順は、(1) 既に設定された `JAVA_HOME` が 21 のとき、(2) macOS の `/usr/libexec/java_home -v 21`、(3) SDKMAN の candidates、(4) `/usr/lib/jvm/*21*`、(5) `~/jdk21`、(6) PATH 上の `java` が 21 のとき。22 と 24 は使わない。見つからなければ Temurin 21 のインストール案内を出して非 0 で終了する。
   - Frontend で `node_modules` が無い、または `package-lock.json` より古いときに `npm ci`。`.env.local` が無ければ `.env.sample` をコピーする（中身が `localhost:8080` なら編集させない）。
   - Backend の `bootRun` と Frontend の `npm run dev` を起動し、ヘルスチェック後に `http://localhost:3333` と「START WITH SAMPLE (サンプルで始める)」を標準出力へ出す。Ctrl-C で両方を止め、孤児プロセスを残さない（trap）。ログは gitignore 対象ディレクトリへ出す。
3. **ルート README を第一経路に合わせて書き直す。** 先頭の不完全なクイックスタートをやめ、clone のあとに `scripts/dev-up.sh` と、ブラウザでの 1 クリックを書く。システム概要の Swagger spawn 記述を削除し、サンプルボタンを正とする。Backend / Frontend README の起動節も同じ内容に合わせ、連携確認の「航空機作成」をサンプルボタンへ置き換える。
4. **API の動きは変えない。** シナリオ読み込み、シミュレーション開始、位置 API の契約は現状のままである。
5. **初期中心の変更は Must にしない。** 400 km 表示で 5 機は既に入る。東京または RJTT への寄せは、スクリプトと README が通ったあとの Should とする。
6. **機械的確認**は `scripts/verify-quickstart.sh` に置く。README 契約（完了条件の確認 1）は CI の Frontend ジョブで常に実行する。確認 2〜4 は稼働中サーバーが必要なため、ローカルで実行し、既存の Docker Compose CI ジョブへ軽い追加として載せる（イメージビルド済みのあとなので追加コストは小さい）。

### 検討した他案（Alternatives Considered）

- **案 A: README の誤記だけ直す。** 採用しなかった理由: ローカル起動が 2 ターミナルと `JAVA_HOME` の手作業のままでは、手順数はほとんど減らない。
- **案 B: Docker の導入手順を厚くし、ローカル経路は触らない。** 採用しなかった理由: 今回の監査環境のように Docker が無い場合の障壁が残る。優先 1 は「少ない手順」であり、「Docker を先に入れさせる」ことそのものが大きな手順である。
- **案 C: サンプル開始後にレーダー中心を自動で RJTT へ移す。** 有用だが、起動手順の短縮とは別の表示変更である。本スライスの Must にはしない。
- **案 D: 起動 API を新設する。** 不要。既存の `bootRun` と `npm run dev` とサンプルボタンで足りる。

### トレードオフ（Trade-offs）

- **メリット**: Docker が無い研究者でも、README の先頭からサンプルレーダーまで辿れる。Swagger と不完全なクイックスタートによる迷いが消える。
- **デメリット / 受容する制約**: Java 21 と Node 18 以上は、ローカル経路では今までどおり必要である。スクリプトは OS ごとの JDK 配置を全部は知れない。見つからないときは案内して終了する。Docker 経路の初回ビルド時間は、本スライスでは計測も短縮もしない。

---

## 完了条件（Success Criteria）

- [x] `spec/20261122-local-quickstart/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている
- [x] 位置 API・シナリオ読み込み・シミュレーション開始の既存の動きが変わっていない（本スライスでエンドポイントの契約を足さない・変えない）
- [x] ルート README の先頭に、Docker なしで完走できる手順がある。先頭が `git clone` と `cd` だけで終わらない。本文に「spawn は Swagger で行う」と書かない
- [x] `scripts/dev-up.sh` が、Java 21 の解決、必要なら `npm ci`、`.env.local` の用意、Backend と Frontend の起動、完了メッセージ（URL とサンプルボタン）を行う。Java 22 / 24 は選ばない
- [x] 次の機械的確認が、未実装では失敗し、実装後は成功する（`scripts/verify-quickstart.sh`）。
  1. ルート README の先頭 80 行に `dev-up` と `START WITH SAMPLE` があり、`docs.html` で機体を作る手順が無い
  2. 起動後に `GET http://localhost:3333/` が 200 を返し、本文に `START WITH SAMPLE (サンプルで始める)` がある
  3. 同ホストで既存の `POST /api/scenario/load`（`Frontend/data/quickstart-arrival-sample.json`）と `POST /api/simulation/start` が成功し、`GET /api/aircraft/location/all` が 5 機を返す
  4. 1〜2 秒後の再取得で、少なくとも 1 機の緯度または経度が変わっている
- [x] Backend README と Frontend README の起動・連携確認が、スクリプトまたは同じ少数手順を指し、初回ユーザーに Swagger での機体作成を要求しない
- [x] `JAVA_HOME` が空で `java` が PATH に無いとき、`~/jdk21` に 21 があればスクリプトがそれを使う
- [x] Frontend のテストとビルド、Backend の既存テストが合格する
- [x] CI の 4 ジョブが、マージするコミットの SHA で全部合格する
- [x] ブラウザ確認: スクリプトで起動したあと、トップのサンプルボタンを 1 回押し、Controller に到着 5 機が見える

## 影響範囲

- **scripts/dev-up.sh**（新規）、**scripts/verify-quickstart.sh**（新規）、`.gitignore`（ログディレクトリ）
- **README.md**、**Backend/README.md**、**Frontend/README.md**
- **.github/workflows/ci.yml**（README 契約チェックと Docker ジョブへの live 検証）
- **spec/20261017-terminal-radar-gaps/spec.md**（進入復行・タワー移管を実装済みとして台帳を更新）
- **API / OpenAPI / 飛行ロジック**: 変更しない（`Backend/src/main` に差分なし）

## 実装計画

### Phase 1（Must-have）

1. 起動スクリプトと機械的確認スクリプトを先に置く。
2. ルート README の先頭をローカル最短経路に差し替え、Docker 経路をその直後の代替にする。Swagger spawn の記述を消す。
3. Backend / Frontend README の起動節を同じ内容に合わせる。
4. CI に README 契約を載せ、Docker ジョブで確認 2〜4 を実行する。
5. 台帳（terminal-radar-gaps）の復行・移管を実装済みに更新する。

### Phase 2（Should / 外）

- 初期中心を東京または RJTT 寄りにする。
- Docker 初回ビルド時間の計測と短縮。

---

## 検証

- [x] 完了条件の機械的確認が、実装前は失敗し、実装後は成功する
- [x] `JAVA_HOME` 未設定かつ `~/jdk21` あり、の条件でスクリプトが Backend を起動できる
- [x] ブラウザでサンプル 5 機が見える
- [x] API 契約を変える差分が PR に含まれていない（`git diff origin/main -- Backend/src/main` が空）

---

## 未解決事項（Unresolved Questions）

- ~~スクリプト名を `dev-up.sh` にするか、`start-local.sh` にするか。~~ → `scripts/dev-up.sh` に決定。
- Windows 向けの同等スクリプトを本スライスに含めるか。第 1 版は Unix シェルとし、Windows は Docker 経路または WSL を README で案内する。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20261121-one-command-quickstart/spec.md](../20261121-one-command-quickstart/spec.md)
- [README.md](../../README.md)
- [Frontend/README.md](../../Frontend/README.md)
- [Backend/README.md](../../Backend/README.md)
