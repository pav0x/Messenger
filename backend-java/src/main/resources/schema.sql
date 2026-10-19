-- ============================================================
-- Схема БД мессенджера (личные и групповые чаты, статусы сообщений).
--
-- Spring Boot прогоняет этот файл автоматически при каждом старте
-- (см. spring.sql.init.mode=always в application.properties), поэтому
-- везде IF NOT EXISTS — повторный запуск не должен ничего ломать.
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128) NOT NULL,
    is_online     BOOLEAN NOT NULL DEFAULT FALSE
);

-- Один чат — либо личный (is_group = false, ровно два участника),
-- либо групповой (is_group = true, участников двое и больше).
-- title и creator_id заполнены только у групповых чатов: creator_id — тот,
-- кто создал группу и только он может ей управлять (добавлять/удалять
-- участников, переименовывать, удалять группу целиком).
CREATE TABLE IF NOT EXISTS chats (
    id         BIGSERIAL PRIMARY KEY,
    is_group   BOOLEAN NOT NULL,
    title      VARCHAR(255),
    creator_id BIGINT REFERENCES users(id) ON DELETE SET NULL
);

-- ADD COLUMN IF NOT EXISTS — на случай, если таблица chats была создана ещё
-- до появления этой колонки (у тех, кто уже запускал проект раньше).
ALTER TABLE chats ADD COLUMN IF NOT EXISTS creator_id BIGINT REFERENCES users(id) ON DELETE SET NULL;

-- Участники чата — общая таблица и для личных, и для групповых чатов.
CREATE TABLE IF NOT EXISTS chat_members (
    chat_id BIGINT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (chat_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_members_user_id ON chat_members(user_id);

-- Индекс только для личных чатов: чтобы не плодить дубли, когда одни и те
-- же два человека продолжают переписку — findOrCreateDirect ищет здесь
-- существующий чат по паре (меньший_id, больший_id), прежде чем создавать новый.
CREATE TABLE IF NOT EXISTS direct_chat_pairs (
    user_a_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_id   BIGINT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    PRIMARY KEY (user_a_id, user_b_id)
);

CREATE TABLE IF NOT EXISTS messages (
    id         BIGSERIAL PRIMARY KEY,
    chat_id    BIGINT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content    TEXT NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'SENT', -- SENT / DELIVERED / READ
    created_at BIGINT NOT NULL -- unix-время в секундах, как и раньше в коде
);

CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);
