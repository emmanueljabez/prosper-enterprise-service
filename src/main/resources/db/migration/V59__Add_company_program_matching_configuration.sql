ALTER TABLE company_programs
    ADD COLUMN IF NOT EXISTS employee_selection_window_hours INTEGER NOT NULL DEFAULT 48,
    ADD COLUMN IF NOT EXISTS employee_selection_shortlist_size INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS requires_mentor_for_session_steps BOOLEAN NOT NULL DEFAULT TRUE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_company_program_employee_selection_window_hours'
    ) THEN
        ALTER TABLE company_programs
            ADD CONSTRAINT chk_company_program_employee_selection_window_hours
                CHECK (employee_selection_window_hours BETWEEN 1 AND 168);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_company_program_employee_selection_shortlist_size'
    ) THEN
        ALTER TABLE company_programs
            ADD CONSTRAINT chk_company_program_employee_selection_shortlist_size
                CHECK (employee_selection_shortlist_size BETWEEN 1 AND 20);
    END IF;
END $$;

COMMENT ON COLUMN company_programs.employee_selection_window_hours IS 'Employee mentor-selection window in hours for EMPLOYEE_SELECT mode.';
COMMENT ON COLUMN company_programs.employee_selection_shortlist_size IS 'Shortlist size used to generate ranked mentor options for employee selection.';
COMMENT ON COLUMN company_programs.requires_mentor_for_session_steps IS 'When true, SESSION journey steps remain blocked until a mentor assignment exists.';
