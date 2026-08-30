ALTER TABLE community_posts
    DROP CONSTRAINT IF EXISTS chk_community_posts_content_present;

ALTER TABLE community_posts
    ADD CONSTRAINT chk_community_posts_body_or_attachment_present CHECK (
        length(trim(content)) > 0
        OR length(trim(COALESCE(media_url, ''))) > 0
        OR length(trim(COALESCE(image_url, ''))) > 0
        OR length(trim(COALESCE(link_url, ''))) > 0
    );
