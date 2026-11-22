# 一様風モデル（対地速度・トラック・ATIS 風）

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md) フェーズ 6-1、[spec/20261122-atis-runway-in-use/spec.md](../20261122-atis-runway-in-use/spec.md)、[spec/20261017-indicated-airspeed-instruction/spec.md](../20261017-indicated-airspeed-instruction/spec.md)、[spec/20261122-hold-racetrack-overlay/spec.md](../20261122-hold-racetrack-overlay/spec.md)、[spec/20261122-runway-catalog/spec.md](../20261122-runway-catalog/spec.md)、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)
- **リポジトリ配置**: `spec/20261122-uniform-wind/spec.md`

## 概要

セッション全体で一様な水平風（高度差・突風・シアーなし）を入れ、対地速度（GS）とトラックを風の三角形で決める。風は既存の `GET` / `PUT /api/atis` に任意フィールドとして足し、ATIS 本文に風向風速を書く。既定は現行どおり風なし（欠測）であり、そのときの位置・針路・GS・待機・最終進入は今日と同一になる。針路指示はこれまでどおり機首方位を飛び、最終進入と待機のレグだけ、コースを保つ一次の風向修正角（WCA）を掛ける。Hold API と `JOIN FINAL` の要求形は変えない。

## 背景・課題

### 現状（#135 マージ済み `53ab2d2` を前提）

- IAS 指示は標準大気・無風で IAS→TAS に換算し、その値を GS として針路方向に進む（[indicated-airspeed-instruction](../20261017-indicated-airspeed-instruction/spec.md)）。`FixedWingFlightBehavior` / `PositionUtils.calculateNextPosition` は `groundSpeed` をヘディング方向へ進める。位置 API の `vector` は `heading` と `groundSpeed` のみで、トラック欄は無い。速度ベクトル（`drawHeadingLine`）も `vector.heading` で引く。
- #132 の ATIS は風を入れず、本文は `WIND AND WEATHER NOT AVAILABLE.` である。無風を `CALM` とは呼ばない。`PUT /api/atis/{icao}` の本体は `runwayInUse`（必須）と任意の `informationIdent`。使用滑走路の自動選定はしない。
- #135 のあと、カタログは羽田 A/B/C/D・成田 A/B の 12 末端。ATIS の `availableRunways` と `JOIN FINAL` の対象はそれに従う。RJTT 05 への `JOIN FINAL` は 400 のまま。
- 待機は無風の標準率レーストラックと進入セクターまで実装済み。子 spec とギャップ台帳は「風修正」を残課題としている。
- ロードマップ 6-1「風と対地速度」は、6-2（ATIS・使用滑走路）とは別スライスとして残っている。

### 課題（Problem Statement）

管制席で共有する地上風が無く、GS が対気速度と同じままなので、向かい風・追い風での到達時間、横風でのコースずれ、ATIS の風欄を訓練できない。#132 は意図的に風を外しており、気象欄は欠測のままである。

### なぜ今か（Motivation）

使用滑走路とカタログが揃ったので、ATIS に風を足し、同じ値を運動に掛けるのが次の自然な一切片である。一様水平風に限り、既定を欠測のままにすれば既存契約と無風テストを壊さない。SID／STAR や離陸後の初期上昇は、手続カタログと経路追従が新たに要るため、本スライスより大きい。

---

## 方針

### 決定方針（Decision）

1. **根拠となる一次資料**
   - **風の三角形（GS・トラック・針路）**: FAA *Pilot’s Handbook of Aeronautical Knowledge*, FAA-H-8083-25C。
     - [Chapter 8: Flight Instruments](https://www.faa.gov/sites/faa.gov/files/regulations_policies/handbooks_manuals/aviation/phak/10_phak_ch8.pdf): Groundspeed は TAS を風で補正した対地速度である。
     - [Chapter 16: Navigation](https://www.faa.gov/sites/faa.gov/files/18_phak_ch16.pdf)（目次は [PHAK 一覧](https://www.faa.gov/regulations_policies/handbooks_manuals/aviation/phak)）: 風向は「風が吹き来る方向」。無風では GS = TAS、トラック = 針路。北からの 25 kt は気塊が南へ 25 NM/h 動く。針路 090°・TAS 120 kt のとき、風 270°/20 kt なら GS 140 kt、風 090°/20 kt なら GS 100 kt。針路とトラックの差が偏流角。コースを保つには風向修正を行う（wind triangle）。
   - **METAR の風向は真方位**: 気象庁『[航空気象通報式 第３版 第17号](https://www.jma.go.jp/jma/kishou/books/tsuhoshiki/koukuu/koukuu3_17.pdf)』（[気象通報式一覧](https://www.jma.go.jp/jma/kishou/books/tsuhoshiki/tsuhoshiki.html)、2025-08-07 改正）規則 5.1: 場外の METAR/SPECI の風向は真方位・10 度単位、真北は 360、静穏は `00000KT`。
   - **日本の場内報・離着陸用の風向は磁方位（参考。本スライスでは数値に掛けない）**: 東京航空地方気象台 [WEATHER TOPICS 通巻 86 号（2021-03-29）](https://www.data.jma.go.jp/haneda-airport/weather_topics/file/rjtt_wt20210329.pdf) は、場内報の風向が磁方位、場外報が真方位であると説明する。AIP Japan GEN 2.1（離着陸時の風向は磁方位、それ以外は真方位）および GEN 1.7 の原文は、AIS/SWIM がログイン必須のため本ドラフトでは未取得。シミュレータに磁偏差モデルが無いため、**運動も ATIS の数字も真方位**とする。8°W などをハードコードして磁方位表示にはしない。
   - **ATIS に風向風速を含めること、変化で情報識別を進めること**: #132 と同じ一次資料。
     - 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』（[JAPA 2025-03 公開 PDF](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)）(Ⅱ)７(４)、(Ⅲ)５(１): 到着・出発の情報に風向風速を含み、ATIS 受信の通報があれば省略できる。(Ⅲ)１(６) の地上風による滑走路自動選定は、**本スライスでも行わない**。
     - FAA JO 7110.65BB Change 3: [2-9-2](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap2_section_9.html) は関連データの変化で新しい録音にする。[2-9-3](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap2_section_9.html) の内容に風が含まれる。[3-10-1](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap3_section_10.html) も着陸情報に地上風を含む。

2. **既定は欠測のまま、無風時の運動は今日と同一**
   - セッション開始時の風は「未設定（欠測）」。運動は現行どおり TAS（無風では GS）を針路方向に進める。ATIS 本文は現行の `WIND AND WEATHER NOT AVAILABLE.` を維持する。欠測を `CALM` とは呼ばない（#132 と同じ）。
   - 無風（欠測または後述の CALM）での N ティック後の位置・針路・GS・待機形状・最終進入コースは、風モデル導入前と一致することをテストで固定する。
   - 風を途中で入れたとき、直前の `vector.groundSpeed`（欠測下では TAS に等しい）を TAS として保ち、GS とトラックだけを直ちに更新する。気塊が動き始めるシミュレーション上の不連続として許容する。

3. **一様水平風（セッション全体で 1 組）**
   - 風向は「吹き来る真方位」（PHAK / 気象庁 5.1）、風速はノット。高度・位置によらず同じ。突風・可変風向（VRB）・シアー・乱気流は持たない。
   - 羽田と成田で別の風は持たない。どちらの ATIS から設定しても、運動と両方の本文に同じ風が載る。
   - 入力範囲: 風速 0–99 kt（整数でよい）、風向は 1–360（真北は 360）。風速が 0 のときは方向を無視し、観測ありの静穏（CALM）とする。運動は欠測と同じく風ベクトル 0 だが、本文だけ `WIND CALM.` になる。
   - 風速が TAS を上回る向かい風など、指定コースを保てない場合は、そのティックでは保てる最大の対地成分に止め、異常値で位置を飛ばさない。詳細は実装に委ね、テストで「発散しない」「無風ケースを壊さない」を固定する。

4. **運動（GS・トラック）**
   - 風ベクトルは、風向の反対方向（吹き去る方向）に風速の大きさ。TAS ベクトルは機首方位方向。GS ベクトルは両者の和。`vector.groundSpeed` は常に |GS|。位置は **トラック方向** に GS で進む（無風ではトラック = 針路なので現行と同一）。
   - IAS 指示: 現行の標準大気換算で目標 TAS を求め、加速度は TAS に掛ける。無風では現行どおり目標 GS = 目標 TAS。
   - GS 指示: 目標は対地速度の大きさ。現在の機首方位（コース維持中は WCA 後の針路）に沿う TAS を、|TAS ベクトル + 風| が指示 GS になるよう解く。無風では TAS = 指示 GS。
   - 針路指示（`HEADING`）および Direct To の「フィックスへの方位を針路にする」追従は、これまでどおり機首方位を飛ぶ。横風ではトラックがずれ、毎ティック方位を張り直す現行の Direct To は、偏流しながらフィックスへ近づく（本スライスでは LNAV のコース維持を足さない）。
   - 機体生成 DTO の `groundSpeed` は、従来どおり機首方位に沿う初速（欠測下では GS = TAS）とする。欠測のままならサンプル JSON の数値は今日と同じ GS になる。

5. **コースを維持するモード（最終進入と待機レグ）**
   - `JOIN FINAL` で進入コースに乗ったあと、および待機のインバウンド／アウトバウンドレグでは、指示針路を「コースそのもの」にせず、コースをトラックとして保つ WCA を足す。WCA は風の三角形（PHAK）の一次解、`sin(WCA) = (風速 / TAS) · sin(風向 − コース)` の符号付き。待機の旋回は、現行どおり機首方位の標準率ターン（空中）。地上軌跡の旋回部は無風円から歪む。
   - 待機のレグ時間・距離の公式は、現行の GS ベースの無風式をそのまま使う。アウトバウンド時間を風で伸ばしてインバウンドを 1 分に揃える公式補正は、一次資料の節を未確認のため本スライスの非目標とする（公示ホールディングの風修正の残り）。
   - 待機オーバーレイ（`holding.pattern`）は、現行の無風レーストラックを指示形状として残す。無風では機影と重なる。風ありでは直線レグは概ね重なり、旋回部はずれてよい。
   - `JOIN FINAL` / Hold の要求形、進入端 8 NM の会合点、RJTT 05 の 400 は変えない。進入コース自体はカタログの真方位のまま（#135）。

6. **ATIS API（加算。既存の欠測本文と PUT 形を壊さない）**
   - `GET /api/atis` の既存キー（`airportIcao`、`airportName`、`runwayInUse`、`informationIdent`、`text`、`availableRunways`）は残す。加算例: `windAvailable`（boolean）、`windDirectionDeg`（欠測・CALM では省略可）、`windSpeedKt`（欠測では省略、CALM は 0）。
   - `PUT /api/atis/{icao}`: `runwayInUse` は現行どおり必須。風フィールドを省略した既存ボディは、風を変えず、滑走路変更時だけその空港の識別を進める（今日と同じ）。
   - 風の更新は任意オブジェクトでよい（例: `"wind": { "directionDeg": 160, "speedKt": 12 }`、静穏は `"wind": { "speedKt": 0 }`、欠測へ戻すは `"wind": null`）。風の値が変わったときは、共有風なので **両空港** の情報識別を 1 つ進める（FAA 2-9-2。両方の本文が変わる）。同じ値の再送では進めない。未知の数値は 400。
   - 本文: 欠測時は現行の 1 行 `WIND AND WEATHER NOT AVAILABLE.` を一字どおり維持する。風があるときはその行を置き換え、`WIND 160 DEGREES 12 KNOTS.`（3 桁の真方位とノット）または `WIND CALM.` とし、続けて `WEATHER NOT AVAILABLE.` を置く（視程・QNH 等はまだ無い）。合成関数のゴールデン文字列で固定する。
   - 位置 API の既存キーは残す。`vector.heading` は機首方位のまま。加算で `vector.track`（度、無風では heading に等しい）を返す。Frontend の速度ベクトル、STCA／RBL の直線予測は、`track` があればトラックと GS で引き、無ければ現行どおり heading を使う。

7. **Controller の操作**
   - 既存の ATIS パネルに、風向・風速の入力（および欠測へ戻す／CALM）を足す。使用滑走路のコンボは #135 の全末端のまま。風の自動選定はしない。
   - データブロックの速度欄は、従来どおり `vector.groundSpeed`（風があれば真の GS）を表示する。

### 検討した他案（Alternatives Considered）

- **案 A: 空港ごとに異なる風を持ち、機体は目的地空港の風を使う。** 採用しなかった理由: 一様風の第一版としては、空港間の機体で風が跳ぶ。セッション 1 組の方が検証しやすい。
- **案 B: ATIS 本文だけ風を書き、運動は無風のまま。** 採用しなかった理由: 6-1 の目的は GS であり、表示と運動が食い違う。
- **案 C: 待機にも公式のアウトバウンド時間補正を入れる。** 採用しなかった理由: 参照すべき手続書の節を本ドラフトでは確認していない。WCA と運動だけに留め、時間補正は後続にする。
- **案 D: ATIS の風向を磁方位で出す。** 採用しなかった理由: 磁偏差モデルが無い。8°W の固定は #135 でも採用していない。真方位で統一し、差分を README に書く。

### トレードオフ（Trade-offs）

- **メリット**: 既定は今日と同一。風を入れたときだけ GS・トラック・ATIS・最終進入のカニ行が見える。API は加算。
- **デメリット / 受容する制約**: 高度による風の変化は無い。ATIS の数字は真方位（日本の場内報・離着陸通報の磁方位とは違う）。待機の旋回とレグ時間は無風公式のまま。視程・QNH・突風は無い。使用滑走路は手動のまま。

### 非目標（Non-goals）

- 突風、VRB、鉛直シアー、高度別・空港別の風、乱気流、ウィンドシアー警報
- 地上風による使用滑走路の自動選定（(Ⅲ)１(６)）
- 磁偏差モデル、ATIS／タワー風の磁方位表示
- METAR 電文の取り込み、視程・雲・気温・QNH、進入方式名
- 待機の公式な時間補正、公示ホールディング、EAT／EFC
- 到着と出発で異なる使用滑走路、SID／STAR、離陸から初期上昇
- 針路指示中の自動カニ行（ベクトル指示は機首方位のまま）

---

## 完了条件（Success Criteria）

- [x] `spec/20261122-uniform-wind/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている。出典 URL が本文にある
- [x] 風が欠測のとき、ATIS 本文は現行の `WIND AND WEATHER NOT AVAILABLE.` のままである。既存形の `PUT`（`runwayInUse` のみ）は風を変えず、滑走路変更時の識別送りは今日と同じ
- [x] 風欠測（および CALM の運動）で、固定シナリオを N ティック進めた位置・針路・GS が、風モデル導入前と一致する（許容は既存テストと同じ）。無風の待機形状と `JOIN FINAL` の進入コースも今日と同一
- [x] リポジトリ内の Python（または同等の独立計算）が、PHAK の例を再現する。TAS 120 kt・針路 090°、風 270°/20 kt で GS 140 kt・トラック 090°、風 090°/20 kt で GS 100 kt。横風のトラック／WCA も同じ式で一致する（GS 0.1 kt、角度 0.1°）
- [x] IAS 指示では TAS 換算は現行のまま、GS だけが風で変わる。GS 指示では |GS| が目標になる（無風では現行どおり）
- [x] `JOIN FINAL` の要求形は変わらず、横風でも進入コースのトラックを保つ（進入後のオフコースが、WCA 無しに比べて有意に小さいことをテストする）。RJTT 05 は 400 のまま
- [x] 待機の POST 形は変わらない。無風では機影が既存オーバーレイに乗る。風ありではインバウンドレグのトラックがコースに沿う。レグ時間の公式補正は求めない
- [x] `GET /api/atis` に風の任意フィールドが付き、風を設定すると本文が `WIND … KNOTS.` または `WIND CALM.` になり、両空港の情報識別が進む。欠測へ戻せる。不正値は 400
- [x] 位置 API に任意の `vector.track` が付き、`vector.heading` は機首方位のまま。Frontend の速度ベクトルと直線予測（STCA／RBL CPA）はトラックと GS を使う
- [x] Frontend / Backend のテストが合格する
- [ ] CI の 4 ジョブが、マージするコミットの SHA で全部合格する
- [x] Backend README と Frontend README に、一様風、既定は欠測、真方位、GS／トラック、最終進入と待機の WCA、磁方位にしないこと、出典を書く。ルート README に変更があれば同じ PR に含める
- [x] `spec/spec.md` の 6-1 とギャップ台帳を、本スライスの範囲（一様風は入る、シアーや待機時間補正は残る）に合わせて更新する
- [ ] ブラウザ確認とスクリーンショット: サンプル開始直後は GS と ATIS が今日と同じ。Controller が例えば風 270°/20 kt を入れると、両空港の ATIS 本文と識別が更新され、向かい／追いの機体の GS が変わり、速度ベクトルがトラック方向を指す。16R への `JOIN FINAL` で機首はコースとずれつつ延長上に乗る。無風に戻すと欠測文と無風の GS に戻る

---

## 影響範囲

- **Backend**: 一様風のセッション状態、`FixedWingFlightBehavior`（および同じ位置計算を使う機体）の GS／トラック、TAS の保持、最終進入と待機レグの WCA、`AtisService` / `AtisTextComposer` / `UpdateAtisRequestDto`、位置 DTO の `vector.track`、`UranosAPI.yml`、関連テスト
- **Frontend**: ATIS パネルの風入力、速度ベクトルと CPA／STCA 予測のトラック利用、ATIS 型
- **README**: Backend / Frontend（ルートにあれば同様）
- **変えないもの**: Hold / `JOIN FINAL`／復行／タワー移管の要求形、`GET /ats/runways`、欠測時の ATIS 本文、磁偏差

---

## 実装計画

### Phase 1（Must-have）

1. 風ベクトルと GS／トラック／WCA の純関数、Python 対照、無風同値テストを先に置く。
2. ATIS の加算フィールドと本文、位置 DTO の `track`、運動への接続、パネルと速度ベクトルを入れる。
3. README と 6-1／台帳を更新する。

### Phase 2（このスライスの外）

- 待機の時間補正、高度別の風、磁方位表示、滑走路の自動選定
- 視程・QNH 等の残りの気象、SID／STAR、初期上昇

---

## 検証

- [x] 無風同値と PHAK 対照と ATIS 契約（既存 PUT を壊さないこと、風更新、400）が、未実装では失敗し、実装後は成功する
- [x] 横風の `JOIN FINAL` と、無風の待機オーバーレイをテストする
- [ ] ブラウザで欠測→風あり→欠測の往復、GS、速度ベクトル、ATIS、最終進入のカニ行を確認し、スクリーンショットを残す
- [ ] CI 4 ジョブがマージ SHA で合格する

---

## 未解決事項（Unresolved Questions）

- AIP Japan GEN 2.1 / GEN 1.7 の原文は未取得。磁方位表示を後続で足す場合は、公式本文を確認してから偏差モデルと一緒に扱う。
- 風速の上限 99 kt と、TAS を超える向かい風のときのクランプ方法は、純関数のテストで具体化する。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20261122-atis-runway-in-use/spec.md](../20261122-atis-runway-in-use/spec.md)
- [spec/20261017-indicated-airspeed-instruction/spec.md](../20261017-indicated-airspeed-instruction/spec.md)
- [spec/20261122-hold-racetrack-overlay/spec.md](../20261122-hold-racetrack-overlay/spec.md)
- FAA PHAK FAA-H-8083-25C: [目次](https://www.faa.gov/regulations_policies/handbooks_manuals/aviation/phak)、[Chapter 8](https://www.faa.gov/sites/faa.gov/files/regulations_policies/handbooks_manuals/aviation/phak/10_phak_ch8.pdf)、[Chapter 16](https://www.faa.gov/sites/faa.gov/files/18_phak_ch16.pdf)
- 気象庁: [航空気象通報式 第３版 第17号](https://www.jma.go.jp/jma/kishou/books/tsuhoshiki/koukuu/koukuu3_17.pdf)、[気象通報式](https://www.jma.go.jp/jma/kishou/books/tsuhoshiki/tsuhoshiki.html)
- 東京航空地方気象台: [WEATHER TOPICS 通巻 86 号](https://www.data.jma.go.jp/haneda-airport/weather_topics/file/rjtt_wt20210329.pdf)（場内報は磁方位、場外報は真方位）
- 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』: [JAPA 公開 PDF（2025-03）](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)
- FAA Order JO 7110.65BB Change 3: [2-9 ATIS](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap2_section_9.html)、[3-10-1](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap3_section_10.html)
- [Backend/README.md](../../Backend/README.md)
- [Frontend/README.md](../../Frontend/README.md)
