ALTER TABLE journey_templates
    ADD COLUMN IF NOT EXISTS cover_image_url VARCHAR(1024);
