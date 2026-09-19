CREATE TABLE spot_accessibility_info (
    id BIGINT NOT NULL AUTO_INCREMENT,
    spot_id BIGINT NOT NULL,
    parking TEXT NULL, public_transport TEXT NULL, route TEXT NULL, ticket_office TEXT NULL,
    promotion TEXT NULL, wheelchair TEXT NULL, entrance_exit TEXT NULL, elevator TEXT NULL,
    restroom TEXT NULL, auditorium TEXT NULL, room TEXT NULL, physical_disability_etc TEXT NULL,
    braille_block TEXT NULL, help_dog TEXT NULL, human_guide TEXT NULL, audio_guide TEXT NULL,
    large_print TEXT NULL, braille_promotion TEXT NULL, guide_system TEXT NULL, visual_disability_etc TEXT NULL,
    sign_guide TEXT NULL, video_guide TEXT NULL, hearing_room TEXT NULL, hearing_disability_etc TEXT NULL,
    stroller TEXT NULL, lactation_room TEXT NULL, baby_chair TEXT NULL, infant_family_etc TEXT NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_spot_accessibility_info_spot UNIQUE (spot_id),
    CONSTRAINT fk_spot_accessibility_info_spot FOREIGN KEY (spot_id) REFERENCES spot (id)
);
