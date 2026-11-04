CREATE TABLE recommendation_histories (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id            BIGINT NOT NULL,
  checkin_id         BIGINT NOT NULL,
  risk_prediction_id BIGINT NULL,
  snapshot_json      VARCHAR(16000) NOT NULL,
  created_at         DATETIME(6) NOT NULL,
  CONSTRAINT uq_recommendation_checkin UNIQUE (checkin_id),
  INDEX idx_recommendation_user_created (user_id, created_at),
  CONSTRAINT fk_recommendation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_recommendation_checkin FOREIGN KEY (checkin_id) REFERENCES daily_checkins(id) ON DELETE CASCADE,
  CONSTRAINT fk_recommendation_prediction FOREIGN KEY (risk_prediction_id) REFERENCES risk_predictions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE workout_sessions
  ADD COLUMN recommendation_id BIGINT NULL,
  ADD CONSTRAINT uq_workout_recommendation UNIQUE (recommendation_id),
  ADD CONSTRAINT fk_workout_recommendation FOREIGN KEY (recommendation_id)
    REFERENCES recommendation_histories(id) ON DELETE SET NULL;

ALTER TABLE feedbacks
  MODIFY COLUMN session_id BIGINT NULL,
  MODIFY COLUMN completed BIT(1) NULL,
  ADD COLUMN recommendation_id BIGINT NULL,
  ADD COLUMN recommendation_status VARCHAR(20) NULL,
  ADD COLUMN suitability_rating INT NULL,
  ADD COLUMN recommendation_difficulty INT NULL,
  ADD COLUMN recommendation_reason VARCHAR(30) NULL,
  ADD COLUMN recommendation_comment VARCHAR(500) NULL,
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD INDEX idx_feedback_recommendation_status (recommendation_status),
  ADD INDEX idx_feedback_recommendation_reason (recommendation_reason),
  ADD CONSTRAINT uq_feedback_recommendation UNIQUE (recommendation_id),
  ADD CONSTRAINT fk_feedback_recommendation FOREIGN KEY (recommendation_id)
    REFERENCES recommendation_histories(id) ON DELETE CASCADE;
