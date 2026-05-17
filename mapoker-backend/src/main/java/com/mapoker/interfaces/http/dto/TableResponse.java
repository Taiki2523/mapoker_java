package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.TableMemberRecord;
import com.mapoker.application.TableRecord;

import java.util.List;

/**
 * テーブル状態レスポンスです。
 *
 * @param id テーブル ID
 * @param roomId ルーム ID
 * @param name テーブル名
 * @param gameType ゲーム種別
 * @param stake ブラインド情報
 * @param minBuyIn 最小バイイン額
 * @param maxBuyIn 最大バイイン額
 * @param maxPlayers 最大参加人数
 * @param flags テーブル属性一覧
 * @param visibility 公開設定
 * @param status テーブル状態
 * @param gameId ゲーム ID
 * @param createdAt 作成日時
 * @param memberCount 参加者数
 * @param members 参加者一覧
 * @param game 紐づくゲーム情報
 */
public record TableResponse(
        String id,
        @JsonProperty("room_id") String roomId,
        String name,
        @JsonProperty("game_type") String gameType,
        StakeDto stake,
        @JsonProperty("min_buy_in") int minBuyIn,
        @JsonProperty("max_buy_in") int maxBuyIn,
        @JsonProperty("max_players") int maxPlayers,
        List<String> flags,
        String visibility,
        String status,
        @JsonProperty("game_id") String gameId,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("member_count") int memberCount,
        List<MemberDto> members,
        GameResponse game
) {
    /**
     * ブラインド情報 DTO です。
     *
     * @param smallBlind スモールブラインド額
     * @param bigBlind ビッグブラインド額
     * @param ante アンティ額（0 でアンティなし）
     */
    public record StakeDto(
            @JsonProperty("small_blind") int smallBlind,
            @JsonProperty("big_blind") int bigBlind,
            int ante
    ) {}

    /**
     * 参加者表示 DTO です。
     *
     * @param name 参加者名
     * @param seatIndex 着席位置
     * @param joinedAt 参加日時
     * @param pendingLeave 離席待ちかどうか
     */
    public record MemberDto(
            String name,
            @JsonProperty("seat_index") int seatIndex,
            @JsonProperty("joined_at") String joinedAt,
            @JsonProperty("pending_leave") boolean pendingLeave,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("avatar_url") String avatarUrl
    ) {}

    /**
     * テーブル情報からレスポンスを生成します。
     *
     * @param table テーブル情報
     * @param members 参加者一覧
     * @param game ゲーム情報
     * @return 生成したレスポンス
     */
    public static TableResponse from(TableRecord table, List<TableMemberRecord> members, GameResponse game) {
        List<MemberDto> memberDtos = members == null
                ? List.of()
                : members.stream()
                .map(member -> new MemberDto(member.name(), member.seatIndex(), member.joinedAt(),
                        member.pendingLeave(), member.displayName(), member.avatarUrl()))
                .toList();
        return new TableResponse(
                table.id(),
                table.roomId(),
                table.name(),
                table.gameType(),
                new StakeDto(table.smallBlind(), table.bigBlind(), table.ante()),
                table.minBuyIn(),
                table.maxBuyIn(),
                table.maxPlayers(),
                table.flags(),
                table.visibility(),
                table.status(),
                table.gameId(),
                table.createdAt().toString(),
                memberDtos.size(),
                memberDtos,
                game
        );
    }
}
