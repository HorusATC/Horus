# ATIS の QNH（高度計規正値）

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md) フェーズ 6-2、[spec/20261122-atis-runway-in-use/spec.md](../20261122-atis-runway-in-use/spec.md)、[spec/20261122-uniform-wind/spec.md](../20261122-uniform-wind/spec.md)、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、Issue [#71](https://github.com/HorusATC/Horus/issues/71)
- **リポジトリ配置**: `spec/20261122-atis-qnh/spec.md`

## 概要

既存の `GET` / `PUT /api/atis` に、空港ごとの QNH（高度計規正値、ヘクトパスカル）を任意フィールドとして足し、ATIS 本文に載せる。Issue #71 が挙げる風・QNH・使用滑走路のうち、残っているのは QNH である（使用滑走路は #132、風は #136）。既定は欠測のままとし、欠測時の本文と、位置・針路・GS・IAS・MSAW／STCA の高度は今日と同一にする。移行高度やフライトレベル、指示高度と真高度の換算は入れない。

同じ PR の文書修正（機能変更ではない）: Frontend README 冒頭の想定ワークフロー表で、Operator の操作一覧に `DEPART`（出発初期上昇）が無い。#137 で API と後段の説明は入っているので、役割表にも出発を足して揃える。

## 背景・課題

### 現状（main `8e5b680`、#137 初期上昇マージ済み。Issue #115 クローズ）

- 優先度 1 は #128 で満たしている。本スライスでクイックスタートを広げない。
- 優先度 2 の到着・出発のうち、SID／STAR（#114）は公式 AIP の入手経路はあるがアクセス待ちのため、今は選ばない。
- ATIS は使用滑走路と一様風まで入っている。風も QNH も無いときは本文が `WIND AND WEATHER NOT AVAILABLE.`。風だけあるときは風行のあと `WEATHER NOT AVAILABLE.`。QNH のフィールドも入力も無い。合成テストは、欠測本文に QNH の数字が混ざらないことを既に断言している。
- 位置 API の高度と IAS→TAS 換算（標準大気、気圧高度として現在高度を使う）は、QNH を見ない。
- ギャップ台帳のレーダー画面の残りに、方位目盛と、Deferred のセクター境界がある。#71 はオープンのまま（コメントは #132 時点で風・QNH 未対応と書いており、風は #136 で解消済み）。
- Frontend README の Operator 役割表は「高度・速度・針路、Direct To、Hold、最終進入、進入復行、タワー移管、Resume」までで、`DEPART` が欠けている（同ファイル後段と `flightPlanControl.tsx` にはある）。

### 課題（Problem Statement）

到着・出発の情報に含まれる高度計規正値が ATIS に無く、管制官とパイロット役が QNH を共有できない。#71 の残件である。

### なぜ今か（Motivation）

使用滑走路と風の次に、同じ一次資料（JCAB (Ⅱ)７(４)・(Ⅲ)５(１)、FAA 2-9-3 / 3-10-1）が挙げる要素が QNH である。加算 API と本文・パネルに閉じられ、既定を欠測にすれば運動を触らずに 1 PR で検証できる。方位目盛は残課題だが、#71 を閉じる訓練上の価値の方が大きい。SID／STAR は今選ばない。

---

## 方針

### 決定方針（Decision）

1. **根拠となる一次資料**
   - **日本（管制方式）**: 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』（[JAPA 2025-03 公開 PDF](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)）(Ⅱ)７(４)、(Ⅲ)５(１): 到着機・出発機への情報に高度計規正値を含み、ATIS 受信の通報があれば省略できる。
   - **気象庁**『[航空気象通報式 第３版 第17号](https://www.jma.go.jp/jma/kishou/books/tsuhoshiki/koukuu/koukuu3_17.pdf)』（[一覧](https://www.jma.go.jp/jma/kishou/books/tsuhoshiki/tsuhoshiki.html)、2025-08-07 改正）規則 12: QNH はヘクトパスカル、10 分位を切り捨てた整数、指示文字 `Q` を前置（例: 995.6 hPa → `Q0995`）。欠測は `Q////`。国内記事に inHg（指示文字 `A`）があるが、本スライスの ATIS 本文は hPa のみとする。
   - **FAA** Order JO 7110.65BB Change 3（効力 2026-07-09）:
     - [2-9-2 / 2-9-3](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap2_section_9.html): 関連データの変化で新しい録音にする。ATIS の内容に気象（高度計規正値を含む）が含まれる。
     - [3-10-1 Landing Information](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap3_section_10.html): 着陸情報に altimeter が含まれる。

2. **QNH は空港ごと。既定は欠測。運動は変えない**
   - 風（#136）はセッション全体で 1 組だが、QNH は飛行場の規正値なので **空港ごと** に持つ。RJTT を変えても RJAA の本文と識別は、値が変わらない限り動かさない。
   - セッション開始時は両空港とも欠測。欠測を 1013 とみなして本文や運動に入れない（#132 が気象を作らない方針と同じ）。
   - **高度計モデル（移行高度、FL、指示高度と真高度／気圧高度の差、IAS 換算の QNH 補正）は本スライスに入れない。** 欠測時はもちろん、QNH を設定したあとも、位置 API の高度・指示高度・IAS→TAS・MSAW／STCA の垂直判定は今日と同一。QNH は ATIS の観測値および管制・パイロット役が読む規正値である。換算を後続で足すときは、退行テストを先に置いてからにする。

3. **API（加算。既存の欠測本文と PUT 形を壊さない）**
   - `GET /api/atis` の既存キーは残す。加算例: `qnhAvailable`（boolean）、`qnhHpa`（欠測では省略）。
   - `PUT /api/atis/{icao}`: `runwayInUse` は現行どおり必須。`qnhHpa` キーを省略した既存ボディは QNH を変えない（風キー省略が風を変えないのと同じ）。設定は整数ヘクトパスカル（例: `"qnhHpa": 1013`）。欠測へ戻すは `"qnhHpa": null`。範囲は **850–1100**（整数）。範囲外は 400。
   - QNH の値がその空港で変わったとき、**その空港だけ** 情報識別を 1 つ進める（FAA 2-9-2）。同じ値の再送では進めない。明示の `informationIdent` は現行どおり優先してよい。
   - 風の更新規則（共有風、両空港の識別、本文の風行）は変えない。

4. **ATIS 本文（ゴールデン文字列で固定）**
   - 風も QNH も欠測: 現行どおり `WIND AND WEATHER NOT AVAILABLE.` を一字維持する。この行に QNH の数字を出さない。
   - 風あり・QNH 欠測: 現行どおり風行（または `WIND CALM.`）のあと `WEATHER NOT AVAILABLE.` を一字維持する。
   - 風欠測・QNH あり: `WIND NOT AVAILABLE.` と `QNH 1013.`（値は 4 桁にせず、通報する整数。1000 未満は先頭ゼロを付けて 4 桁でもよいが、本文は `QNH` と十進の整数で統一し、合成関数のテストで固定する。推奨は `QNH 0995.` のように 4 桁）。
   - 風あり・QNH あり: 風行のあと `QNH 1013.`。`WEATHER NOT AVAILABLE.` は置かない（QNH 以外の視程・雲は未実装であり、行を増やして欠測を列挙しない）。
   - 英語本文は既存 ATIS に合わせる。inHg 併記は本スライスの非目標（気象庁は国内記事で `A` 群を使う）。

5. **Controller の操作**
   - 既存の ATIS パネル（Controller 編集、Operator 読取専用）に、当該空港の QNH（hPa）入力と欠測へ戻す操作を足す。風の入力は変えない。
   - 空港を切り替えて見るとき、QNH は空港ごとの値を表示する。

6. **退行を出さない（No-regression）**
   - 既存 API のパス・必須フィールドを変えない（`GET`/`PUT /api/atis` の既存キー、風オブジェクト、`JOIN FINAL`／復行／`DEPART`／タワー移管／Hold、位置 API、`GET /ats/runways`）。
   - QNH キーを付けない PUT、および QNH 欠測の GET 本文は今日と同一。風欠測時の運動と同値テストを壊さない。QNH を入れても高度・IAS・警報の垂直判定は変えない。
   - 既存の Backend / Frontend テストは、本機能用の期待値追加を除き、契約を緩めて通してはならない。CI 4 ジョブがマージ SHA で合格する。
   - Operator 役割表の修正は README のみで、実行時の挙動を変えない。

7. **同じ PR の文書修正**
   - `Frontend/README.md` 冒頭の想定ワークフロー表、Operator の操作欄に出発初期上昇（`DEPART`）を加える。後段の Flight Plan Control 説明と矛盾しないこと。

### 検討した他案（Alternatives Considered）

- **案 A: 方位目盛（コンパスローズ）。** 採用しなかった理由: ギャップ台帳の残件ではあるが、針路は真方位フレームなので磁方位目盛は出せない。真方位の目盛は有用だが、オープン Issue の QNH より訓練上の共有情報が後になる。次候補とする。真方位であることは後続でも明示する。
- **案 B: QNH 設定で指示高度と真高度をずらし、IAS 換算も気圧高度に合わせる。** 採用しなかった理由: MSAW／STCA と全機の高度が変わり、欠測時との退行判定が重い。移行高度の公式値も本ドラフトでは未確認。ATIS に載せることを先にする。
- **案 C: QNH を風と同じくセッション 1 組にする。** 採用しなかった理由: 羽田と成田で規正値が違うのが通常であり、共有にする必要がない。
- **案 D: SID／STAR（#114）。** 採用しなかった理由: 公式 AIP へのアクセスがまだ無い。

### トレードオフ（Trade-offs）

- **メリット**: #71 の残件が埋まり、ATIS で規正値を共有できる。既定は今日と同一。API は加算。
- **デメリット / 受容する制約**: 高度の数字は QNH を変えても動かない。視程・雲・inHg 併記は無い。移行高度／FL は無い。

### 非目標（Non-goals）

- 移行高度、フライトレベル（QNE）、指示高度と真高度の差、IAS 換算への QNH 補正
- 視程、雲、気温、露点、METAR 電文の取り込み、inHg の本文併記
- 地上風による使用滑走路の自動選定、磁偏差
- 方位目盛、セクター境界、SID／STAR、公示ホールディング
- セッション記録、出発席と進入席の分割

---

## 完了条件（Success Criteria）

- [x] `spec/20261122-atis-qnh/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている。出典 URL がある
- [x] 風も QNH も欠測のとき、ATIS 本文は現行の `WIND AND WEATHER NOT AVAILABLE.` のままである。風あり・QNH 欠測では現行の風行 + `WEATHER NOT AVAILABLE.` のままである。既存形の PUT（`runwayInUse` のみ、または風キーのみ）は QNH を変えない
- [x] `GET /api/atis` に `qnhAvailable` / `qnhHpa`（欠測時は後者を省略）が付く。`PUT` で空港ごとに QNH を設定すると、その空港の本文に `QNH …` が入り、その空港の情報識別だけが進む。他空港の QNH と識別は変わらない。`qnhHpa: null` で欠測に戻せる。850–1100 の外は 400
- [x] 合成関数のゴールデン文字列が、4 通り（両方欠測、風のみ、QNH のみ、両方あり）を固定する（本文は `QNH 0995.` のように 4 桁ゼロ埋め）
- [x] QNH の有無にかかわらず、位置・指示高度・IAS→TAS・無風時の GS／トラック・MSAW／STCA の垂直判定が、QNH を導入する前と同一である（退行テスト）
- [x] **退行なし**: 既存の Backend / Frontend テストが、契約を緩めずに合格する。風・`DEPART`／`JOIN FINAL`／復行／Hold の契約を壊さない
- [x] Frontend と Backend のビルドおよびテストが合格する
- [ ] CI の 4 ジョブが、マージするコミットの SHA で全部合格する
- [x] Controller の ATIS パネルで空港ごとに QNH を入れ・欠測に戻せ、Operator は読取専用で本文を見る。ブラウザ確認とスクリーンショットを残す（欠測 → RJTT だけ 1013 → 識別と本文の更新、RJAA は欠測のまま → 欠測へ戻す）
- [x] Backend README と Frontend README に、空港ごとの QNH（hPa）、欠測既定、本文の 4 通り、高度モデルを変えないこと、出典を書く。Frontend README の ATIS 説明から「QNH は未実装」を落とす
- [x] Frontend README 冒頭の Operator 役割表に `DEPART`（出発初期上昇）が含まれる
- [x] `spec/spec.md` の 6-2 とギャップ台帳を、QNH が入ったこと（高度換算・視程は残る）に合わせて更新する。#71 の「風・QNH・使用滑走路」は本スライスで揃う

---

## 影響範囲

- **Backend**: `AtisService` / `AtisTextComposer` / `UpdateAtisRequestDto`、空港ごとの QNH 状態、`UranosAPI.yml`、契約テスト（運動コードは変えない）
- **Frontend**: ATIS パネルの QNH 入力、`utility/api/atis.ts` の型
- **README**: Backend / Frontend（役割表を含む）
- **文書**: `spec/spec.md` 6-2、ギャップ台帳
- **変えないもの**: 風の共有規則、飛行運動、警報の垂直判定、`DEPART` ほか既存 API

---

## 実装計画

### Phase 1（Must-have）

1. 本文合成と PUT 契約（省略は変更なし、範囲外は 400、空港ごと、識別送り）のテストを先に置く。欠測本文の一字同値と、高度／IAS の退行テストも含める。
2. サービス・DTO・パネルを接続する。
3. README、役割表、6-2／台帳を同じ PR で更新する。

### Phase 2（このスライスの外）

- QNH を使った気圧高度・移行高度／FL
- 視程・雲、inHg 併記
- 方位目盛、SID／STAR（アクセス確保後）

---

## 検証

- [x] 4 通りの本文、空港分離、識別送り、400、キー省略時の非変更が、未実装では失敗し、実装後は成功する
- [x] 既存の ATIS／風／無風運動／`DEPART` テストが退行しない
- [x] ブラウザで RJTT のみ QNH を入れ・戻す操作を確認し、スクリーンショットを残す
- [ ] CI 4 ジョブがマージ SHA で合格する
- [x] Operator 役割表に `DEPART` がある

---

## 未解決事項（Unresolved Questions）

- 本文の QNH は `QNH 0995.` の 4 桁ゼロ埋めで固定した（気象庁電文 `Q0995` に合わせる）。
- 移行高度の公式値を後続で使う場合は、入手できた AIP 本文を確認してからにする。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20261122-atis-runway-in-use/spec.md](../20261122-atis-runway-in-use/spec.md)
- [spec/20261122-uniform-wind/spec.md](../20261122-uniform-wind/spec.md)
- 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』: [JAPA 公開 PDF（2025-03）](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)
- 気象庁: [航空気象通報式 第３版 第17号](https://www.jma.go.jp/jma/kishou/books/tsuhoshiki/koukuu/koukuu3_17.pdf)、[気象通報式](https://www.jma.go.jp/jma/kishou/books/tsuhoshiki/tsuhoshiki.html)
- FAA Order JO 7110.65BB Change 3: [2-9 ATIS](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap2_section_9.html)、[3-10-1](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap3_section_10.html)
- Issue [#71](https://github.com/HorusATC/Horus/issues/71)
- [Backend/README.md](../../Backend/README.md)
- [Frontend/README.md](../../Frontend/README.md)
