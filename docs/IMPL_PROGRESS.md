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

## Phase 5: クリーンアップ ✅ 完了
- [x] `com.mapoker.mapoker.*` 重複ファイル無害化

## Phase 6: Tests ✅ 完了（基本）
- [x] `domain/hand/HandEvaluatorTest.java`
- [x] `domain/rules/ActionValidatorTest.java`
- [x] `domain/game/GameStateTest.java`
- [x] `MapokerApplicationTests.java` (local profile)

---
## 残タスク（優先順）

### 次にやること
1. **コンパイル確認** — devcontainer内で `./mvnw test -Dspring.profiles.active=local` を実行してエラー確認
2. **ホールカード可視性** — 認証ユーザーの index と player_id を照合して hole cards フィルタリング
3. **統合テスト** — Testcontainers で PostgreSQL 含む E2E シナリオ
4. **バリデーション** — `@Valid` アノテーション、入力サニタイズ

### 既知の未実装
- ホールカード可視性の本番実装（現在は null 返し）
- 統合テスト（Testcontainers）
