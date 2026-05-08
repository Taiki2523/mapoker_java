package com.mapoker.domain.hand;

import com.mapoker.domain.card.Rank;
import java.util.List;

/**
 * 役とキッカー情報を保持するレコードです。
 *
 * @param rank 役
 * @param kickers 比較用のランク一覧
 */
public record HandValue(HandRank rank, List<Rank> kickers) {

    /**
     * 別の役と強さを比較します。
     *
     * @param other 比較対象の役
     * @return この役が強ければ正、弱ければ負、同値なら 0
     */
    public int compareTo(HandValue other) {
        if (this.rank != other.rank) {
            return this.rank.ordinal() - other.rank.ordinal();
        }
        int max = Math.min(this.kickers.size(), other.kickers.size());
        for (int i = 0; i < max; i++) {
            int cmp = this.kickers.get(i).getValue() - other.kickers.get(i).getValue();
            if (cmp != 0) return cmp;
        }
        return 0;
    }
}
