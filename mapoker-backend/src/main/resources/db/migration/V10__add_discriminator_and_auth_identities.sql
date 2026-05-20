-- pgcrypto (gen_random_uuid) が未導入の場合のみ
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- V9 で追加した google_id を削除（user_auth_identities に移行）
ALTER TABLE users DROP COLUMN IF EXISTS google_id;

-- username の UNIQUE 制約を削除 → username + discriminator の複合ユニークに変更
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;

-- discriminator カラムを追加（既存ユーザーは '0000' を初期値とする）
ALTER TABLE users ADD COLUMN IF NOT EXISTS discriminator VARCHAR(4) NOT NULL DEFAULT '0000';
ALTER TABLE users ADD CONSTRAINT uq_users_username_discriminator UNIQUE (username, discriminator);
ALTER TABLE users ADD CONSTRAINT chk_users_discriminator_format CHECK (discriminator ~ '^[0-9]{4}$');

-- public_id カラムを追加（既存ユーザーは UUID を自動付与）
ALTER TABLE users ADD COLUMN IF NOT EXISTS public_id UUID UNIQUE DEFAULT gen_random_uuid();
ALTER TABLE users ALTER COLUMN public_id SET NOT NULL;

-- avatar_url カラムを追加
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT;

-- user_auth_identities テーブルを作成
CREATE TABLE IF NOT EXISTS user_auth_identities (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         VARCHAR(50)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_auth_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_user_auth_identities_user_id
    ON user_auth_identities (user_id);
