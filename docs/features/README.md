# Features Roadmap

このディレクトリはmapokerの大型アップデートのロードマップを管理します。

## 目的

- バグ修正・小規模な変更は GitHub Issues で管理する
- このディレクトリは**複数イシューにまたがる機能追加**や**将来の大型アップデート**の構想・設計ドキュメントを置く場所

## ステータス定義

| ステータス | 説明 |
|---|---|
| `planned` | 実装予定（設計検討中） |
| `in-design` | 設計・仕様策定中 |
| `in-progress` | 実装中 |
| `done` | リリース済み |

## バージョニング方針

| 規模 | バージョンアップ |
|---|---|
| バグ修正・細かい改善 | パッチ (`1.0.X`) |
| 新機能追加（小〜中） | マイナー (`1.X.0`) |
| 大規模リリース | メジャー (`X.0.0`) |

---

## 全機能一覧

| ファイル | 機能 | ステータス | 規模 |
|---|---|---|---|
| [ante.md](ante.md) | アンティ設定 | `planned` | マイナー |
| [straddle.md](straddle.md) | ストラドル | `planned` | マイナー |
| [table-join-bb.md](table-join-bb.md) | テーブル参加時の 1BB 強制支払い | `planned` | マイナー |
| [turn-sound.md](turn-sound.md) | 手番通知サウンド | `planned` | マイナー |
| [google-auth.md](google-auth.md) | Google Auth + username/discriminator | `planned` | マイナー |
| [spectator-mode.md](spectator-mode.md) | 観戦モード | `planned` | マイナー |
| [table-chat.md](table-chat.md) | テーブルチャット | `planned` | マイナー |
| [hand-history-viewer.md](hand-history-viewer.md) | ハンド履歴ビューアー | `planned` | マイナー |
| [offline.md](offline.md) | オフラインモード（リアルカード補助） | `in-design` | **メジャー** |
| [tournament.md](tournament.md) | トーナメントモード | `in-design` | **メジャー** |
