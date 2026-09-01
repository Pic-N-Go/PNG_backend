ALTER TABLE community_posts
    ADD COLUMN technical_exif_consent VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN location_exif_consent VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE review
    ADD COLUMN technical_exif_consent VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN location_exif_consent VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';
