ALTER TABLE notes
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN original_filename VARCHAR(500),
    ADD COLUMN mime_type VARCHAR(255),
    ADD COLUMN index_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE notes
    ADD CONSTRAINT ck_notes_source_type
        CHECK (source_type IN ('MANUAL', 'FILE')),
    ADD CONSTRAINT ck_notes_index_status
        CHECK (index_status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'));
