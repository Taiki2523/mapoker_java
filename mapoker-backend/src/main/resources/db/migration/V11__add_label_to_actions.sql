-- アクションテーブルに補足ラベルカラムを追加（ショーダウン役名など）
ALTER TABLE actions ADD COLUMN IF NOT EXISTS label VARCHAR(100);

-- action_type enum に showdown / payout を追加
ALTER TYPE action_type ADD VALUE IF NOT EXISTS 'showdown';
ALTER TYPE action_type ADD VALUE IF NOT EXISTS 'payout';
