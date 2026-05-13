# Flyway 運用ルール

Flyway の versioned migration を安全に運用するためのルールと、チェックサム不一致時の対応方針をまとめる。

## ベストプラクティス

- `src/main/resources/db/migration/V*.sql` は、一度どこかの DB に適用されたら**不変**として扱う。
- スキーマ変更やデータ補正は、既存 `V` を編集せず新しい `V` を追加する。
- `V` の番号が増えることは問題として扱わない。
- migration 履歴が長くなったら、既存 `V` を潰すのではなく `B*.sql` の baseline migration を追加する。
- `repair` は checksum 不一致の救済手段であり、通常運用の代替にはしない。

## なぜ `V` を増やし続けてよいのか

Flyway は versioned migration を順番に適用し、`flyway_schema_history` で履歴を管理する。

- `V` は変更履歴そのもの
- 番号が増えること自体は正常
- 問題になるのは、適用済み `V` の中身を後から変えること

つまり「version が増える」のは自然で、「適用済み migration を編集する」のが危険。

## 履歴を短くしたいときの正攻法

Flyway では `B*.sql` の baseline migration を使う。

- 例: `B7__baseline.sql`
- 新しい環境では、Flyway は最新の baseline から開始する
- `B7` 以下の古い `V1..V7` は新規環境では無視される
- 既存環境では baseline migration は無視される

この方法なら、既存環境を壊さずに新規構築だけを軽くできる。

## このリポジトリでの運用ルール

- 通常の変更は `V*.sql` を追加して進める
- 既存 `V*.sql` の編集は禁止
- 初回リリース前や大きな節目で、新規環境の初期化を軽くしたくなったら `B*.sql` を追加する
- `B` を追加しても、古い `V` は削除しない
- `repair` はローカル開発や合意済み環境の例外対応に限定する

## なぜ必要か

Flyway は、適用済み migration の version と checksum を DB の `flyway_schema_history` に保存する。

- DB に記録された checksum と、現在の `V*.sql` ファイルの checksum が一致しない
- 起動時の `validate` が失敗する
- Spring Boot が起動失敗する
- `docker compose` では backend コンテナが再起動ループに入る

つまり、既存 migration をあとから編集すると、コードだけ直しても既存 DB では起動できなくなる。

## 今回の事象

今回確認した事象は以下。

- migration version: `6`
- DB に記録済み checksum: `-124199906`
- 現在ファイルの checksum: `-198025761`

これは「`V6` が DB 適用後に編集された」状態を意味する。

## チェックサム不一致時の対応ルール

優先順位は以下。

1. 既存 migration を元に戻す。
2. 必要な変更は新しい migration として追加する。
3. 新規環境の初期化負荷を下げたいなら、別途 `B*.sql` を追加する。
4. `repair` は、既存 DB を壊さないことを理解したうえで、意図的に採用するときだけ使う。

## `repair` を使ってよい条件

`repair` は便利だが、雑に使うと「DB の履歴だけを現在ファイルに合わせる」ので、問題の痕跡を隠しやすい。原則として常用しない。

使ってよいのは次のようなケースに限る。

- ローカル開発 DB で、履歴を書き換えても影響範囲を把握できている
- ステージング等で、チーム内で `repair` 実施を合意している
- 既存 migration の編集が意図的で、DB 実体との差分確認が済んでいる

次のケースでは避ける。

- 本番 DB
- いつ・なぜ migration を編集したのか不明な状態
- DB 実体と SQL 差分を確認していない状態

## チェックサム不一致が出たときの手順

1. ログで `version`, `applied checksum`, `resolved checksum` を確認する。
2. 該当 `V*.sql` が過去に編集されていないか `git log -p -- <file>` で確認する。
3. その変更を残すべきか、元に戻すべきか判断する。
4. 原則は「既存 migration を戻し、新しい migration を追加」する。
5. 新規環境の migration 時間や見通しが気になるなら、`B*.sql` 追加を検討する。
6. 例外的に `repair` を使う場合は、ローカル/合意済み環境に限定し、理由を docs や PR に残す。

## レビュー時の確認事項

- 既存の `V*.sql` を編集していないか
- migration 追加で表現できる変更を、既存 migration 修正で済ませていないか
- 履歴短縮のために既存 `V` を潰そうとしていないか
- baseline が必要な場面なら `B*.sql` で表現しているか
- `repair` を前提にした運用になっていないか
