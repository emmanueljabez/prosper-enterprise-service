ALTER TABLE company_program_mentor_assignments
    ADD COLUMN IF NOT EXISTS journey_instance_step_id UUID REFERENCES journey_instance_steps(id) ON DELETE CASCADE;

ALTER TABLE company_program_mentor_assignments
    DROP CONSTRAINT IF EXISTS uk_company_program_mentor_assignment_participant;

CREATE UNIQUE INDEX IF NOT EXISTS uk_company_program_mentor_assignment_program_scope
    ON company_program_mentor_assignments(participant_id)
    WHERE journey_instance_step_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_company_program_mentor_assignment_step_scope
    ON company_program_mentor_assignments(participant_id, journey_instance_step_id)
    WHERE journey_instance_step_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_company_program_mentor_assignments_step
    ON company_program_mentor_assignments(journey_instance_step_id);

COMMENT ON COLUMN company_program_mentor_assignments.journey_instance_step_id IS
    'Optional journey step scope for employee-selected mentors. NULL keeps the legacy program-level assignment.';
