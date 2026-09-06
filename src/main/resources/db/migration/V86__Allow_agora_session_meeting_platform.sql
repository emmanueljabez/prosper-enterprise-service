ALTER TABLE sessions
    DROP CONSTRAINT IF EXISTS chk_sessions_meeting_platform;

ALTER TABLE sessions
    ADD CONSTRAINT chk_sessions_meeting_platform
        CHECK (meeting_platform IN ('GOOGLE_MEET', 'ZOOM', 'AGORA'));

COMMENT ON COLUMN sessions.meeting_platform IS 'Meeting platform: ZOOM, GOOGLE_MEET, or AGORA';
