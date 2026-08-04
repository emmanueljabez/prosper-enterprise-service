CREATE TABLE IF NOT EXISTS company_recommended_programs (
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    program_id UUID NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    PRIMARY KEY (company_id, program_id)
);

CREATE INDEX IF NOT EXISTS idx_company_recommended_programs_program_id
    ON company_recommended_programs (program_id);
