package com.mapoker.application;

import com.mapoker.domain.game.GameState;
import java.util.List;
import java.util.Optional;

/**
 * ゲーム状態の永続化を担うリポジトリポート。
 *
 * <p>実装は Spring プロファイルにより切り替わる。
 * {@code local} プロファイルではインメモリ実装、{@code postgresql} プロファイルでは
 * PostgreSQL 実装が使用される。
 *
 * <p>{@link #update(String, GameState, ActionRecord)} は ゲーム状態の更新とアクションの
 * 挿入を単一トランザクションで行う必要がある。
 */
public interface GameRepository {

    /**
     * 新規ゲームを永続化する。
     *
     * @param id    ゲームを一意に識別する UUID 文字列
     * @param state 初期化済みの {@link GameState}
     */
    void create(String id, GameState state);

    /**
     * ゲーム状態を更新し、同時にアクションを記録する。
     *
     * <p>状態更新とアクション挿入は同一トランザクション内で完結させること。
     *
     * @param id     ゲーム ID
     * @param state  更新後の {@link GameState}
     * @param action 今回適用されたアクション
     */
    void update(String id, GameState state, ActionRecord action);

    /**
     * アクションを伴わずにゲーム状態のみを更新する。
     *
     * <p>ハンド開始・ショーダウン適用など、アクション記録が不要な更新に使用する。
     *
     * @param id    ゲーム ID
     * @param state 更新後の {@link GameState}
     */
    void update(String id, GameState state);

    /**
     * 指定 ID のゲームを取得する。
     *
     * @param id ゲーム ID
     * @return ゲームが存在する場合は {@link Optional} でラップされた {@link GameState}、
     *         存在しない場合は {@link Optional#empty()}
     */
    Optional<GameState> findById(String id);

    /**
     * 全ゲームを取得する。
     *
     * @return 全 {@link GameState} のリスト
     */
    List<GameState> findAll();

    /**
     * ゲーム状態を更新せずにアクションのみを追記する。
     *
     * <p>ショーダウン結果・ペイアウトなど、ゲーム状態変更を伴わないログ追記に使用する。
     *
     * @param id     ゲーム ID
     * @param action 追記するアクション
     */
    void appendAction(String id, ActionRecord action);

    /**
     * 指定ゲームに記録された全アクション履歴を取得する。
     *
     * @param gameId ゲーム ID
     * @return アクション履歴のリスト（{@code seq} 昇順）
     */
    List<ActionRecord> findActionsByGameId(String gameId);
}
