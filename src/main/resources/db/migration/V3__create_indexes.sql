CREATE INDEX idx_translations_user_created 
    ON translations (user_id, created_at DESC);

CREATE INDEX idx_translations_target_language 
    ON translations (target_language);

CREATE INDEX idx_translations_metadata_gin 
    ON translations USING gin (metadata);

-- Enable pg_trgm for full text search operations
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_translations_source_text_trgm 
    ON translations USING gin (source_text gin_trgm_ops);
