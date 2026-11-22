# 滑走路までの距離・時間と測距線（RBL）

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md)、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、[spec/20261017-runway-final-approach/spec.md](../20261017-runway-final-approach/spec.md)、[spec/20261122-msaw/spec.md](../20261122-msaw/spec.md)、[spec/20260318-range-rings-display/spec.md](../20260318-range-rings-display/spec.md)、[spec/20260318-data-block-display-items/spec.md](../20260318-data-block-display-items/spec.md)

## 概要

管制官画面（および同じレーダーキャンバスを使う Operator 画面）に、次の二つを入れる。最終進入コース上またはその近傍にいる到着機について、進入端までの沿コース距離（海里）と、現在対地速度による到達時間をデータブロックに出す。あわせて、レーダー上の 2 点（機体同士、または機体と任意点、または任意の 2 点）を結ぶ測距線（RBL）を、ツール切替で描けるようにする。線のラベルには水平距離（海里）と真方位を出し、両端が機体で安価に求められるときだけ最接近までの時間も出す。計算と表示書式の定義源は Frontend の単一モジュールに置く。位置 API は変更しない。

## 背景・課題

### 現状

- 位置・対地速度・針路は `GET /aircraft/location/all` の `AircraftLocationDto`（`position`、`vector.groundSpeed`、`vector.heading`、`destinationIcao`）で Frontend に届く。MSAW 用の `msawAlert` はあるが、進入端までの距離や測距のフィールドはない。
- 滑走路は `Backend/src/main/resources/fix/runways.json` に RJTT 16R（進入端 35.5530N 139.7810E、`heading` 163）と RJAA 16R のみ。`RunwayCatalog.buildFinalApproachGeometry` は進入端から会合点 8 NM、点線延長 10 NM を球面オフセットで作る。同じ幾何は `GET /ats/runways`（BFF は `GET /api/ats/runways`）で Frontend に届き、`radarCanvas` が起動時に取得して `drawRunways` で実線 RWY・点線延長を描く。TypeScript の `RunwayMapGeometry` は進入端・末端・延長端の緯度経度だけを型にしており、API が既に返している `airportIcao` / `designator` / `heading` / `approachCourseHeading` をまだ使っていない。
- MSAW の進入廊は、進入端から外側最大 10 NM、左右 1.0 NM（`msa-areas.json` の `approachCorridorExtensionNm` / `approachCorridorWidthNm`）。沿トラック距離は Backend `MsawDetector` 内で計算しているが、DTO には載せていない。
- レーダーにはレンジリング（中心からの同心円、間隔は NM）、速度ベクトル、ラベルのドラッグがある。`radarCanvas` の `mousedown` は、Fix 選択モードでなければ機体シンボル（半径 30 px）の選択と、ラベル矩形（概ね幅 70 px・高さ約 60 px）のドラッグ開始だけを扱う。測距の対話はない。
- 距離計算の既存実装は Backend `GeodeticUtils`（短距離は平面近似、50 NM 以上は地球半径 6378.1 km の Haversine）。Frontend に、2 点の海里距離・前方方位・到達時間を単一の定義源としたモジュールはない。
- 飛行力学と滑走路幾何は、`heading` を測地線方位として `offsetPointNm` や位置更新に渡している。磁気偏差のカタログや変換はない。滑走路 16R の 163 は実世界では磁方位に相当する番号だが、シミュレータ内ではその数値がそのまま測地計算に使われている。
- ギャップ台帳は「測距線と滑走路までの距離・時間」をレーダー画面の残りに置いている。レンジリング（2-2）と滑走路・最終進入は Done。MSAW も Done。

### 課題（Problem Statement）

- 到着間隔や最終進入の速度指示を訓練・研究で使うとき、管制官役が「いま進入端まで何海里・何分」をレーダー上の数値で確認できない。
- 2 機の間隔や、機体と任意点の方位・距離を、画面上で直接測る手段がない。レンジリングは中心からの距離だけで、任意 2 点には使えない。
- 日本の管制方式基準と FAA JO 7110.65 は、レーダー進入中に接地点または滑走路からの距離を海里で通報する手順を持つ。画面に残距離がなければ、その数値の読み取りを練習しにくい。ただし Horus の第 1 版は PAR/ASR の誘導シミュレータではなく、通常のターミナルレーダー表示の補助である。

### なぜ今か（Motivation）

- 滑走路幾何、最終進入、対地速度、データブロック、ラベルドラッグ、レンジリングが main に揃っている。次の実機差として、台帳が「レーダー画面の残り」に置いているのが測距線と滑走路までの距離・時間である。
- 必要な入力（位置、対地速度、進入端座標、10 NM の延長線）は既に Frontend にある。位置 API を増やさなくても、小さな表示スライスとして切れる。

---

## 方針

### 決定方針（Decision）

1. **計算の置き場所は Frontend。位置 API は変えない。**
   - 進入端座標と延長幾何は既に `GET /api/ats/runways` で届く。機体の緯度・経度・対地速度も位置 DTO にある。「ファイナル上または近傍」の判定に使う廊の長さ・幅は、既に描画している 10 NM 延長および MSAW と同じ 1.0 NM 半幅で足り、新しいサーバ状態は要らない。
   - RBL は画面上のクリックとドラッグのツールであり、サーバ往復を挟む理由がない。距離・方位・時間の式を RBL と進入端残距離で共有するには、Frontend に単一モジュールを置くのが自然である。
   - MSAW の `msawAlert` が Backend にあるのは、区域カタログと警報判定がサーバ所有だからである。本スライスは表示用の測距であり、その対比で API 追加は採用しない。

2. **進入端までの残距離（データブロック）**
   - 対象は、カタログ滑走路の進入側延長上またはその近傍にいる機体。廊の定義は MSAW と同じにする。進入端から外側へ沿コース 0 NM 超〜 10 NM 以下、左右横断 1.0 NM 以下。進入端より内側（出発末端側）や、廊の外では出さない。
   - 滑走路の選び方: 廊に入っているカタログ滑走路を使う。複数に同時に入ることは、現行の RJTT 16R と RJAA 16R では起きない。将来の複数滑走路に備え、入っている滑走路が複数なら沿コース距離が短い方を使う。目的地 ICAO の一致は必須にしない（誘導中の到着機を落とさないため）。
   - 距離は進入端までの**沿コース（投影）距離**とする。斜距離や大圏距離そのものではない。わずかにコースを外れていても、「接地点／滑走路からの何海里」に相当する残距離として使える。Horus はグライドパスと接地点（グライドパスと滑走路の交点）を持たないため、基準はカタログの**進入端**とする。用語は「進入端までの距離」であり、公式の接地点距離そのものではないことを README に書く。
   - 時間は、その沿コース距離を現在対地速度で割った値（風なし、減速なし、針路が完全にコースと一致している前提の概算）。対地速度が 1 kt 未満、または時間が 60 分以上なら時間は出さない。
   - 表示はデータブロックの対地速度／目的地行の次に 1 行。書式の定義源は下記モジュール。例: `16R 8.2NM 3:00`。行が増える分、ラベルのドラッグ判定矩形も高くする（既存の固定高さのままでは新しい行を掴めない）。
   - 最終進入コース上への距離チケットや、PAR 風の毎海里通話そのものは範囲外。画面の数値は、管制官役が必要なら用語に載せるための補助である。

3. **測距線（RBL）**
   - Controller / Operator 共通のレーダーに、レンジリング設定の近くへ「RBL」ツールボタンを置く。
   - **ツールがオンのときだけ**点を取る。1 回目のクリックで始点、2 回目で終点を確定し、線を残す。第 1 版は同時に 1 本。確定後にまた 2 点を取ると、前の線を置き換える。
   - 端点は、既存の機体シンボル判定（半径 30 px）に入ればその機体にスナップし、位置に追従する。そうでなければ `CoordinateManager.calculateGeoCoordinates` で緯度経度に直し、地理座標として固定する（パン・ズーム後も同じ地点を指す）。フィックスへのスナップは、既存の `searchFixName` を流用できるなら Must に含めてよいが、無くても完了条件は満たせる（Should）。
   - 線の中点付近にラベルを出す。距離（海里、小数 1 桁）と、始点から終点への**前方真方位**（3 桁、末尾 `T`）。両端が機体のときだけ、水平面・等速直線運動を仮定した最接近時間（CPA）を付ける。高度は見ない。離反（CPA が 0 秒以下）または 60 分以上なら時間は出さない。STCA の代替ではない。
   - **解除**: ツール横の解除ボタンで線を消す。Escape は、取り途中なら始点だけを取り消し、確定済みなら線を消す。ツールをオフにしても、解除するまで線は残してよい（オフ中のクリックは通常の選択・ラベルドラッグに戻る）。
   - **ラベルドラッグとの干渉禁止**: ツールがオンのあいだは、ラベル矩形への `mousedown` でドラッグを開始しない。ツールがオフのときのラベルドラッグ・機体選択は、本スライスの前後で同じである。Shift などの修飾キー専用操作は採用しない（押し忘れと、ドラッグ開始条件の複雑化を避ける）。

4. **方位は真方位（測地前方方位）とし、磁気偏差は入れない。**
   - 実世界の管制指示の針路は磁方位である。管制方式基準 (Ⅳ)12(4) は精測レーダー進入中の針路を「磁針路」と書き、14 CFR 97.5 も計器方式の方位を原則磁方位とする。
   - しかし Horus の針路・滑走路 `heading`・速度ベクトルは、磁気偏差を掛けずに測地計算へ渡している。ここに RJTT の実世界偏差だけを RBL へ掛けると、画面の測距方位と、既存の針路指示・滑走路方位がずれる。
   - よって第 1 版の RBL 方位は真方位とし、ラベルに `T` を付けて磁方位と混同しない。RJTT / RJAA の偏差モデルは、飛行力学側と同時に入れる後段とする。

5. **計算と書式の単一定義源**
   - 新規モジュール（推奨パス: `Frontend/utility/measure/rangeBearing.ts`）に、球面距離（Haversine）、前方方位、沿コース／横断距離、廊判定、到達時間、CPA、表示書式を置く。`drawAircraft` と RBL 描画はこれを import するだけにする。
   - 地球半径は Backend と同じ 6378.1 km、1 NM = 1.852 km（半径 3443.8984881210 NM）。短距離で平面近似へ切り替えない（Backend `GeodeticUtils` の 50 NM 閾値は踏襲しない）。テストは製品コードを import し、下表の独立計算値と照合する。

6. **根拠となる一次資料（確認したものに限る）**
   - **日本（現行手続き）**: 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』（日本航空機操縦士協会が 2025-03 に公開した版。改正番号 145、国空制第 591 号、令和 7.3.6 改正、令和 7.3.20 適用）。
     - 用語「接地点」: 「精密進入のグライドパスと滑走路の交点をいう。」
     - (Ⅳ)12(5)【接地点からの距離】: 「最終進入中の航空機に対しては、接地点からの距離を１海里につき１回以上通報するものとする。」用語「接地点から〔数値〕海里」「〔number〕MILE/S FROM TOUCHDOWN.」
     - (Ⅳ)11 の捜索レーダー進入は基準が滑走路で、「滑走路から〔数値〕海里」「〔number〕MILE/S FROM RUNWAY」および「〔number〕MILES FROM RUNWAY, BEGIN DESCENT.」を使う。
     - これらはレーダー進入（精測／捜索）の通報義務であり、通常の ILS / RNAV 到着に同じ毎海里通報を義務づける条文としては読んでいない。Horus 第 1 版は、その数値を画面で読めるようにする補助である。
   - **日本（表示例）**: 国土交通省航空局交通管制部『航空保安業務の概要（2026）』。TEPS（航空路）の表示例に「対地速度＋後方乱気流区分／目的空港／残距離表示」とある。同じ資料の TAPS（空港／ターミナル）表示例は、便名、現在高度／上昇下降／承認高度、対地速度／後方乱気流区分／目的空港、ターゲットシンボル／間隔測点であり、残距離は列挙されていない。したがって「TAPS のデータブロックが残距離を出す」とは、この公開資料からは言わない。本スライスの根拠は、上記の通報手順と、ギャップ台帳が挙げた訓練上の不足である。
   - **FAA**: Order JO 7110.65BB Change 3（効力 2026-07-09）。
     - 5-9（Radar Arrivals）には、PAR のような「接地点から毎海里」の一般規定はない。5-9-2 はゲート内側でコースを外れたときに `(Ident) (distance) MILE(S) FROM THE AIRPORT, ... SAY INTENTIONS.` を使う。
     - 5-11-5（Surveillance Approaches）: 「Inform the aircraft of its distance from the runway, airport/heliport, or MAP, as appropriate, each mile on final.» 用語 `(Number) MILE(S) FROM RUNWAY/AIRPORT/HELIPORT OR MISSED APPROACH POINT.`
     - 5-12-5（PAR）: 「Inform the aircraft of its distance from touchdown at least once each mile on final approach.» 用語 `(Number of miles) MILES FROM TOUCHDOWN.`
   - **EUROCONTROL**: *Continuous Descent Approach — Implementation Guidance Information*（2008-05）。PANS-ATM（Doc 4444）8.6.4.2 d を引用し、精密／捜索レーダー進入向けの `"(callsign) (distance) MILES FROM TOUCHDOWN"` は、進入の他の段階でも使えるとする。誘導中は DTG（distance-to-go）の通報を適宜更新するよう書いている。Doc 4444 本文そのものは有償のため、本 spec ではこの引用を確認経路とする。
   - **ATS の測距ツール**: SKYbrary *Range and Bearing Tool*。状況表示上の 2 点（画面上の点、ウェイポイント、航跡）を結び、最低限は距離と方位を線上のラベルで出す。移動体があれば時間の見積もりが付くことも多い。スナップ付き。手動ツールであり、CPA の読み取りには限界がある。STARS / ERAM の公式の運用マニュアル（JO 6191 系は整備ハンドブックであり、管制官 HMI の操作手順書ではない）は、公開の faa.gov 資料としては確認できなかった。VATSIM 等の再現ドキュメントは一次資料にしない。

### 検討した他案（Alternatives Considered）

- **案 A: 残距離を Backend で計算し、`msawAlert` と同様に位置 DTO へ加算する。** 採用しなかった理由: 必要な入力は既に Frontend にあり、RBL と式を共有できない。ポーリング間隔に表示が縛られる。API を増やさず既存の動きを変えない、という制約にも反しやすい。
- **案 B: 残距離だけ先に出し、RBL は次スライスにする（またはその逆）。** 採用しなかった理由: 距離の式と書式が同じ定義源になる。両方を入れても画面対話と 1 モジュールに収まり、台帳の 1 項目を一度に閉じられる。
- **案 C: 残距離を大圏距離（進入端までの直線）だけにする。** 採用しなかった理由: コースをわずかに外れた到着機で、通報で使う「コースに沿った何海里」とずれる。沿コース距離は MSAW が既に使っている考え方でもある。
- **案 D: RBL を修飾キー（Shift+ドラッグ等）だけにする。** 採用しなかった理由: ラベルドラッグの開始条件と衝突しやすい。ツール切替なら、オンのあいだはドラッグを始めない、と明確に分けられる。
- **案 E: 磁方位を出し、RJTT の偏差を固定値で掛ける。** 採用しなかった理由: 飛行力学と滑走路幾何が偏差を使っていないため、RBL だけ磁方位にすると既存の針路指示と矛盾する。
- **案 F: 両機 RBL の CPA 時間を出さない。** 採用しなかった理由: 水平・等速の式は同じモジュールに数関数で足り、ユニットテストで固定できる。ただし STCA の代替にしないこと、高度を見ないことを明示する。速度ベクトル先端同士の測距は出さない（SKYBrary が挙げる誤読しやすい使い方のため）。

### トレードオフ（Trade-offs）

- **メリット**: API を増やさず、既存の滑走路幾何と位置 DTO だけで残距離と測距が揃う。計算と書式が 1 箇所に固定される。ラベルドラッグの既存操作を、ツールオフ時は温存できる。
- **デメリット / 受容する制約**:
  - 残距離の基準は進入端であり、規程上の接地点（グライドパス交点）そのものではない。
  - 時間は現在対地速度の単純割りであり、減速・風・旋回は見ない。
  - RBL 方位は真方位である。磁方位が必要な訓練は、偏差モデルの後段まで待つ。
  - 第 1 版の RBL は 1 本。TEPS 型の「目的空港までの残距離」や、PAR/ASR そのものの誘導は範囲外。
  - 公開資料の TAPS 表示例に残距離は出ていない。本スライスを「TAPS 互換の残距離行」とは呼ばない。

### 非目標（Non-goals）

- 位置 DTO や OpenAPI の変更、Backend の新しい測距エンドポイント
- 磁気偏差、磁方位表示、コンパスローズ
- グライドパス、接地点座標、PAR/ASR の誘導と毎海里の自動通話
- 公式 MVA / AIP の距離情報、風、減速プロファイル
- STCA 発報時の自動測距線、速度ベクトル先端同士の測距
- 同時に複数の RBL、フィックススナップの必須化（Should）
- データブロック全体の TAPS 寄せ（承認高度の定位置化などは別項目）

---

## 完了条件（Success Criteria）

- [x] `spec/20261122-runway-distance-rbl/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている
- [x] 位置 API の既存フィールドと既存エンドポイントの動きが変わっていない（本スライスで DTO を足さない）
- [x] `Frontend/utility/measure/rangeBearing.ts`（パスは同等なら可）が距離・方位・時間・書式の単一定義源であり、描画コードはそれを import する
- [x] Frontend の Jest が製品コードを import し、次の独立計算値と照合する（期待値表だけをテストへコピーして照合しない）。地球半径 6378.1 km、1 NM = 1.852 km の球面 Haversine と前方方位。Python 3.13 で同じ式を独立に計算した値。WGS84（geographiclib `Geodesic.WGS84`）との差は参考であり、テストの期待値には使わない（8 NM の例で約 0.023 NM / 0.07°、RJTT–RJAA で約 0.015 NM / 0.09°）。許容差は距離 0.001 NM、方位 0.01°、時間 0.05 s。
  - **距離・方位 1**: RJTT 16R 進入端 (35.5530, 139.7810) から真方位 343°、8 NM の点は (35.6802700700, 139.7330940835)。往路距離 8.0000000000 NM、前方方位 343.000000°。復路の前方方位は 162.972102°（子午収束のため 163 ちょうどにはならない）。
  - **距離・方位 2**: 同進入端から RJAA 16R 進入端 (35.7647, 140.3864) まで 32.1880525766 NM、前方方位 66.537584°。逆方向 246.890510°。
  - **到達時間**: 8.0 NM / 160 kt = 180.000000 s（表示 `3:00`）。5.0 NM / 180 kt = 100.000000 s（`1:40`）。10.0 NM / 240 kt = 150.000000 s（`2:30`）。1.0 NM / 140 kt = 25.714286 s（`0:26`）。対地速度 0 または 60 分以上は時間なし。
  - **廊判定**: 進入端から沿コース 8 NM・横断 0 の点は表示対象。沿コース 8 NM・横断 2.0 NM の点、および進入端より内側（出発側）の点は対象外。沿コース 10.0 NM ちょうどは対象、10.0 NM 超は対象外。横断 1.0 NM ちょうどは対象。
  - **CPA（両機 RBL）**: 機 A を進入端、針路 090、180 kt。機 B を A から真方位 090°・10 NM の点 (35.5528273730, 139.9854901850)、針路 270、180 kt。距離 10.000 NM、A→B 前方方位 090.000°、水平 CPA は 100 s（0.05 s 以内）。同じ位置で両方 090 なら離反または CPA なし。
- [x] 書式: 距離は小数 1 桁 + `NM`、真方位は 3 桁ゼロ埋め + `T`、時間は `m:ss`。進入端行は `16R 8.2NM 3:00` の形。RBL は `12.4NM 087T`、両機で CPA があるときだけ時間を後ろに付ける
- [ ] RBL ツールがオンのとき、ラベルドラッグが始まらない。オフのとき、従来どおりラベルをドラッグでき、機体シンボルの選択もできる — Tool-off label drag verified in Playwright; tool-on drag suppression not separately exercised in browser.
- [ ] スナップした端は機体に追従し、任意点の端は緯度経度で固定される。解除ボタンと Escape で、取り途中または確定済みの線を消せる。同時表示は 1 本 — Clear button and one-line RBL verified; Escape and live aircraft-follow snap not separately browser-checked.
- [ ] ラベル行追加後も、新しい行を含む矩形でドラッグできる — Label drag worked with the extra row present; dedicated hit-test of the new threshold line was not isolated.
- [x] Backend のテストが全件合格する（本スライスで Backend を変えないなら、既存の合格を維持する）
- [x] Frontend のテストとビルドが全件合格する
- [x] CI の 4 ジョブ（変更検出、Backend、Frontend、Docker Compose）が、マージするコミットの SHA で全部合格する
- [x] ルート README に変更があれば同じ PR に含める。Frontend README は残距離行・RBL 操作・進入端が接地点そのものではないこと・真方位であることを書く。Backend を変えない場合、Backend README の更新は不要
- [x] `spec/spec.md` と `spec/20261017-terminal-radar-gaps/spec.md` の「測距線と滑走路までの距離・時間」が、実装後の状態に更新されている
- [ ] ブラウザでの手動検証が完了している（PdM またはテックリードが、対象コミットで次を確認する）: Playwright covered (a)(b)(d RBL)/(e clear+tool-off drag); (c) off-corridor hide, (d) tool-on no-drag, (f) on-screen CPA, (g) STCA/MSAW/EMRG not separately checked.
  - (a) トップの START WITH SAMPLE から開始する
  - (b) 到着機（例: ANA101）を RJTT 16R の最終進入延長へ載せ、データブロックに `16R` と海里・時間が現れ、進入端に近づくにつれて距離が減る
  - (c) 同じ機をコースから明らかに外す（横断が 1 NM を超える）か進入端を通過させると、残距離行が消える
  - (d) Controller で RBL をオンにし、2 機（または 1 機と空白点）をクリックして線と `NM` / `T` が出る。ツールオンのままラベルを掴んでもラベル位置は動かない
  - (e) RBL をオフにすると、従来どおりラベルをドラッグできる。線は解除まで残ってもよい。解除または Escape で線が消える
  - (f) 対向に近い 2 機で CPA 時間が出ること、または離反では時間が出ないこと、を 1 組確認する
  - (g) STCA・MSAW・緊急スコーク・レンジリング・最終進入の点線が、従来どおり見える

---

## 影響範囲

- **Frontend/utility/measure/rangeBearing.ts**（新規）: 計算と書式の定義源、および Jest
- **Frontend/utility/aircraft/drawAircraft.ts**: 進入端行の描画（定義源を import）
- **Frontend/components/radarCanvas.tsx**: RBL のツール状態、クリック、描画、ラベルドラッグの抑止、ヒット矩形の高さ
- **Frontend/utility/AtsRouteManager/routeRenderer.ts**: `RunwayMapGeometry` に API 既存フィールド（`airportIcao`、`designator`、`heading` または `approachCourseHeading`）を足す。描画色や延長の長さは変えない
- **Frontend の設定 UI**: RBL のオン／オフと解除（レンジリング設定の近く）。新規 Context でも、キャンバスローカル状態でもよい
- **spec/spec.md**、**spec/20261017-terminal-radar-gaps/spec.md**、**Frontend/README.md**（および変更があればルート README）
- **Backend / 位置 API / `UranosAPI.yml`**: 変更しない

---

## 実装計画

### Phase 1（Must-have）

1. 測距モジュールと、上記の数値ケースを製品コードに対して固定するテストを先に置く。
2. `RunwayMapGeometry` を API の既存フィールドに合わせ、進入廊判定と沿コース距離をモジュールへ置く。
3. データブロックへ進入端行を出し、ラベルのヒット矩形を行数に合わせる。
4. RBL ツール（オン／オフ、2 クリック、1 本、スナップ、真方位ラベル、解除、Escape、ツールオン時はラベルドラッグ禁止）を `radarCanvas` に入れる。両機のときだけ CPA 時間を出す。
5. README とロードマップ／台帳を、実装と同じ PR で更新する。

### Phase 2（このスライスの外）

- フィックス／ナビエイドへのスナップ（Should として Phase 1 に含めてもよい）
- 複数 RBL、磁偏差と磁方位、接地点（グライドパス交点）への切り替え、TEPS 型の目的地残距離

---

## 検証

- [x] Frontend のユニットテストが、上表の数値を製品コードに対して満たす
- [x] `npx jest --listTests` に新規テストが含まれ、未実装では失敗し、実装後は成功する
- [x] `npm test` と `npm run build`（およびリポジトリが定める Frontend 静的チェック）が通る
- [x] Backend テストは、コードを変えないなら既存の合格を維持する
- [ ] 完了条件のブラウザ手順 (a)–(g) を、マージするコミットで確認する — Same partial Playwright coverage as the Success Criteria browser item above; (c)(f)(g) and tool-on no-drag not fully checked.

---

## 未解決事項（Unresolved Questions）

- フィックスへのスナップを Phase 1 に含めるか。`searchFixName` が既にあるので実装コストは小さいが、Must にはしていない。
- 残距離行の表示トグルをデータブロック設定に足すか。第 1 版は廊内なら常時表示とし、隠す必要が出たら後段とする。
- Operator 画面でも RBL と残距離を出すか。キャンバスが共通なので出すのが自然であり、本 spec は両方に出す。片方だけに限る理由が出たら、実装前にこの項を更新する。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/spec.md](../spec.md)
- [spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)
- [spec/20261017-runway-final-approach/spec.md](../20261017-runway-final-approach/spec.md)
- [spec/20261122-msaw/spec.md](../20261122-msaw/spec.md)
- 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』（管制方式基準を含む）: [JAPA 公開 PDF（2025-03）](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)（接地点の定義、(Ⅳ)11 捜索レーダー進入、(Ⅳ)12(5) 接地点からの距離。改正 145 / 国空制第 591 号、令和 7.3.20 適用）
- 国土交通省航空局交通管制部『航空保安業務の概要（2026）』: [PDF](https://www.mlit.go.jp/koku/content/002013001.pdf)（TEPS 表示例の残距離、TAPS 表示例には残距離の列挙なし）
- FAA Order JO 7110.65BB Change 3（効力 2026-07-09）: [現行号の案内](https://www.faa.gov/regulations_policies/orders_notices/index.cfm/go/document.current/documentnumber/7110.65)、[5-9 Radar Arrivals](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_9.html)、[5-11 Surveillance Approaches](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_11.html)（5-11-5）、[5-12 PAR Approaches](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_12.html)（5-12-5）
- EUROCONTROL, *Continuous Descent Approach — Implementation Guidance Information*（2008-05）: [SKYBrary PDF](https://skybrary.aero/sites/default/files/bookshelf/2846.pdf)（PANS-ATM 8.6.4.2 d の引用と DTG）
- SKYBrary, *Range and Bearing Tool*: [https://skybrary.aero/articles/range-and-bearing-tool](https://skybrary.aero/articles/range-and-bearing-tool)
- 14 CFR 97.5（計器方式の方位は原則磁方位。本スライスで磁方位を採用しない理由の対照）: [eCFR](https://www.ecfr.gov/current/title-14/chapter-I/subchapter-F/part-97/subpart-A/section-97.5)
- [Frontend/README.md](../../Frontend/README.md)
- [Backend/README.md](../../Backend/README.md)
