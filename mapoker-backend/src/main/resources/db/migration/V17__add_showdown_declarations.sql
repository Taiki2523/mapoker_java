-- オフラインモードのショーダウン宣言を保存するカラムを追加
-- { "0": true, "1": false } のように playerIndex → isWin を格納する
ALTER TABLE games ADD COLUMN IF NOT EXISTS showdown_declarations JSONB;
