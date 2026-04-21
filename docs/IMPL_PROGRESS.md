# 実装進捗トラッカー

再開ポイント: このファイルを見れば次に何をすべきか分かる。

## Phase 1: Domain Layer ✅ 完了
- [x] `domain/card/Card.java`
- [x] `domain/card/Rank.java`
- [x] `domain/card/Suit.java`
- [x] `domain/card/Deck.java`
- [x] `domain/hand/HandRank.java`
- [x] `domain/hand/HandValue.java`
- [x] `domain/hand/HandEvaluator.java`
- [x] `domain/rules/Street.java`
- [x] `domain/rules/ActionType.java`
- [x] `domain/rules/Action.java`
- [x] `domain/rules/PlayerState.java`
- [x] `domain/rules/TableState.java`
- [x] `domain/rules/ActionValidator.java`
- [x] `domain/game/GameStatus.java`
- [x] `domain/game/OddChipRule.java`
- [x] `domain/game/Player.java`
- [x] `domain/game/ShowdownResult.java`
- [x] `domain/game/GameState.java`

## Phase 2: Application Layer ✅ 完了
- [x] `application/ActionRecord.java`
- [x] `application/GameRepository.java`
- [x] `application/GameService.java`

## Phase 3: Infrastructure Layer ✅ 完了（local プロファイル）
- [x] `infrastructure/persistence/InMemoryGameRepository.java`
- [x] `infrastructure/config/SecurityConfig.java`
- [x] `resources/db/migration/V1__initial_schema.sql`
- [x] `resources/application.properties`
- [x] `resources/application-local.properties`
- [x] `infrastructure/persistence/PostgresGameRepository.java`
- [x] `infrastructure/persistence/InMemoryUserRepository.java`
- [x] `infrastructure/persistence/PostgresUserRepository.java`
- [x] `application/User.java`
- [x] `application/UserRepository.java`
- [x] `application/UserService.java`
- [x] `infrastructure/config/RateLimitConfig.java`

## Phase 4: HTTP Layer ✅ 完了
- [x] `interfaces/http/dto/CreateGameRequest.java`
- [x] `interfaces/http/dto/StartHandRequest.java`
- [x] `interfaces/http/dto/ApplyActionRequest.java`
- [x] `interfaces/http/dto/GameResponse.java`
- [x] `interfaces/http/dto/ShowdownResponse.java`
- [x] `interfaces/http/dto/ActionsResponse.java`
- [x] `interfaces/http/dto/ErrorResponse.java`
- [x] `interfaces/http/GameController.java`
- [x] `interfaces/http/GlobalExceptionHandler.java`
- [x] `interfaces/http/AuthController.java`
- [x] `interfaces/http/RoomController.java`
- [x] `interfaces/http/filter/LoginRateLimitFilter.java`
- [x] `interfaces/http/dto/LoginRequest.java`
- [x] `interfaces/http/dto/RegisterRequest.java`
- [x] `interfaces/http/dto/UserResponse.java`
- [x] `interfaces/http/TableController.java`
- [x] `interfaces/http/dto/CreateTableRequest.java`
- [x] `interfaces/http/dto/TableResponse.java`
- [x] `interfaces/http/dto/TableMembershipRequest.java`

## Phase 5: クリーンアップ ✅ 完了
- [x] `com.mapoker.mapoker.*` 重複ファイル無害化

## Phase 6: Tests ✅ 完了
- [x] `domain/hand/HandEvaluatorTest.java`
- [x] `domain/rules/ActionValidatorTest.java`
- [x] `domain/game/GameStateTest.java`
- [x] `MapokerApplicationTests.java` (local profile)
- [x] `interfaces/http/TableControllerIntegrationTest.java`
- [x] `interfaces/http/GameControllerVisibilityTest.java`
- [x] `PostgresPersistenceIntegrationTest.java` (Testcontainers)

---
## 完了メモ

- コア実装は完了。`/v1/tables` ベースのテーブル作成・参加・退室フローを実装済み。
- ホールカード可視性は、認証済みユーザーの参加席に基づいてサーバー側で判定するよう修正済み。
- DTO バリデーションと統一エラーハンドリングを追加済み。
- `/v1/auth/history` とテーブル参加履歴の永続化を追加済み。マイページはプレイ履歴 API を表示する構成に更新済み。
- 検証済み:
  - `npm run build`
  - `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v /tmp/m2:/root/.m2 -v /home/taiki/projects/mapoker_java/mapoker-backend:/workspace -w /workspace maven:3.9-eclipse-temurin-21-alpine ./mvnw test`

## 将来タスク（任意機能）

- ハンドリプレイ
- テーブル検索 / 絞り込み
- オーナー移譲や再接続時の自動復帰強化
