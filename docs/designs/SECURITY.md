# 脆弱性対応と検査

確認日: 2026-10-24（日本時間）。本番環境への反映はまだ行っていない。

## 今回の変更

- Java 21を維持し、Spring Bootを3.5.13から3.5.16へ更新。
- BootのBOMより新しい修正が必要な依存は、Jackson 2.21.8、Tomcat 10.1.61、PostgreSQL JDBC 42.7.14、Log4j 2.25.5を指定。
- フロントのDocker・CIをNode.js 24へ統一。nginxは1.31系列に固定。
- npmの宣言済み互換範囲でロックファイルを更新。React/React DOMは19.2.4、React Router DOMは7.18.4、Viteは7.3.7。
- 実行イメージのAlpineパッケージをビルド時に更新。
- PostgreSQL 16公式イメージに含まれるGo製gosuに指摘があったため、Alpineのsu-execへ置換したDBイメージを追加。PostgreSQLの初期化処理とメジャーバージョンは維持する。
- `CORS_ALLOWED_ORIGINS`が実際の`cors.allowed-origin-patterns`へ反映されるよう修正。既定は`http://localhost:3000`。
- 本番用HTTP APIにCSRF保護を有効化。`GET /v1/auth/csrf`でセッションに紐づくトークンを取得し、変更リクエスト・アップロード・STOMP CONNECTへヘッダーで送信する。
- ログイン時にセッションIDとCSRFトークンを更新。CSRF取得レスポンスは`no-store`。
- WebSocketは認証済みの指定購読先だけを許可。クライアントからのブロードキャスト送信と生のユーザーキュー購読は拒否する。CSRFは現行の`@EnableWebSocketSecurity`で検証する。
- オンラインの席操作・手札参照を変更できないpublicIdで照合。解決失敗時に指定席へフォールバックしない。
- オンライン卓のハンド開始等は着席者に限定。オンライン卓で手動の勝者指定を拒否する。
- 認証済みユーザーの入退席ではリクエストの名前より本人のIDを優先し、他人の名前へのフォールバックを拒否する。
- 管理者権限を変更可能なユーザー名からpublicIdへ変更。
- アバターは申告Content-Typeに加えて画像形式・寸法を検証。最大4096×4096ピクセル。
- 通常ゲームの乱数を`SecureRandom`へ変更。明示シード付きのテスト用APIは維持。
- nginxのlocation別設定でもセキュリティヘッダーを維持し、クライアントが渡した転送ヘッダーを上書き・除去する。
- DBとバックエンドのホスト公開を127.0.0.1に限定。DBパスワード未設定時はComposeを失敗させる。
- Dockerコンテキストから.env・node_modules・生成物を除外。

## 検査結果

Trivy 0.76.1を以下のイメージdigestで実行した。

`ghcr.io/aquasecurity/trivy@sha256:790fd66d1697395d8693d1acaa1ea702406a20f109b6fc7bac8db623eca6c06c`

| 対象 | 更新前 | 更新後 |
|---|---:|---:|
| npm依存（開発依存を含む） | 17件（High 12、Moderate 4、Low 1） | 0件 |
| 既存backendイメージのOS部品 | 106件 | 0件 |
| backendイメージのJava依存 | 66件 | 2件（下記の未使用機能） |
| frontend実行イメージ | 更新前未計測 | 0件 |
| 最新PostgreSQL 16公式イメージ | gosuに61件 | 派生イメージで0件 |

件数はパッケージと脆弱性の組み合わせの数で、重複しないCVE件数ではない。修正版を含む検査用イメージのタグは`mapoker-security-{backend,frontend,database}:review`。元イメージ・稼働コンテナは更新していない。

## Springに残る2件の評価

生のスキャン結果には2件とも残す。`.trivyignore.yaml`は特定バージョンのJARパスに限定し、2026-11-24までの期限を設定したCI判定用の例外である。

- [CVE-2026-47884](https://spring.io/security/cve-2026-47884/): XsltViewと暗黙のview名による画面描画が前提。本アプリはJSON REST APIで、該当のView設定・描画処理を使用しない。
- [CVE-2026-47890](https://spring.io/security/cve-2026-47890/): SSEでview fragmentを描画することが前提。本アプリはSTOMP WebSocketを使い、SSE/view fragmentを使用しない。

上記の機能を追加する場合、期限前でも例外を再評価する。公式情報上、6.2系の修正版はEnterprise Support、OSS修正版は7.0.9以降である。Spring Boot 4への移行は別途互換性検証を伴うため、今回のパッチ更新には含めない。

## 検証

- バックエンド: `cd mapoker-backend && ./mvnw test`（243件成功）。CSRFのHTTP往復、ログイン時のセッション更新、旧トークン拒否、WebSocket CONNECTのトークン検証、送信・購読制限、席操作・退出・管理者権限の回帰テストを追加。
- フロントエンド: `npm run lint`、`npm run build`、Docker内の`npm ci`とビルド。
- 3種類の実行イメージをビルド・検査。
- 隔離した一時DBコンテナで初期化・SQL実行・su-execの存在を確認。ホストポート・既存データは使わない。
- 一時frontendコンテナでindex.html・config.js・healthの応答とセキュリティヘッダーを確認。
- 一時DBとbackendを専用内部ネットワークで起動し、postgresql/productionプロファイル、17件のマイグレーション、healthとCSRF取得を確認。
- ローカル・本番のCompose設定を、値を表示しない`config --quiet`で確認。
- `./mvnw verify`の85%カバレッジ基準は、元ブランチの一時コピーでも未達（約72.3%）。変更後は約72.6%。基準を下げたり、今回の変更を通すために対象を除外したりはしていない。

## 継続検査

`.github/workflows/security.yml`でPR・main/developへのpush・週次・手動実行に対応する。

- `npm audit --audit-level=high`。
- backend/frontend/databaseを`docker build --pull`でビルドし、TrivyでJava・OS部品を検査。
- 生のJSONレポートを保存し、期限付き例外を適用したHigh/Criticalが残れば失敗。未修正版を一律に除外しない。
- レポートはGitHub Actions artifactとして30日保存。

## 反映前の作業

1. `POSTGRES_PASSWORD`が設定済みであることを確認する。既存DBに対しては、実際のパスワードと一致させる。環境変数の変更だけで既存DBのパスワードは変更されない。
2. 本番の`CORS_ALLOWED_ORIGINS`に実際のフロントエンドのOriginを指定する。`*`を指定しない。Cookie付き通信で`*`を指定すると起動時に拒否する。
3. 管理者設定を`WALLET_ADMIN_PUBLIC_IDS`（カンマ区切りのpublicId）へ移行する。従来の`WALLET_ADMIN_USERNAMES`は使用しない。未設定時は管理者によるチップ付与を許可しない。
4. リリースで追加されたdatabaseイメージも公開されていることを確認する。ローカルでは`docker compose --env-file .env.local build --pull`で3種類ともビルドできる。
5. 外部ホストからDB/APIへ直接接続していた運用は、SSHトンネル等へ切り替える。frontendからbackendへのコンテナ間通信は引き続き利用できる。
6. HTTPS経由でGoogleログイン・再接続・ゲーム操作・画像アップロードを確認する。セッションCookieは引き続きSecure/HttpOnly/SameSite=Lax。
7. スキャンを再実行する。依存DBは更新されるため、検査日の0件は将来の0件を保証しない。

## 継続する確認事項

- オフライン卓にはホスト/操作ユーザーの固定IDが保存されていない。ホストだけが全席を操作するのか、各参加者が自席を操作するのかの方針確認後、認可モデル・保存・UIを揃える。現状のオフライン卓の全席操作、招待コード配信・リバイ・クローズの権限を解決済みとは扱わない。
- 公開/非公開卓の閲覧・招待コードの扱い、履歴の参照範囲は追加確認が必要。
- 残高の同時更新・再試行・二重加算、共有状態の並行操作は個別の負荷・競合テストが必要。
- ログインのレート制限はプロセス内のIP別Mapであり、期限付き削除や分散制限は未導入。
- 秘密情報のGit履歴検査、実際のネットワーク境界・TLS設定・バックアップ復元は今回の依存/コード検査とは別途実施する。
