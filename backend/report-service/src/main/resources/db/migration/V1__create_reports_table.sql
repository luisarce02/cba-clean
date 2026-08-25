CREATE TABLE reports (
    id               UUID PRIMARY KEY,
    type             VARCHAR(32)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    priority         VARCHAR(16)  NOT NULL,
    description      VARCHAR(2000),
    closing_note     TEXT,
    latitude         DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude        DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    address          VARCHAR(300),
    reporter_name    VARCHAR(100),
    reporter_email   VARCHAR(200),
    reporter_phone   VARCHAR(25),
    photo_ids        TEXT[]       NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at       TIMESTAMPTZ  NOT NULL,
    last_modified_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_reporter_contact
        CHECK (reporter_email IS NOT NULL OR reporter_phone IS NOT NULL OR reporter_name IS NULL)
);

CREATE INDEX idx_reports_status ON reports (status);
CREATE INDEX idx_reports_created_at ON reports (created_at);
