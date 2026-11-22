# MSAW 簡易版（最低安全高度警報）

## メタデータ

- **Status**: In Progress
- **Date**: 2026-11-22
- **関連**: [spec/spec.md](../spec.md) フェーズ 4-4、[spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)、Issue [#63](https://github.com/HorusATC/Horus/issues/63)、[spec/20261121-emergency-squawk/spec.md](../20261121-emergency-squawk/spec.md)

## 概要

ターミナルレーダーの管制官画面に、最低安全高度警報（MSAW）の簡易版を入れる。航空機の現在高度、または短い先読みによる予測高度が、訓練用の区域最低高度を下回ったら、Controller のデータブロックに STCA や緊急スコーク（EMRG 等）と区別できる表示（TAPS に合わせた `MSA` / `LOW`）を出す。区域高度は RJTT 周辺の簡略ポリゴンであり、AIP の MVA や障害物データそのものではないことを明示する。最終進入のごく近傍では警報を抑制し、通常の着陸で誤警報が出ないようにする。

## 背景・課題

### 現状

- 位置・高度・針路・対地速度・垂直速度は Backend の `AircraftPosition`（緯度・経度・高度 ft）と `AircraftVector`（針路・対地速度 kt・垂直速度 ft/min）にあり、`GET /aircraft/location/all` の `AircraftLocationDto` で Frontend に届く。指示目標は `instructedVector`、管制メモは `atcClearance`。
- データブロックの警報表示は次のとおり。STCA は `riskLevel`（30 以上で黄リング、70 以上で赤点滅）。緊急スコークは `Frontend/utility/aircraft/emergencySquawk.ts` を単一の定義源として、データブロックに HIJK / RDOF / EMRG、シンボルに色付きリングを出す。復行は `GA`、タワー移管は `TWR`。
- コンフリクト検出は `ConflictDetector` が担当し、位置 API に `riskLevel` を載せる。地形・最低高度に対する同等の検出はない。
- 滑走路は `Backend/src/main/resources/fix/runways.json` に RJTT 16R と RJAA 16R のみ。最終進入の会合点は進入端から 8 NM、延長は 10 NM（`RunwayCatalog`）。`NavigationMode` に最終進入はなく、会合後は `HEADING` になる。位置 DTO に「ファイナル上」フラグはない。
- クイックスタートの到着サンプル（ANA101 / JAL202 / SKY303 / ANA404 / JAL505）は到着空港 RJTT、初期高度 16,000〜20,000 ft。初期垂直速度は機により −500〜−1,000 fpm（例: ANA101 は −800 fpm）。民間機の最大上昇／降下率は 1,640 fpm。
- ロードマップ 4-4（MSAW 簡易版）とターミナルレーダーギャップ台帳は、この能力を未着手のまま残している。緊急スコーク（5-5 / #68）は Done。

### 課題（Problem Statement）

- 実際のターミナルレーダー（日本では TAPS、以前の ARTS-F を含む）は、CFIT を防ぐ地上セーフティネットとして MSAW（低高度警報）を持つ。Horus には STCA があるが、地形・最低高度側の警報がない。
- 進入中の高度逸脱を、訓練や研究の独立した条件として再現できない。
- 日本の管制方式基準は、IFR 機に低高度警報が出たときの通報手順と用語を定めている。画面に警報がなければ、その手順を練習できない。

### なぜ今か（Motivation）

- 針路・高度・速度、Direct To、スクオーク、STCA、緊急スコーク、滑走路と最終進入が main に揃っている。次の実機差として、台帳が「間隔・警報の残り」に置いているのが MSAW である。
- 静的検査の第 1 段階が入り、型と契約テストを先に置ける状態になった。
- 第 1 版は固定しきい値または区域ポリゴンで足り、本格的な数値標高モデルは後段でよい、という切り方は既に研究バックログとロードマップ 4-4 で固定している。

---

## 方針

### 決定方針（Decision）

1. **根拠となる一次資料（表示と手順）**
   - **日本（現行手続き）**: 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』（管制方式基準を含む。日本航空機操縦士協会が 2025-03 に公開した版）。用語「低高度警報」は次の 3 つを含む。
     - `LOW`: TAPS のターミナル管制 HMI およびタワーディスプレイ。計器進入方式の進入経路上を監視する。
     - `MSA`: 同装置。進入経路上を除く進入管制区を監視する。
     - `LA`: ARTS 表示装置および ARTS のタワーディスプレイ。
   - 同規程 (Ⅰ)２(23)【最低安全高度警報】: 監視対象空域の IFR 機（トランスポンダー故障を除く）に低高度警報が出たとき、管制機関は警報を通報し、高度に注意を喚起する。用語は次で固定する。
     - 「低高度警報、直ちに高度を点検してください。」
     - `LOW ALTITUDE WARNING, CHECK YOUR ALTITUDE IMMEDIATELY.`
   - FAF 通過後（または着陸誘導管制所と通信中）の `LOW`／音声付き `LA` は直ちに通報する。それ以外の `MSA`／音声なし `LA` は表示の有効性を判断してから通報する、という差がある。第 1 版の画面は `LOW` と `MSA` を区別して出し、判断そのものは管制官役に委ねる。
   - **日本（実システム上の見え方）**: 運輸安全委員会 AI2016-3（那覇、JA802P）。ARTS-F の MSAW 進入監視は、現在高度による警報（AC）と予測高度による警報（AP）を持ち、データブロックに赤字の「ＬＡ」を出し、ブザーのあと音声警報を鳴らす。那覇 RWY18 の PAR 進入では、監視エリアが進入端から 15.74〜0.81 NM と記録されている（0.81 NM より内側は監視外）。
   - **ICAO**: Doc 4444 (PANS-ATM) 15.7.4。目的は最低安全高度の侵害の可能性を適時に警告し CFIT を防ぐこと。通報高度を定義済みの最低安全高度と比べ、現在または予測が下回れば、担当管制官に視覚および音声の警告を出す。レーダー誘導中なら直ちに安全高度へ上昇（必要なら針路変更）を指示し、それ以外は警報が出たことを伝え高度確認を指示する。地域手順が決めるべき事項（対象飛行、空域、高度値、表示方法、パラメータと警告時間、個別抑制）も同項が列挙する。条文は ICAO の有料文書本体ではなく、JTSB AI2016-3 2.21.1 の引用と、EUROCONTROL GUID-160 が「15.7.4 に由来する」と明記していることを一次の確認経路とする。
   - **FAA**: JO 7110.65BB Change 3（2026-07-09 効力）2-1-6 Safety Alert。用語は `LOW ALTITUDE ALERT (call sign), CHECK YOUR ALTITUDE IMMEDIATELY.` であり、ファイナル前なら当該空域の MEA/MVA/MOCA/MIA を続けてよい。同 5-14-7 はターミナル（STARS）で VFR および IFR キャンセル機の MSAW 処理を原則抑制し、個別の low altitude alert は管制席から suppress できるとする。同 5-13-2 は航空路の E-MSAW であり、本スライスの対象外とする。
   - **EUROCONTROL**: Specification for MSAW（EUROCONTROL-SPEC-126, Edition 0.9, 2009-05-19, Draft）および Guidelines GUID-160 Part I / Part III（Edition 1.0, 2017-01-18）。MSAW は地形・障害物への接近を適時に知らせる地上セーフティネットで、視覚警報は必須、音声は任意。警報は条件が続く間出す（MSAW-13）。事前に決めた空域と個別便の抑制が可能でなければならない（MSAW-14）。本仕様は Approach Path Monitor（APM）には適用せず、APM は別のセーフティネットとする。Part III は空港近傍では MSAW を抑制し、最終進入の逸脱は APM に任せることを推奨する。ポリゴン定義のときの TerrainPredictionTime は 0〜50 秒が推奨範囲。
   - **ICAO Doc 9426**（ATS Planning Manual, 1984）は有料で、今回は本文を取得していない。運用要件の一次は Doc 4444 15.7.4 とする。

2. **検出ロジック（第 1 版）**
   - 検出は Backend の専用サービス（`ConflictDetector` と同列のドメインサービス）が行う。Frontend で高度としきい値を再計算しない。
   - 対象は、位置 API に載るすべての航空機とする。Horus には IFR/VFR 区分もトランスポンダー故障フラグもないため、第 1 版は全機を「高度通報付き IFR 相当」として扱う（FAA の VFR 抑制と、管制方式基準のトランスポンダー故障除外は、フラグが無いあいだ適用しない。未解決事項に残す）。
   - 区域最低高度は、クラスパス上の JSON（`runways.json` と同様のカタログ）で与える。各要素は識別子、最低高度（ft）、緯度経度のポリゴン（閉じていなくても判定時に閉じる）。**この数値と形状は訓練用の簡略値であり、AIP Japan の MVA、MSA、障害物、または公式の進入管制区境界ではない。** カタログと README の両方にその旨を書く。ポリゴンが重なるときは、より高い最低高度を採用する（安全側）。どのポリゴンにも入っていない機体は警報を出さない。
   - 初期の訓練用区域（実装時に JSON へ落とす。経緯度はおおむねの矩形でよい）:
     - `west-mountains`（箱根・丹沢・富士寄りの西側）: 最低高度 10,000 ft。目安 34.80–35.80 N、138.20–139.25 E。クイックスタートの ANA101（35.2, 138.8）、JAL202、SKY303 がここに入る。
     - `izu-south`: 最低高度 6,000 ft。目安 34.20–34.80 N、138.40–139.50 E。
     - `kanto-plain-and-bay`（関東平野と東京湾周辺）: 最低高度 2,500 ft。目安 34.80–36.20 N、139.25–140.90 E。ANA404、JAL505 がここに入る。
   - **現在高度**が当該最低高度未満、または **先読み 45 秒**（垂直速度 ft/min を用い、水平位置も `GeodeticUtils.predictPosition` で進めた点の区域高度と比較）で未満になるとき、警報を出す。45 秒は EUROCONTROL Part III のポリゴン利用時 0〜50 秒の範囲に入る訓練パラメータであり、カタログに定数として置き、コードに埋めない。ARTS-F の AC（現在）と AP（予測）に合わせ、DTO にはどちらで発火したかを載せる（両方なら `CURRENT` を優先する）。
   - **進入経路上（`LOW`）**: カタログに載る各滑走路について、進入端から外側へ最大 10 NM（既存の `FINAL_EXTENSION_NM` と一致）、延長中心線から左右 1.0 NM 以内を「計器進入経路上」の簡易廊とする。廊の中では区域ポリゴンの代わりに、次の訓練用フロアを使う。
     - `floorFt = airportElevationFt + 300 × 進入端までの海里`
     - 300 ft/NM は概ね 3 度の降下に相当する簡略値で、公示されたグライドパスではない。空港標高はカタログに訓練値として置く（RJTT 20 ft、RJAA 140 ft）。
     - このフロアを下回ったら種別は `LOW`。廊の外で区域最低高度を下回ったら種別は `MSA`。
   - **抑制**: いずれかのカタログ滑走路進入端から 1.0 NM 未満（滑走路上を含む）では MSAW を出さない。1.0 NM は FAA の Approach Path Monitor Cutoff（通常 1 NM、最大 2 NM）および JTSB が記録した那覇の監視下限 0.81 NM に合わせた訓練値で、カタログに置く。本抑制は「通常の接地付近の誤警報を防ぐ」ためであり、最終進入全体を消すものではない（那覇の事例のように、ファイナル上でも低すぎれば `LOW` を出す）。本格的な APM は範囲外とする。
   - 個別便の suppress / コントローラ操作による inhibit は第 1 版の範囲外とする（EUROCONTROL MSAW-14 と FAA 5-14-7 にあるが、今回は実装しない）。
   - 警報は条件が続くあいだ出し、高度が当該フロア以上に戻れば（現在も予測も下回らなくなれば）消す。

3. **表示仕様（Controller データブロック）**
   - Frontend の定義源は `emergencySquawk.ts` と同じ形の `Frontend/utility/aircraft/msawAlert.ts` に集約する。ラベル、色、通報用語、型ガードをここにだけ書く。`drawAircraft.ts` やバナーはそれを参照するだけにする。
   - データブロックに、緊急スコーク行と同様の短いラベルを出す。
     - `MSA`: 進入経路外。色は EMRG の赤（`#cf2222`）および STCA の黄／赤と重ならない琥珀（例: `#f59e0b`）。
     - `LOW`: 進入経路上。同じ色（種別は文字で区別する）。TAPS の色分けが一次資料で確認できなかったため、第 1 版は色を分けない。
   - シンボルには、STCA 赤点滅・緊急スコークリングより後、STCA 黄リングより前の優先度で、同色の定常リングを描く。点滅は STCA の `riskLevel >= 70` に残す。
   - 優先度（マーカー）: STCA 赤点滅（>= 70）> 緊急スコークリング > MSAW リング > STCA 黄（>= 30）> スコークフィルタ強調。データブロックでは `MSA`/`LOW` と HIJK/RDOF/EMRG を同時に出してよい（危害の種類が違うため）。
   - Controller 画面に、1 機以上が警報中なら規程の通報用語（日本語と英語）を短く提示する。位置は既存の STCA ストリップ周辺でよい。音声・ブザーは範囲外（ARTS-F にはあるが、緊急スコーク第 1 版と同じく視覚のみ）。
   - Operator 画面でも位置 DTO は同じなので表示してよいが、通報用語バナーは Controller のみとする。

4. **API**
   - 既存の `GET /aircraft/location/all` および `GET /aircraft/location` の形は壊さない。フィールドの追加のみ。
   - `AircraftLocationDto` に次を追加する（未警報時は `null`。フィールド自体は常に出す）。
     ```json
     "msawAlert": {
       "type": "MSA",
       "msaFt": 2500,
       "trigger": "CURRENT"
     }
     ```
     - `type`: `"MSA"` または `"LOW"`
     - `msaFt`: 判定に使った最低高度（ft）
     - `trigger`: `"CURRENT"` または `"PREDICTED"`
   - 新しいエンドポイントは作らない。Frontend の `AircraftLocationDto` と `mapDtoToAircraft` は、フィールド欠落を未警報として扱う（古い Backend との耐性）。
   - `Backend/UranosAPI.yml` の `AircraftLocation` に同じフィールドを追加する。既存フィールドの意味と必須性は変えない。

5. **型をテストより先に使う**
   - Backend は `MsawAlertType`（`MSA` / `LOW`）と `MsawTrigger`（`CURRENT` / `PREDICTED`）を列挙型にし、DTO と検出結果で共有する。
   - Frontend は `msawAlert.ts` を唯一の表示定義源とする。Jest は製品コードを import し、ラベル・色・用語・型ガードをその定義に対して確認する。テスト側に同じ表をコピーしない。
   - 検出の正しさは Backend のユニットテストが、実在の `MsawDetector`（およびカタログ読み込み）を呼んで保証する。次を少なくとも含む。
     - 区域ポリゴン内で現在高度が最低高度未満なら `MSA` が付く。
     - 現在は以上でも、45 秒先の予測位置・高度が未満なら `PREDICTED` で付く。
     - 両方以上なら `null`。
     - 進入廊内で訓練フロア未満なら `LOW`。
     - 進入端から 1.0 NM 未満なら出ない。
     - 重なるポリゴンでは高い方の最低高度を使う。
     - ポリゴン外では出ない。
     - しきい値を逆にしても（以上で発火、未満で消える、など）テストが落ちること。

### 検討した他案（Alternatives Considered）

- **案 A: 全域を単一の固定最低高度（例: 3,000 ft）にする。**
  - 却下理由: 西側の山岳と東京湾を同じ値にすると、どちらかで誤警報か見逃しになる。ポリゴンでも実装量は小さい。
- **案 B: データブロック表示を ARTS の単一「LA」（赤）だけにする。**
  - 却下理由: 現行の日本の装置は TAPS の `LOW` / `MSA` であり、管制方式基準 (Ⅰ)２(23) も両者で手順が違う。3 文字ラベルで足り、進入廊は既存の滑走路データから作れる。ARTS の「LA」は関連資料と README で対応を説明する。
- **案 C: 最終進入の廊全体で MSAW を抑制する（EUROCONTROL の「空港近傍は APM に任せる」を大きく取る）。**
  - 却下理由: 那覇の重大インシデントはファイナル上の低高度で LA が発出している。第 1 版で進入全体を消すと、いちばん訓練したい逸脱が再現できない。抑制は進入端 1.0 NM 未満に限り、廊内は簡略フロアで `LOW` を出す。本格 APM は別 spec とする。
- **案 D: 数値標高モデルや AIP の公式 MVA を使う。**
  - 却下理由: 公式 MVA チャートは AIP Japan（AIS アカウント）にあり、今回は再配布できる一次データとして取得していない。DEM は範囲も検証も第 1 版に対して大きすぎる。訓練用ポリゴンと免責を先に置く。
- **案 E: MSAW 専用 API を新設する。**
  - 却下理由: STCA の `riskLevel` と同じく、レーダーが既にポーリングしている位置 API に載せる方が一貫する。既存の応答形を壊さない。
- **案 F: 音声警報と個別 suppress UI を第 1 版に含める。**
  - 保留: 緊急スコーク第 1 版と同じく視覚を先にする。EUROCONTROL も視覚を必須、音声を任意とする。

### トレードオフ（Trade-offs）

- **メリット**:
  - 進入中の高度逸脱を、STCA とは独立した条件として訓練・研究できる。
  - 日本の現行表示（`LOW` / `MSA`）と規程の通報用語に寄せられる。
  - 既存の位置 API を拡張するだけで、エンドポイント追加がない。
- **デメリット / 受容する制約**:
  - 区域高度は訓練用の簡略値であり、公式 MVA / 障害物ではない。利用者にその旨を必ず示す。
  - 進入フロアは 3 度相当の簡略値であり、公示進入方式や APM ではない。
  - 滑走路カタログが RJTT 16R と RJAA 16R しかないため、`LOW` と進入端抑制もこの 2 本に限る。
  - IFR/VFR とトランスポンダー故障を区別できない。
  - 音声、個別 suppress、公式地形データ、APM は範囲外。
  - サンプル機の初期高度が高いため、ブラウザ確認ではシミュレーション速度 10 倍と、最低高度を下回るまでの待機が必要。

---

## 完了条件（Success Criteria）

- [ ] `spec/20261122-msaw/spec.md` がリポジトリにあり、実装完了後に Status が Done になっている
- [ ] 訓練用 MSA カタログがリポジトリにあり、公式 MVA / 障害物データではないことがファイル先頭と README に書かれている
- [ ] Backend のユニットテストが、実在の検出コードとカタログを呼び、上記の検出ケース（現在・予測・解除・`LOW`・進入端 1.0 NM 抑制・重なり・区域外・しきい値の逆転で失敗すること）を満たす
- [ ] Frontend の Jest が `msawAlert.ts` など製品コードを import し、ラベル・色・通報用語をその定義に対して確認する（テストへ表をコピーしない）
- [ ] 位置 API の既存フィールドと既存エンドポイントの動きが変わっていない（追加は `msawAlert` のみ）
- [ ] `UranosAPI.yml` の `AircraftLocation` に `msawAlert` が追加されている
- [ ] Backend のテストが全件合格する
- [ ] Frontend のテストとビルドが全件合格する
- [ ] CI の 4 ジョブ（変更検出、Backend、Frontend、Docker Compose）が、マージするコミットの SHA で全部合格する
- [ ] ルート README、Backend README、Frontend README が、同じ変更に含まれて更新されている
- [ ] `spec/spec.md` の 4-4 と、必要なら `spec/20261017-terminal-radar-gaps/spec.md` の参照が、実装後の状態に更新されている
- [ ] ブラウザでの手動検証が完了している（PdM またはテックリードが、対象コミットで次を確認する）:
  - (a) トップの START WITH SAMPLE から開始する
  - (b) 区域が既知の 1 機（例: 関東平野の JAL505、最低高度 2,500 ft）に、その最低高度を明らかに下回る高度（例: 1,500 ft）を指示する
  - (c) シミュレーション速度を 10 倍にし、現在または予測高度が最低高度を下回るまで待つ
  - (d) Controller のデータブロックに `MSA`（進入廊内なら `LOW`）が、EMRG および STCA と区別できる色で出る。他機には出ない
  - (e) 通報用語（日本語と英語）が Controller に出る
  - (f) 最低高度を明らかに上回る高度へ上昇を指示し、上昇後にラベルと用語が消える
  - (g) ブラウザのコンソールと Backend のログにエラーがない
  - (h) スコーク・針路・速度の指示と、STCA の既存表示が引き続き動く
  - 進入端 1.0 NM 抑制は、ブラウザで再現しにくい場合はユニットテストの合格をもって足りる。再現できた場合は記録する
  - 既知の問題（Controller データブロックが古い目標高度を示すことがある。main でも発生）を、本機能の不具合と取り違えない

---

## 影響範囲

- **Backend**:
  - 新規: MSA カタログ（JSON）、読み込み、点のポリゴン内判定、`MsawDetector`（または同等のドメインサービス）、警報の列挙型
  - `GetAllAircraftLocationsWithRiskUseCase` と `AircraftLocationDto` に `msawAlert` を追加（`toDto` と fallback の両方）
  - `UranosAPI.yml` の `AircraftLocation`
  - ユニットテスト（検出・カタログ・必要なら位置 API のフィールド追加）
- **Frontend**:
  - `utility/aircraft/msawAlert.ts`（定義源）
  - `utility/api/location.ts` の DTO と `mapDtoToAircraft`
  - `utility/aircraft/aircraftClass.ts`（フィールドの保持と更新）
  - `utility/aircraft/drawAircraft.ts`（ラベルとリング、優先度）
  - Controller の通報用語バナー（新規コンポーネント、または既存ストリップ付近）
  - Jest（製品コードを import）
- **ドキュメント**:
  - ルート README、Backend README（位置 API の追加フィールドと、カタログが訓練用であることの説明）、Frontend README（データブロック表示と通報用語）
  - 本 spec、`spec/spec.md` 4-4、必要ならギャップ台帳
- **変えないもの**:
  - 飛行力学、管制指示 API、STCA の計算、緊急スコーク API、既存位置フィールドの意味

---

## 実装計画

### Phase 1: カタログと検出

- 訓練用 JSON カタログ（区域ポリゴン、先読み 45 秒、進入端抑制 1.0 NM、進入廊の幅と 10 NM、300 ft/NM のフロア、空港標高の訓練値、免責文）
- ポリゴン内判定と `MsawDetector`
- 上記ユニットテスト（逆転ケースで落ちることを含む）

### Phase 2: API と Frontend 表示

- DTO / OpenAPI / 位置ユースケースへの `msawAlert` 追加
- `msawAlert.ts`、描画、通報用語（Controller のみ）
- Jest（製品コードを import）

### Phase 3: ドキュメントと検証

- README 3 系統とロードマップ行の更新
- ローカルで Backend テスト、Frontend の test と build、format / lint を実行し、出力を残す
- ブラウザ確認 (a)–(h)
- CI 4 ジョブが対象 SHA で合格することを確認してから完了とする

---

## 検証

- [ ] Backend のテストが通る
- [ ] Frontend のテストとビルドが通る
- [ ] CI（Docker Compose を含む 4 ジョブ）が、マージするコミットで全部合格する
- [ ] 手動動作確認（完了条件の (a)〜(h)）
- [ ] README と spec の状態更新

---

## 未解決事項（Unresolved Questions）

- IFR/VFR またはトランスポンダー故障のフラグを、後のスライスで航空機状態に足し、FAA 5-14-7 と管制方式基準の除外を実装するか。
- 個別便の suppress / inhibit UI を、STCA の抑制と揃えて別 spec にするか。
- 音声警報（ARTS-F のブザーと "LOW ALTITUDE WARNING"）を緊急スコークの音声とまとめて別 spec にするか。
- 公式の MVA / 進入管制区境界を、AIP Japan から利用許諾の範囲で取り込めるか。取り込むまでは訓練用ポリゴンを使う。
- 進入廊内の簡略フロアを、後で独立した APM（EUROCONTROL が MSAW と分けるもの）に置き換えるか。
- カタログ滑走路を RJTT / RJAA の他方向や他空港へ増やすタイミング（`LOW` と抑制の対象が今は 16R の 2 本に限られる）。
- 区域ポリゴンを Controller のレーダーに描くか（第 1 版は検出しきい値だけとし、描画は必須にしない）。

---

## 関連ドキュメント

- [spec/spec.md](../spec.md)（4-4 MSAW 簡易版）
- [spec/20261017-terminal-radar-gaps/spec.md](../20261017-terminal-radar-gaps/spec.md)
- [spec/20261121-emergency-squawk/spec.md](../20261121-emergency-squawk/spec.md)
- [docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md)
- 国土交通省航空局『航空保安業務処理規程 第５管制業務処理規程』（管制方式基準）: [JAPA 公開 PDF（2025-03）](https://www.japa.or.jp/wp-content/uploads/2025/03/796cd8c6b76f1d8f80777b3f72785bdd.pdf)（用語「低高度警報」、(Ⅰ)２(23)）
- 運輸安全委員会 AI2016-3: [https://jtsb.mlit.go.jp/aircraft/rep-inci/AI2016-3-1-JA802P.pdf](https://jtsb.mlit.go.jp/aircraft/rep-inci/AI2016-3-1-JA802P.pdf)（2.21 MSAW、ARTS-F の LA / AC / AP、監視下限 0.81 NM）
- AIP Japan（公式 MVA 等、第 1 版では数値を使わない）: [https://aisjapan.mlit.go.jp/](https://aisjapan.mlit.go.jp/)
- 国土交通省 航空：用語集（TAPS 等）: [https://www.mlit.go.jp/koku/15_bf_000320.html](https://www.mlit.go.jp/koku/15_bf_000320.html)
- ICAO Doc 4444 (PANS-ATM) 15.7.4（JTSB 2.21.1 の引用、EUROCONTROL GUID-160 が由来を明記）
- FAA Order JO 7110.65BB Change 3: [2-1-6 Safety Alert](https://www.faa.gov/Air_traffic/publications/atpubs/atc_html/chap2_section_1.html)、[5-14-7 Inhibiting MSAW](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap5_section_14.html)、[現行号の案内](https://www.faa.gov/regulations_policies/orders_notices/index.cfm/go/document.current/documentnumber/7110.65)
- EUROCONTROL Specification for MSAW (SPEC-126, Ed. 0.9, 2009-05-19, Draft): [SKYBrary PDF](https://skybrary.aero/sites/default/files/bookshelf/5200.pdf)
- EUROCONTROL Guidelines for MSAW (GUID-160, Ed. 1.0, 2017-01-18): [案内](https://www.eurocontrol.int/publication/eurocontrol-guidelines-minimum-safe-altitude-warning-msaw)、[Part I](https://www.eurocontrol.int/sites/default/files/2019-09/ectl-guideline-160-msaw-part-i-1.0.pdf)、[Part III](https://www.eurocontrol.int/sites/default/files/2019-09/ectl-guideline-160-msaw-part-iii-1.0.pdf)
- [Backend/README.md](../../Backend/README.md)
- [Frontend/README.md](../../Frontend/README.md)
