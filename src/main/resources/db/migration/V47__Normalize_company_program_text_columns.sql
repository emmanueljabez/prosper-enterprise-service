DO $$
DECLARE
    objective_type TEXT;
    target_audience_type TEXT;
BEGIN
    SELECT data_type
    INTO objective_type
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'company_programs'
      AND column_name = 'objective';

    IF objective_type = 'bytea' THEN
        EXECUTE $sql$
            ALTER TABLE company_programs
            ALTER COLUMN objective TYPE TEXT
            USING CASE
                WHEN objective IS NULL THEN NULL
                ELSE convert_from(objective, 'UTF8')
            END
        $sql$;
    END IF;

    SELECT data_type
    INTO target_audience_type
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'company_programs'
      AND column_name = 'target_audience_description';

    IF target_audience_type = 'bytea' THEN
        EXECUTE $sql$
            ALTER TABLE company_programs
            ALTER COLUMN target_audience_description TYPE TEXT
            USING CASE
                WHEN target_audience_description IS NULL THEN NULL
                ELSE convert_from(target_audience_description, 'UTF8')
            END
        $sql$;
    END IF;
END $$;
