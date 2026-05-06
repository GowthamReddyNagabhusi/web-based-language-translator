CREATE TABLE translations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_text TEXT NOT NULL,
    translated_text TEXT NOT NULL,
    source_language VARCHAR(10),
    target_language VARCHAR(10) NOT NULL,
    provider_used VARCHAR(50),
    is_cached BOOLEAN DEFAULT false,
    is_favorite BOOLEAN DEFAULT false,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
