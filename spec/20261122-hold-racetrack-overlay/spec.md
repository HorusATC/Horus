# 待機の標準率レーストラックと指示経路の描画

## メタデータ

- **Status**: Done
- **Author**: Kazuki Futawatari
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md) フェーズ 3-2、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、[spec/20261122-hold-inbound-leg-turns/spec.md](../20261122-hold-inbound-leg-turns/spec.md)、[spec/20260509-phase3-holding-pattern/spec.md](../20260509-phase3-holding-pattern/spec.md)

## 概要

#129 で待機指示の入方向・レグ・左右旋回は指定できるようになったが、飛行はフィックスと軸上の出方向点を往復するシャトルであり、標準率旋回のレーストラックではない。指示された待機経路もレーダーに描かれない。本スライスでは、無風のもとで機体を標準率（原則 3°/秒）の 180° 旋回と平行な入・出方向レグで周回させ、同じ幾何をレーダーに重ね描きする。公示経路、進入セクター、EAT／EFC、風修正は範囲外とする。`POST /api/aircraft/{callsign}/hold` の契約は変えない。レーダーが経路を描くために、位置 API へ任意の `holding` オブジェクトを加算する。

## 背景・課題

### 現状（main `d43e1ca` で確認）

- Hold API は `inboundCourseDeg` / `legMinutes` / `legNm` / `turnDirection` を受け付ける。時間レグの出方向距離は、各出方向開始時の対地速度 ×（分 / 60）である。省略時は右旋回・4.0 NM・Fix 名ハッシュ入方向（運用コースではない）。
- `AircraftBase` は、フィックス到着まで直行し、その後フィックスと `holdingOutboundTarget` を切り替えて往復する。出方向目標は入方向 +180° の軸上にあり、旋回半径ぶんのオフセットが無い。左右の強制旋回はあるが、目標が軸上のため経路はレーストラックにならない。
- 民間機の `maxTurnRate` は既に 3.0°/秒。戦闘機は 9°/秒、ヘリは 6°/秒で、待機中もこの上限のままである。
- 位置 DTO（`AircraftLocationDto`）に待機幾何は無い。データブロックは `HLD` を出すだけである。レーダーに指示経路のオーバーレイは無い。
- #129 の既知の残り: シャトル飛行、経路の非描画、公示ホールディング、進入セクター、EAT／EFC。

### 課題（Problem Statement）

- ターミナルレーダーでは、待機機の間隔と遅延はレーストラックの形と占有で判断する。軸上の往復では、指示したコースとレグが画面上の周回と一致せず、#129 の指示要素を研究・訓練に使いにくい。
- Controller は Hold を出さない（Operator が出す）。経路を描くには、位置ストリームに指示幾何が乗っている必要がある。

### なぜ今か（Motivation）

- 指示パラメータは揃った。次に足りないのは、規程が前提とする周回そのものと、管制官が見る経路である。風や公示カタログ、進入セクターより、既存の Hold 実装の飛行と描画に閉じられる。
- 無風・セクターなしに限れば、1 PR に収まる。

---

## 方針

### 決定方針（Decision）

1. **根拠となる一次資料**
   - **日本（指示側）**: 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』（JAPA 2025-03 公開、改正 145、国空制第 591 号、令和 7.3.20 適用）(Ⅱ)６(１)ｂ。待機指示は関係方位、フィックス、入方向経路、出方向距離または分単位の飛行時間、旋回方向を含む。飛行方式（旋回率やレーストラックの幅）は同項に書かれていない。
   - **FAA 管制側**: Order JO 7110.65BB Change 3（効力 2026-07-09）[4-6 Holding Aircraft](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap4_section_6.html)（4-6-1、4-6-4）。指示要素は #129 と同じ対照であり、本スライスでは変えない。
   - **FAA 飛行側（旋回とレグ）**: AIM [5-3-8 Holding](https://www.faa.gov/air_traffic/publications/atpubs/aim_html/chap5_section_3.html)（公式 HTML）。RNAV 横誘導を使わない待機では、進入中および待機中の旋回を次のいずれか、**バンクが最も小さくなるもの**で行う。(1) 3°/秒、(2) バンク 30°、(3) フライトディレクター使用時はバンク 25°。本スライスは (3) を実装しない（FD をモデル化しない）。同趣旨は [FAA AIP ENR 1.5](https://www.faa.gov/air_traffic/publications/atpubs/aip_html/part2_enr_section_1.5.html) にもある。
   - **時間レグの取り方（FAA AIP ENR 1.5）**: 14,000 ft MSL 以下は 1 分、超えは 1.5 分（インバウンドの所定時間）。最初の出方向は同じ時間だけ飛び、以後の出方向はインバウンドが所定時間になるよう調整する。出方向の計時はフィックス上空またはアビームの遅い方から始め、アビームが取れなければ出方向への旋回完了時に始める。DME／距離指定では、出方向レグの長さを海里で測る。無風・速度一定なら入と出の時間は一致するので、本スライスでは出方向の事後調整をしない。
   - **ICAO**: Doc 8168 (PANS-OPS) は国際的な待機方式（レーストラック）の出典だが、本文は有償のため段落番号を置かない。Doc 4444 は管制間隔・指示の側であり、本スライスの旋回幾何の定義源にはしない。
   - **AIP Japan**: JAPA E-journal Vol.045 が ENR 1.5-1.5.1 を引用するのは「公示が無く指示も無いときの標準待機」であり、旋回率の条文は今回確認していない。AIS 原文も未取得。旋回率は AIM 5-3-8／FAA AIP ENR 1.5 に依る。
   - 方位は既存シミュレータと同じ測地フレーム（磁偏差なし）。無風のため、旋回半径の速度は対地速度を対気速度の代用とする（高度による TAS 換算はしない）。

2. **飛行: 軸上シャトルを、標準率のレーストラックに置き換える。**
   - フィックス到着前は現行どおりフィックスへ向かう（ダイレクト／パラレル／ティアドロップのセクター進入は非目標）。
   - フィックスを最初に通過したら、指示旋回方向へ、待機用旋回率で 180° 旋回し、出方向針路（入方向 +180°）にロールアウトする。
   - 出方向レグ: 距離指定なら、ロールアウト後に指示海里だけ進んでから入方向旋回を始める。時間指定なら、アビーム（またはフィックス上空／ロールアウト完了の、FAA AIP にならった開始点）から指示時間だけ飛んで旋回を始める。#129 のように時間を事前に軸上の点へ換算してその点へ向かう方式はやめる。
   - 入方向旋回 180° ののち、入方向コースでフィックスへ向かい、通過したら再び出方向旋回する。
   - **待機用旋回率** ω = min(3°/秒, バンク 30° の旋回率)。バンク 30° の旋回率は ω_rad/s = g tan(30°) / v、v は対地速度、g = 9.80665 m/s²、1 kt = 1852/3600 m/s。機体特性の `maxTurnRate` がこれより小さくても、待機中は上記を使う（民間機の 3°/秒はそのまま。戦闘機 9°/秒やヘリ 6°/秒は待機中に落とす）。
   - 旋回半径 r [NM] = v_nm/s / ω_rad/s = `groundSpeedKt / (20 × π × ω_deg_per_s)`。右旋回なら、入方向に飛ぶ機体から見て右側（方位 入方向+90°）に幅 2r の出方向レグが平行に置かれる。左旋回はその鏡像。
   - Resume は現行どおり待機を終了する。

3. **API**
   - `POST /api/aircraft/{callsign}/hold` は変更しない（フィールドも省略時互換もそのまま）。
   - 位置 API（`GET` の航空機位置、既存の `AircraftLocationDto`）に任意フィールド `holding` を加算する。`navigationMode` が `HOLDING` のときだけ出す。非待機機では省略する（`@JsonInclude.NON_EMPTY` と整合）。
   - `holding` は少なくとも次を含む。定義源は Backend の幾何コード 1 か所とする。
     - 指示: `fixName`、フィックス緯度経度、`inboundCourseDeg`、`turnDirection`、`legMinutes` または `legNm`（指示どおり一方）、解決済みの `outboundLegNm`
     - 旋回: `turnRateDegPerSec`、`turnRadiusNm`（当該機の現在対地速度で計算）
     - 描画用頂点 `pattern`: 閉じたレーストラックの緯度経度列（直線は両端、半円は少なくとも 8 分割）。Frontend は再計算せずこの列を描く。
   - 既存フィールドの意味は変えない。Frontend の `AIRCRAFT_LOCATION_DTO_FIELD_PARTITION`（`Frontend/utility/api/location.ts`）と `UranosAPI.yml`、契約テストを更新する。

4. **レーダー描画**
   - Controller と Operator のレーダーで、`holding` を持つ機体について指示経路を重ねる。RBL やレンジリングと見分けられる色・線種にする。ラベルのドラッグや既存の `HLD` 表示は変えない。
   - 本スライスでは表示のオン／オフ切替は Must にしない（待機機に常時出す）。TAPS／TEPS が同等のオーバーレイを持つとは主張しない。訓練・研究用の指示経路表示である。

5. **テストで固定する数値（製品コードを呼ぶ）**
   - 180 kt、3°/秒: r = 180 / (20π × 3) = 3/π ≈ **0.9549 NM**。バンク 30° の旋回率はこの速度では 3°/秒より大きいので、3°/秒が選ばれる。
   - 入方向 090°、右旋回、1.00 分、180 kt: 出方向レグ長 3.00 NM。最初の 180° 旋回後、機体はフィックスから方位 180°、距離 2r ≈ **1.9099 NM** にあり、針路は 270°。
   - 同じ指示の左旋回は、方位 000°、距離 2r の鏡像。#129 の「左右で出方向点が同じ」テストは、この鏡像関係に置き換える。
   - 250 kt ではバンク 30° が 3°/秒より遅い（約 2.52°/秒）。そのときの ω と r をユニットテストで固定する。
   - 許容差は距離 0.05 NM、方位 1°、針路 2°。シミュレーション刻みで 180° 旋回時間（3°/秒なら 60 秒）を機械的に検証する。
   - 位置 API: 待機中は `holding.pattern` が閉じ、フィックスと出方向端が上記幾何と一致する。非待機では `holding` が無い。

### 検討した他案（Alternatives Considered）

- **案 A: リーダー線の自動配置を先にする。** 採用しなかった理由: 画面は整うが、#129 の既知の残り（経路が指示と一致しない）は残る。ラベルは既に手動でドラッグできる。
- **案 B: 風なしの「使用滑走路」だけを先に切る。** 採用しなかった理由: `JOIN FINAL` は既に滑走路を明示する。風・ATIS なしの使用滑走路は、既定値を切り替える運用設定に近く、待機の視覚的・手順的ギャップより研究上の急ぎが弱い。風モデルと同時にやる方が一貫する。
- **案 C: 進入セクター（ダイレクト／パラレル／ティアドロップ）まで入れる。** 採用しなかった理由: レーストラック自体がまだシャトルのままだと、進入後の周回も検証しにくい。セクターは次の候補に残す。
- **案 D: 経路描画だけ行い、飛行はシャトルのままにする。** 採用しなかった理由: オーバーレイと航跡がずれる。研究者が見る価値が落ちる。
- **案 E: 位置 API を変えず、Hold を出した Operator だけが経路を覚える。** 採用しなかった理由: Controller に経路が出ない。

### トレードオフ（Trade-offs）

- **メリット**: 指示した待機が、管制官とパイロットのレーダーで同じレーストラックとして見える。間隔やレグ時間の議論が、軸往復ではなく規程どおりの形でできる。
- **デメリット / 受容する制約**: 最初の進入はフィックス直行のまま（セクターなし）。風がなく、インバウンド時間の事後調整も無い。高度に対する TAS 換算が無い。位置 DTO への加算は、Frontend の網羅的フィールド分割の更新を要する。バンク 25°（FD）と待機速度制限は入れない。

### 非目標（Non-goals）

- 公示待機カタログと `AS PUBLISHED`
- ダイレクト／パラレル／ティアドロップの進入セクター
- EAT／EFC／遅延時間未定
- 風修正、磁偏差、高度による TAS 換算
- フライトディレクター時の 25° バンク、待機最大速度
- Hold 指示 API の変更、Controller からの Hold
- SID／STAR、使用滑走路・ATIS、セッション記録

---

## 完了条件（Success Criteria）

- [x] `spec/20261122-hold-racetrack-overlay/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている
- [x] `POST /api/aircraft/{callsign}/hold` の要求・応答・省略時互換が #129 のままである（このエンドポイントのフィールド追加も削除もしない）
- [x] 位置 API の変更は任意フィールド `holding` の加算に限る。非待機機の JSON に `holding` が出ない。既存フィールドの意味は変わらない。`UranosAPI.yml` と契約テストがある
- [x] ユニットテストが製品コードで、上記の r = 3/π NM（180 kt, 3°/秒）、右旋回 090°／1 分の 2r オフセット、左旋回の鏡像、250 kt での 30° バンク制限を固定する
- [x] フィックス通過後のシミュレーションテストで、180° 標準率旋回のあと出方向針路にロールアウトし、時間または距離の出方向レグののち入方向へ旋回し、フィックスへ戻る。軸上の往復（出方向目標が入方向軸上だけ）になっていない
- [x] 戦闘機または `maxTurnRate` が 3°/秒を超える特性でも、待機中の旋回率は本スライスの式に従うことをテストする
- [x] Controller と Operator のレーダーで、待機機の `holding.pattern` が指示どおりのレーストラックとして見える。Resume 後は経路が消える
- [x] Frontend の位置 DTO 分割（`location.ts`）を更新し、Frontend のテストとビルド、Backend のテストが合格する
- [ ] CI の 4 ジョブが、マージするコミットの SHA で全部合格する
- [x] Backend README と Frontend README に、待機が標準率レーストラックであること、経路が表示されること、位置 API の `holding`、残る非目標（セクター・公示・風）を書く。ルート README に変更があれば同じ PR に含める
- [x] `spec/spec.md` の 3-2 とギャップ台帳を更新し、公示パターン・進入セクター・EAT／EFC が残課題であることを残す
- [x] ブラウザ確認: サンプルを開始し、Operator で到着機を既知の Fix に入方向・1 分レグ・右旋回で Hold させる。フィックス到着後、機体が半円を描いて平行な出方向レグへ入り、レーダーに同じ形の経路が見える。左旋回でも鏡像になる。Resume で飛行も描画も終わる

---

## 影響範囲

- **Backend**: `HoldingPatternGeometry`（半径・頂点・旋回率）、`AircraftBase` の待機飛行、`AircraftLocationDto` と `GetAllAircraftLocationsWithRiskUseCase`、`UranosAPI.yml`、幾何・飛行・位置のテスト。Hold 要求 DTO／コントローラは変えない
- **Frontend**: `utility/api/location.ts`（型・分割・マッピング）、レーダー描画（`drawAircraft.ts` / `radarCanvas.tsx`）、関連テスト
- **README**: Backend / Frontend（ルートにあれば同様）
- **spec/spec.md**、ギャップ台帳、#129 子 spec への残課題の追記
- **Hold 指示 API**: 変更しない

---

## 実装計画

### Phase 1（Must-have）

1. 旋回率・半径・レーストラック頂点の計算を 1 か所に置き、数値テストを先に書く。
2. フィックス到着後の飛行を、出方向旋回 → レグ → 入方向旋回 → 入方向レグに置き換える。既存の「左右で出方向点が同一」テストを鏡像関係へ更新する。
3. 位置 DTO に `holding` を加算し、OpenAPI と契約テスト、Frontend の型分割を更新する。
4. レーダーに `pattern` を描き、README と台帳を更新する。

### Phase 2（このスライスの外）

- 進入セクター、公示カタログ、EAT／EFC
- 風修正とインバウンド時間の調整、TAS 換算
- 経路表示のオン／オフ

---

## 検証

- [x] 完了条件の数値・飛行・契約テストが、未実装では失敗し、実装後は成功する
- [x] Hold 指示 API を変える差分が PR に含まれていない
- [x] 非待機機の位置 JSON に `holding` が無い
- [x] ブラウザで左右のレーストラックとオーバーレイ、Resume を確認する

---

## 未解決事項（Unresolved Questions）

- **アビーム判定（決定）**: 平面投影の cross-track / along-track（`HoldingPatternGeometry.isAbeamOutbound`）。出方向計時はアビーム、または出方向ロールアウト完了の遅い方（無風では実質ロールアウト完了と一致）。
- **半径・レグ固定（決定）**: 各出方向開始（フィックス通過）時の対地速度で `turnRate` / `turnRadius` / `outboundLegNm` を凍結し、次の出方向開始まで飛行と `holding.pattern` の両方に使う。

---

## 関連ドキュメント

- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- [spec/20261122-hold-inbound-leg-turns/spec.md](../20261122-hold-inbound-leg-turns/spec.md)
- [spec/20260509-phase3-holding-pattern/spec.md](../20260509-phase3-holding-pattern/spec.md)
- [spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)
- 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』: [JAPA 公開 PDF（2025-03）](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)（(Ⅱ)６(１)ｂ 待機指示）
- FAA Order JO 7110.65BB Change 3: [4-6 Holding Aircraft](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap4_section_6.html)
- FAA AIM 5-3-8 Holding: [Chapter 5 Section 3](https://www.faa.gov/air_traffic/publications/atpubs/aim_html/chap5_section_3.html)（標準率 3°/秒、バンク 30°／25°）
- FAA AIP ENR 1.5: [Holding, Approach, and Departure Procedures](https://www.faa.gov/air_traffic/publications/atpubs/aip_html/part2_enr_section_1.5.html)（時間レグ、出方向の計時、バンク。米国 AIP であり、AIP Japan の条文そのものではない）
- ICAO Doc 8168 (PANS-OPS): レーストラック待機の国際的な出典。本文は有償のため、本 spec では段落番号を置かない
- [Backend/README.md](../../Backend/README.md)
- [Frontend/README.md](../../Frontend/README.md)
