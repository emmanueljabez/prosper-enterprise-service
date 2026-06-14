CREATE TABLE IF NOT EXISTS company_program_catalog_programs (
    id UUID PRIMARY KEY,
    company_program_id UUID NOT NULL REFERENCES company_programs(id) ON DELETE CASCADE,
    program_id UUID NOT NULL REFERENCES programs(id) ON DELETE RESTRICT,
    journey_order INTEGER NOT NULL,
    journey_stage_name VARCHAR(255),
    stage_type VARCHAR(20) NOT NULL DEFAULT 'CORE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_company_program_catalog_programs_order UNIQUE (company_program_id, journey_order)
);

CREATE INDEX IF NOT EXISTS idx_company_program_catalog_programs_program
    ON company_program_catalog_programs(program_id);

CREATE INDEX IF NOT EXISTS idx_company_program_catalog_programs_company_program
    ON company_program_catalog_programs(company_program_id, journey_order);

INSERT INTO company_program_catalog_programs (
    id,
    company_program_id,
    program_id,
    journey_order,
    journey_stage_name,
    stage_type,
    created_at
)
SELECT
    gen_random_uuid(),
    cp.id,
    cp.program_id,
    1,
    NULL,
    'CORE',
    COALESCE(cp.created_at, CURRENT_TIMESTAMP)
FROM company_programs cp
WHERE cp.program_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM company_program_catalog_programs cpcp
      WHERE cpcp.company_program_id = cp.id
  );
