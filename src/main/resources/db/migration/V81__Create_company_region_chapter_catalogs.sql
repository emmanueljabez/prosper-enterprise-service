CREATE TABLE IF NOT EXISTS company_regions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_company_regions_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_regions_company_name
    ON company_regions (company_id, LOWER(name));

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_regions_company_code
    ON company_regions (company_id, LOWER(code))
    WHERE code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_company_regions_company_created
    ON company_regions (company_id, created_at DESC);

CREATE TABLE IF NOT EXISTS company_chapters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    region_id UUID REFERENCES company_regions(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_company_chapters_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_chapters_company_name
    ON company_chapters (company_id, LOWER(name));

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_chapters_company_code
    ON company_chapters (company_id, LOWER(code))
    WHERE code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_company_chapters_company_created
    ON company_chapters (company_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_company_chapters_region
    ON company_chapters (region_id);

CREATE OR REPLACE FUNCTION update_company_regions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_company_regions_updated_at ON company_regions;
CREATE TRIGGER trigger_company_regions_updated_at
    BEFORE UPDATE ON company_regions
    FOR EACH ROW
    EXECUTE FUNCTION update_company_regions_updated_at();

CREATE OR REPLACE FUNCTION update_company_chapters_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_company_chapters_updated_at ON company_chapters;
CREATE TRIGGER trigger_company_chapters_updated_at
    BEFORE UPDATE ON company_chapters
    FOR EACH ROW
    EXECUTE FUNCTION update_company_chapters_updated_at();

COMMENT ON TABLE company_regions IS 'Company-managed region catalog for cohort delivery and reporting';
COMMENT ON TABLE company_chapters IS 'Company-managed chapter catalog for cohort delivery and reporting';
