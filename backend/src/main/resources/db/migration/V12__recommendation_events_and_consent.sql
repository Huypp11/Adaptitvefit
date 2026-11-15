CREATE TABLE recommendation_data_consents (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id            BIGINT NOT NULL,
  policy_version     VARCHAR(40) NOT NULL,
  granted            BIT(1) NOT NULL,
  recorded_at        DATETIME(6) NOT NULL,
  INDEX idx_data_consent_user_recorded (user_id, recorded_at, id),
  CONSTRAINT fk_data_consent_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recommendation_events (
  id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id                    BIGINT NOT NULL,
  recommendation_id          BIGINT NULL,
  workout_session_id         BIGINT NULL,
  tracking_id                VARCHAR(80) NOT NULL,
  event_type                 VARCHAR(32) NOT NULL,
  recommendation_mode        VARCHAR(24) NOT NULL,
  idempotency_key            VARCHAR(191) NOT NULL,
  payload_json               VARCHAR(2000) NULL,
  occurred_at                DATETIME(6) NOT NULL,
  CONSTRAINT uq_recommendation_event_idempotency UNIQUE (user_id, idempotency_key),
  INDEX idx_recommendation_event_window (occurred_at, event_type),
  INDEX idx_recommendation_event_tracking (user_id, tracking_id, occurred_at),
  INDEX idx_recommendation_event_recommendation (recommendation_id, occurred_at),
  INDEX idx_recommendation_event_session (workout_session_id, occurred_at),
  CONSTRAINT fk_recommendation_event_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_recommendation_event_recommendation FOREIGN KEY (recommendation_id)
    REFERENCES recommendation_histories(id) ON DELETE CASCADE,
  CONSTRAINT fk_recommendation_event_session FOREIGN KEY (workout_session_id)
    REFERENCES workout_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
