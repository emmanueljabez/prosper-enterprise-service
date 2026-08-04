ALTER TABLE review_requests
    ADD COLUMN IF NOT EXISTS flow_token VARCHAR(190),
    ADD COLUMN IF NOT EXISTS submission_token VARCHAR(190);

CREATE UNIQUE INDEX IF NOT EXISTS uk_review_requests_flow_token
    ON review_requests(flow_token)
    WHERE flow_token IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_review_requests_submission_token
    ON review_requests(submission_token)
    WHERE submission_token IS NOT NULL;

CREATE TABLE IF NOT EXISTS review_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_request_id UUID NOT NULL REFERENCES review_requests(id) ON DELETE CASCADE,
    question_code VARCHAR(80) NOT NULL,
    answer_type VARCHAR(16) NOT NULL,
    numeric_score INTEGER,
    boolean_answer BOOLEAN,
    text_answer TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_review_answers_request_question UNIQUE (review_request_id, question_code),
    CONSTRAINT chk_review_answers_type CHECK (answer_type IN ('SCORE', 'BOOLEAN', 'TEXT')),
    CONSTRAINT chk_review_answers_numeric_score CHECK (numeric_score IS NULL OR (numeric_score >= 1 AND numeric_score <= 5))
);

CREATE INDEX IF NOT EXISTS idx_review_answers_request
    ON review_answers(review_request_id, sort_order ASC);

CREATE OR REPLACE FUNCTION update_review_answers_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_review_answers_updated_at ON review_answers;
CREATE TRIGGER trigger_review_answers_updated_at
    BEFORE UPDATE ON review_answers
    FOR EACH ROW
    EXECUTE FUNCTION update_review_answers_updated_at();

COMMENT ON TABLE review_answers IS 'Structured answers captured from WhatsApp review flow submissions';
