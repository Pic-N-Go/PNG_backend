CREATE TABLE spot_pet_info (
    id BIGINT NOT NULL AUTO_INCREMENT,
    spot_id BIGINT NOT NULL,
    accompany_type VARCHAR(100) NULL,
    allowed_companion VARCHAR(255) NULL,
    required_items TEXT NULL,
    additional_info TEXT NULL,
    facilities TEXT NULL,
    provided_items TEXT NULL,
    purchasable_items TEXT NULL,
    rental_items TEXT NULL,
    accident_risk_info TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_spot_pet_info_spot UNIQUE (spot_id),
    CONSTRAINT fk_spot_pet_info_spot FOREIGN KEY (spot_id) REFERENCES spot (id)
);
