CREATE TABLE IF NOT EXISTS journey_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(160) NOT NULL,
    program_type VARCHAR(64),
    description TEXT,
    default_duration_weeks INTEGER,
    template_version INTEGER NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    template_snapshot_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS journey_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journey_template_id UUID NOT NULL REFERENCES journey_templates(id) ON DELETE CASCADE,
    step_key VARCHAR(96) NOT NULL,
    default_sequence INTEGER NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    step_type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    default_due_offset_days INTEGER,
    step_config_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_journey_steps_template_key UNIQUE (journey_template_id, step_key),
    CONSTRAINT chk_journey_steps_type CHECK (
        step_type IN ('SESSION', 'CHECK_IN', 'ACTION_ITEM', 'SURVEY', 'REFLECTION')
    )
);

CREATE TABLE IF NOT EXISTS journey_step_dependencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journey_template_id UUID NOT NULL REFERENCES journey_templates(id) ON DELETE CASCADE,
    from_step_id UUID NOT NULL REFERENCES journey_steps(id) ON DELETE CASCADE,
    to_step_id UUID NOT NULL REFERENCES journey_steps(id) ON DELETE CASCADE,
    dependency_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_journey_step_dependencies UNIQUE (journey_template_id, from_step_id, to_step_id),
    CONSTRAINT chk_journey_step_dependency_type CHECK (
        dependency_type IN ('FINISH_TO_START', 'OPTIONAL_GATE')
    )
);

ALTER TABLE company_programs
    ADD COLUMN IF NOT EXISTS journey_template_id UUID REFERENCES journey_templates(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_company_programs_journey_template
    ON company_programs(journey_template_id);

CREATE TABLE IF NOT EXISTS journey_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id UUID NOT NULL REFERENCES company_program_participants(id) ON DELETE CASCADE,
    journey_template_id UUID NOT NULL REFERENCES journey_templates(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    progress_percent INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_journey_instances_participant UNIQUE (participant_id),
    CONSTRAINT chk_journey_instances_status CHECK (
        status IN ('NOT_STARTED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_journey_instances_participant
    ON journey_instances(participant_id);

CREATE INDEX IF NOT EXISTS idx_journey_instances_template_status
    ON journey_instances(journey_template_id, status);

CREATE TABLE IF NOT EXISTS journey_instance_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journey_instance_id UUID NOT NULL REFERENCES journey_instances(id) ON DELETE CASCADE,
    journey_step_id UUID NOT NULL REFERENCES journey_steps(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    due_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    skipped_reason TEXT,
    blocked_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_journey_instance_steps UNIQUE (journey_instance_id, journey_step_id),
    CONSTRAINT chk_journey_instance_steps_status CHECK (
        status IN ('PENDING', 'READY', 'COMPLETED', 'SKIPPED', 'BLOCKED')
    )
);

CREATE INDEX IF NOT EXISTS idx_journey_instance_steps_instance_status
    ON journey_instance_steps(journey_instance_id, status);

CREATE INDEX IF NOT EXISTS idx_journey_instance_steps_due_at
    ON journey_instance_steps(due_at);

CREATE OR REPLACE FUNCTION update_journey_templates_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_journey_steps_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_journey_instances_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_journey_instance_steps_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_journey_templates_updated_at ON journey_templates;
CREATE TRIGGER trigger_journey_templates_updated_at
    BEFORE UPDATE ON journey_templates
    FOR EACH ROW
    EXECUTE FUNCTION update_journey_templates_updated_at();

DROP TRIGGER IF EXISTS trigger_journey_steps_updated_at ON journey_steps;
CREATE TRIGGER trigger_journey_steps_updated_at
    BEFORE UPDATE ON journey_steps
    FOR EACH ROW
    EXECUTE FUNCTION update_journey_steps_updated_at();

DROP TRIGGER IF EXISTS trigger_journey_instances_updated_at ON journey_instances;
CREATE TRIGGER trigger_journey_instances_updated_at
    BEFORE UPDATE ON journey_instances
    FOR EACH ROW
    EXECUTE FUNCTION update_journey_instances_updated_at();

DROP TRIGGER IF EXISTS trigger_journey_instance_steps_updated_at ON journey_instance_steps;
CREATE TRIGGER trigger_journey_instance_steps_updated_at
    BEFORE UPDATE ON journey_instance_steps
    FOR EACH ROW
    EXECUTE FUNCTION update_journey_instance_steps_updated_at();

COMMENT ON TABLE journey_templates IS 'Reusable mentorship journey definitions that can be attached to company programs';
COMMENT ON TABLE journey_steps IS 'Milestones that define a guided mentorship journey';
COMMENT ON TABLE journey_step_dependencies IS 'Dependencies between journey steps for linear and future branching support';
COMMENT ON TABLE journey_instances IS 'Runtime journey execution records for enrolled employees';
COMMENT ON TABLE journey_instance_steps IS 'Step-level progression records for a journey instance';

INSERT INTO journey_templates (
    id, name, program_type, description, default_duration_weeks, template_version, is_active, template_snapshot_json
) VALUES
    (
        '7ef5d59b-3121-4f7d-8c29-9ce0d3991001',
        'New Hire Onboarding Journey',
        'ONBOARDING',
        'A five-step journey for new hires to build context, set goals, and apply mentor guidance in their first month.',
        6,
        1,
        TRUE,
        '{"preset":"onboarding","stepCount":5}'
    ),
    (
        '7ef5d59b-3121-4f7d-8c29-9ce0d3991002',
        'First-Time Manager Journey',
        'MANAGER_ENABLEMENT',
        'A structured journey for new managers to establish team rhythms, reflection habits, and leadership confidence.',
        8,
        1,
        TRUE,
        '{"preset":"first_time_manager","stepCount":5}'
    ),
    (
        '7ef5d59b-3121-4f7d-8c29-9ce0d3991003',
        'High Potential Growth Journey',
        'HIGH_POTENTIAL',
        'A milestone-based journey for ambitious employees preparing for stretch opportunities and internal mobility.',
        8,
        1,
        TRUE,
        '{"preset":"high_potential","stepCount":5}'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO journey_steps (
    id, journey_template_id, step_key, default_sequence, title, description, step_type, required, default_due_offset_days, step_config_json
) VALUES
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992001', '7ef5d59b-3121-4f7d-8c29-9ce0d3991001', 'kickoff_session', 1, 'Kickoff Session', 'Meet your mentor, align on context, and define what a strong onboarding experience should look like.', 'SESSION', TRUE, 0, '{"recommendedDurationMinutes":60}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992002', '7ef5d59b-3121-4f7d-8c29-9ce0d3991001', 'goals_reflection', 2, 'Goals Reflection', 'Capture the first three priorities you want your mentorship journey to accelerate.', 'REFLECTION', TRUE, 3, '{"employeeCompletable":true}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992003', '7ef5d59b-3121-4f7d-8c29-9ce0d3991001', 'momentum_check_in', 3, 'Momentum Check-In', 'Review what is landing well and where you need more support before the next session.', 'CHECK_IN', TRUE, 14, '{"employeeCompletable":true}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992004', '7ef5d59b-3121-4f7d-8c29-9ce0d3991001', 'growth_session', 4, 'Growth Session', 'Use the second mentor session to work through blockers and strengthen confidence in role execution.', 'SESSION', TRUE, 21, '{"recommendedDurationMinutes":60}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992005', '7ef5d59b-3121-4f7d-8c29-9ce0d3991001', 'final_reflection', 5, 'Final Reflection', 'Summarize the advice you will keep using and the next actions you own after the journey closes.', 'REFLECTION', TRUE, 35, '{"employeeCompletable":true}'),

    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992101', '7ef5d59b-3121-4f7d-8c29-9ce0d3991002', 'leadership_kickoff', 1, 'Leadership Kickoff', 'Use the first session to define management expectations, team risks, and the support your mentor can provide.', 'SESSION', TRUE, 0, '{"recommendedDurationMinutes":60}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992102', '7ef5d59b-3121-4f7d-8c29-9ce0d3991002', 'manager_observation', 2, 'Manager Reflection', 'Write down the patterns you are seeing in your team and where you need more confidence as a manager.', 'REFLECTION', TRUE, 5, '{"employeeCompletable":true}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992103', '7ef5d59b-3121-4f7d-8c29-9ce0d3991002', 'feedback_rhythm', 3, 'Feedback Rhythm Check-In', 'Confirm the manager habits, communication routines, and team touchpoints you are testing.', 'CHECK_IN', TRUE, 14, '{"employeeCompletable":true}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992104', '7ef5d59b-3121-4f7d-8c29-9ce0d3991002', 'coaching_session', 4, 'Coaching Session', 'Bring a real management challenge back to your mentor and pressure-test your response.', 'SESSION', TRUE, 21, '{"recommendedDurationMinutes":60}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992105', '7ef5d59b-3121-4f7d-8c29-9ce0d3991002', 'leadership_commitments', 5, 'Leadership Commitments', 'Finish with a short action plan for how you will lead meetings, feedback, and one-on-ones going forward.', 'ACTION_ITEM', TRUE, 35, '{"employeeCompletable":true}'),

    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992201', '7ef5d59b-3121-4f7d-8c29-9ce0d3991003', 'career_vision_session', 1, 'Career Vision Session', 'Use your first session to define the role stretch, visibility gaps, and growth opportunities you want to pursue.', 'SESSION', TRUE, 0, '{"recommendedDurationMinutes":60}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992202', '7ef5d59b-3121-4f7d-8c29-9ce0d3991003', 'growth_goal_map', 2, 'Growth Goal Map', 'Translate the session into two or three concrete development goals you can track over the next month.', 'ACTION_ITEM', TRUE, 5, '{"employeeCompletable":true}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992203', '7ef5d59b-3121-4f7d-8c29-9ce0d3991003', 'sponsor_alignment', 3, 'Sponsor Alignment Check-In', 'Check whether your goals now match the visibility and sponsorship you need inside the company.', 'CHECK_IN', TRUE, 14, '{"employeeCompletable":true}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992204', '7ef5d59b-3121-4f7d-8c29-9ce0d3991003', 'stretch_debrief_session', 4, 'Stretch Debrief Session', 'Bring a real stretch assignment or leadership moment into the second session and get direct guidance.', 'SESSION', TRUE, 21, '{"recommendedDurationMinutes":60}'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3992205', '7ef5d59b-3121-4f7d-8c29-9ce0d3991003', 'next_move_reflection', 5, 'Next Move Reflection', 'Close the journey by recording the next move, sponsor ask, or opportunity you will pursue next.', 'REFLECTION', TRUE, 35, '{"employeeCompletable":true}')
ON CONFLICT (id) DO NOTHING;

INSERT INTO journey_step_dependencies (id, journey_template_id, from_step_id, to_step_id, dependency_type) VALUES
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993001', '7ef5d59b-3121-4f7d-8c29-9ce0d3991001', '7ef5d59b-3121-4f7d-8c29-9ce0d3992001', '7ef5d59b-3121-4f7d-8c29-9ce0d3992002', 'FINISH_TO_START'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993002', '7ef5d59b-3121-4f7d-8c29-9ce0d3991001', '7ef5d59b-3121-4f7d-8c29-9ce0d3992002', '7ef5d59b-3121-4f7d-8c29-9ce0d3992003', 'FINISH_TO_START'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993003', '7ef5d59b-3121-4f7d-8c29-9ce0d3991001', '7ef5d59b-3121-4f7d-8c29-9ce0d3992003', '7ef5d59b-3121-4f7d-8c29-9ce0d3992004', 'FINISH_TO_START'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993004', '7ef5d59b-3121-4f7d-8c29-9ce0d3991001', '7ef5d59b-3121-4f7d-8c29-9ce0d3992004', '7ef5d59b-3121-4f7d-8c29-9ce0d3992005', 'FINISH_TO_START'),

    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993101', '7ef5d59b-3121-4f7d-8c29-9ce0d3991002', '7ef5d59b-3121-4f7d-8c29-9ce0d3992101', '7ef5d59b-3121-4f7d-8c29-9ce0d3992102', 'FINISH_TO_START'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993102', '7ef5d59b-3121-4f7d-8c29-9ce0d3991002', '7ef5d59b-3121-4f7d-8c29-9ce0d3992102', '7ef5d59b-3121-4f7d-8c29-9ce0d3992103', 'FINISH_TO_START'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993103', '7ef5d59b-3121-4f7d-8c29-9ce0d3991002', '7ef5d59b-3121-4f7d-8c29-9ce0d3992103', '7ef5d59b-3121-4f7d-8c29-9ce0d3992104', 'FINISH_TO_START'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993104', '7ef5d59b-3121-4f7d-8c29-9ce0d3991002', '7ef5d59b-3121-4f7d-8c29-9ce0d3992104', '7ef5d59b-3121-4f7d-8c29-9ce0d3992105', 'FINISH_TO_START'),

    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993201', '7ef5d59b-3121-4f7d-8c29-9ce0d3991003', '7ef5d59b-3121-4f7d-8c29-9ce0d3992201', '7ef5d59b-3121-4f7d-8c29-9ce0d3992202', 'FINISH_TO_START'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993202', '7ef5d59b-3121-4f7d-8c29-9ce0d3991003', '7ef5d59b-3121-4f7d-8c29-9ce0d3992202', '7ef5d59b-3121-4f7d-8c29-9ce0d3992203', 'FINISH_TO_START'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993203', '7ef5d59b-3121-4f7d-8c29-9ce0d3991003', '7ef5d59b-3121-4f7d-8c29-9ce0d3992203', '7ef5d59b-3121-4f7d-8c29-9ce0d3992204', 'FINISH_TO_START'),
    ('7ef5d59b-3121-4f7d-8c29-9ce0d3993204', '7ef5d59b-3121-4f7d-8c29-9ce0d3991003', '7ef5d59b-3121-4f7d-8c29-9ce0d3992204', '7ef5d59b-3121-4f7d-8c29-9ce0d3992205', 'FINISH_TO_START')
ON CONFLICT (id) DO NOTHING;
