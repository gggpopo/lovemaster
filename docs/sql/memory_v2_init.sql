-- =========================================================
-- Memory v2 初始化脚本（PostgreSQL + pgvector）
-- 目标：
-- 1) 兼容当前项目的 conversation_memory_store（用于现有向量记忆链路）
-- 2) 预置结构化记忆表 memory_record_v2 + memory_embedding_v2（用于后续优化）
-- =========================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------
-- 1) 当前项目兼容表：conversation_memory_store
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversation_memory_store (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content text,
    metadata json,
    embedding vector(2048)
);

CREATE INDEX IF NOT EXISTS idx_conversation_memory_store_embedding_hnsw
    ON conversation_memory_store USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_conversation_memory_store_metadata_gin
    ON conversation_memory_store USING gin ((metadata::jsonb));

-- ---------------------------------------------------------
-- 2) 结构化记忆主表：memory_record_v2
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS memory_record_v2 (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id varchar(64) NOT NULL,
    conversation_id varchar(64) NOT NULL,
    scene_id varchar(64),
    scene_stage varchar(32),
    memory_type varchar(32) NOT NULL,
    content text NOT NULL,
    importance numeric(4,3) NOT NULL DEFAULT 0.500,
    confidence numeric(4,3) NOT NULL DEFAULT 0.500,
    source_turn_start int,
    source_turn_end int,
    feedback_score int NOT NULL DEFAULT 0,
    is_deleted boolean NOT NULL DEFAULT false,
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_memory_record_v2_user_created
    ON memory_record_v2 (user_id, created_at DESC)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_memory_record_v2_conversation
    ON memory_record_v2 (conversation_id, created_at DESC)
    WHERE is_deleted = false;

-- ---------------------------------------------------------
-- 3) 结构化记忆向量表：memory_embedding_v2
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS memory_embedding_v2 (
    id bigserial PRIMARY KEY,
    memory_id uuid NOT NULL REFERENCES memory_record_v2(id) ON DELETE CASCADE,
    user_id varchar(64) NOT NULL,
    conversation_id varchar(64) NOT NULL,
    memory_type varchar(32) NOT NULL,
    importance numeric(4,3) NOT NULL DEFAULT 0.500,
    created_at timestamptz NOT NULL DEFAULT now(),
    content text NOT NULL,
    embedding vector(2048) NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_memory_embedding_v2_user_created
    ON memory_embedding_v2 (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_memory_embedding_v2_conversation
    ON memory_embedding_v2 (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_memory_embedding_v2_hnsw
    ON memory_embedding_v2 USING hnsw (embedding vector_cosine_ops);

COMMIT;

-- ---------------------------------------------------------
-- 4) 可选：快速写入一条测试数据（手动执行）
-- ---------------------------------------------------------
-- INSERT INTO conversation_memory_store (content, metadata, embedding)
-- VALUES (
--   '用户: 我喜欢西湖附近约会，预算300',
--   '{"conversation_id":"mem_demo_001","memory_type":"conversation","user_id":"u001"}'::json,
--   ('[' || array_to_string(array_fill(0.001::real, ARRAY[2048]), ',') || ']')::vector(2048)
-- );
BEGIN;

-- 1) conversation_memory_store
DO $$
    BEGIN
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'conversation_memory_store') THEN
            TRUNCATE TABLE conversation_memory_store;
            ALTER TABLE conversation_memory_store DROP COLUMN IF EXISTS embedding;
            ALTER TABLE conversation_memory_store ADD COLUMN embedding vector(2048);
            DROP INDEX IF EXISTS idx_conversation_memory_store_embedding_hnsw;
            CREATE INDEX idx_conversation_memory_store_embedding_hnsw
                ON conversation_memory_store USING hnsw (embedding vector_cosine_ops);
        END IF;
    END $$;
-- 3) memory_embedding_v2
DO $$
    BEGIN
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'memory_embedding_v2') THEN
            TRUNCATE TABLE memory_embedding_v2;
            ALTER TABLE memory_embedding_v2 DROP COLUMN IF EXISTS embedding;
            ALTER TABLE memory_embedding_v2 ADD COLUMN embedding vector(2048);
            DROP INDEX IF EXISTS idx_memory_embedding_v2_hnsw;
            CREATE INDEX idx_memory_embedding_v2_hnsw
                ON memory_embedding_v2 USING hnsw (embedding vector_cosine_ops);
        END IF;
    END $$;

COMMIT;
