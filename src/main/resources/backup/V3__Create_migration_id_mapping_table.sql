-- Create migration_id_mapping table for tracking MongoDB ObjectIDs to Supabase UUIDs
-- SAFE MODE: Only create if it doesn't exist
CREATE TABLE IF NOT EXISTS migration_id_mapping (
    id BIGSERIAL PRIMARY KEY,
    old_mongodb_id VARCHAR(50) NOT NULL,
    new_supabase_id VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    migration_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Create indexes for efficient lookups during migration (only if they don't exist)
CREATE INDEX IF NOT EXISTS idx_migration_old_id ON migration_id_mapping(old_mongodb_id);
CREATE INDEX IF NOT EXISTS idx_migration_entity_type ON migration_id_mapping(entity_type);
CREATE INDEX IF NOT EXISTS idx_migration_old_id_entity ON migration_id_mapping(old_mongodb_id, entity_type);

-- Create unique constraint to prevent duplicate mappings (only if it doesn't exist)
CREATE UNIQUE INDEX IF NOT EXISTS idx_migration_unique_mapping ON migration_id_mapping(old_mongodb_id, entity_type);

-- Create trigger function if it doesn't exist
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to automatically update updated_at (only if it doesn't exist)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger 
        WHERE tgname = 'update_migration_id_mapping_updated_at'
    ) THEN
        CREATE TRIGGER update_migration_id_mapping_updated_at 
            BEFORE UPDATE ON migration_id_mapping 
            FOR EACH ROW 
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- Add comments for documentation
COMMENT ON TABLE migration_id_mapping IS 'Maps MongoDB ObjectIDs to Supabase UUIDs during data migration';
COMMENT ON COLUMN migration_id_mapping.old_mongodb_id IS 'Original MongoDB ObjectID as string';
COMMENT ON COLUMN migration_id_mapping.new_supabase_id IS 'New Supabase UUID or ID as string';
COMMENT ON COLUMN migration_id_mapping.entity_type IS 'Type of entity (users, topics, sessions, etc.)';
COMMENT ON COLUMN migration_id_mapping.migration_timestamp IS 'When this mapping was created during migration';
