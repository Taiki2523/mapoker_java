package com.mapoker.domain.hand;

import com.mapoker.domain.card.Rank;
import java.util.List;

public record HandValue(HandRank rank, List<Rank> kickers) {

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
