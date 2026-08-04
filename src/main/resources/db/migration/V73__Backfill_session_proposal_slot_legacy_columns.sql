ALTER TABLE session_proposal_slots
    ADD COLUMN IF NOT EXISTS scheduled_start TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS scheduled_end TIMESTAMP WITH TIME ZONE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'session_proposal_slots'
          AND column_name = 'proposed_start'
    ) THEN
        UPDATE session_proposal_slots
        SET scheduled_start = proposed_start
        WHERE scheduled_start IS NULL
          AND proposed_start IS NOT NULL;

        ALTER TABLE session_proposal_slots
            ALTER COLUMN proposed_start DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'session_proposal_slots'
          AND column_name = 'proposed_end'
    ) THEN
        UPDATE session_proposal_slots
        SET scheduled_end = proposed_end
        WHERE scheduled_end IS NULL
          AND proposed_end IS NOT NULL;

        ALTER TABLE session_proposal_slots
            ALTER COLUMN proposed_end DROP NOT NULL;
    END IF;
END $$;
