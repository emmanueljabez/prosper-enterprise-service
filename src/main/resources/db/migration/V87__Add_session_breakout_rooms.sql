CREATE TABLE IF NOT EXISTS session_breakout_rooms (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id uuid NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    agora_channel_name varchar(180) NOT NULL UNIQUE,
    status varchar(30) NOT NULL DEFAULT 'DRAFT',
    created_by uuid NOT NULL REFERENCES profiles(id),
    opened_at timestamptz,
    closed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS session_breakout_participants (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id uuid NOT NULL REFERENCES session_breakout_rooms(id) ON DELETE CASCADE,
    session_id uuid NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    profile_id uuid NOT NULL REFERENCES profiles(id),
    status varchar(30) NOT NULL DEFAULT 'ASSIGNED',
    joined_at timestamptz,
    left_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_session_breakout_rooms_session_id
    ON session_breakout_rooms(session_id);

CREATE INDEX IF NOT EXISTS idx_session_breakout_participants_session_id
    ON session_breakout_participants(session_id);

CREATE INDEX IF NOT EXISTS idx_session_breakout_participants_room_id
    ON session_breakout_participants(room_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_session_breakout_active_assignment
    ON session_breakout_participants(session_id, profile_id)
    WHERE status IN ('ASSIGNED', 'JOINED');
