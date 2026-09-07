UPDATE session_breakout_rooms
SET agora_channel_name = 'pm-bo-' || md5(session_id::text || ':' || id::text),
    updated_at = now()
WHERE agora_channel_name IS NULL
   OR octet_length(agora_channel_name) > 64;

ALTER TABLE session_breakout_rooms
    ALTER COLUMN agora_channel_name TYPE varchar(64);
