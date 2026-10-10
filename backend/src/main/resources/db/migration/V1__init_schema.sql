-- AdaptiveFit AI: schema v1. Dung INT/DOUBLE/BIT(1) de Hibernate validate khop kieu.

CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL,
  created_at    DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_profiles (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id           BIGINT NOT NULL UNIQUE,
  age               INT NOT NULL,
  height_cm         DECIMAL(5,1) NOT NULL,
  weight_kg         DECIMAL(5,1) NOT NULL,
  fitness_level     VARCHAR(20) NOT NULL,
  goal              VARCHAR(30) NOT NULL,
  available_minutes INT NOT NULL,
  preferred_workout VARCHAR(50),
  location          VARCHAR(20) NOT NULL,
  equipment         VARCHAR(255),
  CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE daily_checkins (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  checkin_date    DATE NOT NULL,
  sleep_hours     DOUBLE NOT NULL,
  stress          INT NOT NULL,
  energy          INT NOT NULL,
  mood            INT NOT NULL,
  free_time_min   INT NOT NULL,
  motivation      INT NOT NULL,
  exercised_today BIT(1) NOT NULL,
  duration_min    INT NULL,
  difficulty      INT NULL,
  missed_reason   VARCHAR(30) NULL,
  created_at      DATETIME(6) NOT NULL,
  UNIQUE KEY uq_checkin_user_date (user_id, checkin_date),
  CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dataset_versions (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(100) NOT NULL,
  source     VARCHAR(255),
  row_count  INT NOT NULL,
  file_hash  VARCHAR(64),
  is_demo    BIT(1) NOT NULL,
  created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE model_versions (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  version            VARCHAR(30) NOT NULL UNIQUE,
  algorithm          VARCHAR(50) NOT NULL,
  dataset_version_id BIGINT NULL,
  file_path          VARCHAR(255),
  status             VARCHAR(20) NOT NULL,
  is_active          BIT(1) NOT NULL,
  created_at         DATETIME(6) NOT NULL,
  CONSTRAINT fk_model_dataset FOREIGN KEY (dataset_version_id) REFERENCES dataset_versions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE model_evaluations (
  id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
  model_version_id       BIGINT NOT NULL,
  accuracy               DOUBLE,
  `precision`            DOUBLE,
  recall                 DOUBLE,
  f1                     DOUBLE,
  roc_auc                DOUBLE,
  confusion_matrix_json  JSON,
  feature_importance_json JSON,
  created_at             DATETIME(6) NOT NULL,
  CONSTRAINT fk_eval_model FOREIGN KEY (model_version_id) REFERENCES model_versions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE interventions (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  code           VARCHAR(20) NOT NULL UNIQUE,
  priority       INT NOT NULL,
  condition_json JSON,
  description    VARCHAR(500),
  active         BIT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE risk_predictions (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  checkin_id       BIGINT NOT NULL UNIQUE,
  model_version_id BIGINT NULL,
  risk_score       DOUBLE NOT NULL,
  risk_level       VARCHAR(10) NOT NULL,
  shap_json        JSON,
  intervention_id  BIGINT NULL,
  created_at       DATETIME(6) NOT NULL,
  CONSTRAINT fk_pred_checkin FOREIGN KEY (checkin_id) REFERENCES daily_checkins(id) ON DELETE CASCADE,
  CONSTRAINT fk_pred_model FOREIGN KEY (model_version_id) REFERENCES model_versions(id),
  CONSTRAINT fk_pred_intervention FOREIGN KEY (intervention_id) REFERENCES interventions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE exercises (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  name               VARCHAR(120) NOT NULL,
  description        VARCHAR(500),
  duration_min       INT NOT NULL,
  duration_max       INT NOT NULL,
  difficulty         INT NOT NULL,
  intensity          VARCHAR(10) NOT NULL,
  equipment          VARCHAR(255),
  location           VARCHAR(20) NOT NULL,
  muscle_group       VARCHAR(50),
  goal               VARCHAR(30),
  energy_requirement INT NOT NULL,
  recovery_suitable  BIT(1) NOT NULL,
  instructions       TEXT,
  media_url          VARCHAR(255),
  active             BIT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE exercise_tags (
  exercise_id BIGINT NOT NULL,
  tag         VARCHAR(40) NOT NULL,
  PRIMARY KEY (exercise_id, tag),
  CONSTRAINT fk_tag_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intervention_exercises (
  intervention_id BIGINT NOT NULL,
  exercise_id     BIGINT NOT NULL,
  PRIMARY KEY (intervention_id, exercise_id),
  CONSTRAINT fk_ie_intervention FOREIGN KEY (intervention_id) REFERENCES interventions(id) ON DELETE CASCADE,
  CONSTRAINT fk_ie_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE workout_sessions (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id           BIGINT NOT NULL,
  prediction_id     BIGINT NULL,
  intervention_code VARCHAR(20),
  status            VARCHAR(20) NOT NULL,
  started_at        DATETIME(6) NOT NULL,
  finished_at       DATETIME(6) NULL,
  CONSTRAINT fk_ws_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_ws_pred FOREIGN KEY (prediction_id) REFERENCES risk_predictions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE workout_session_exercises (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id  BIGINT NOT NULL,
  exercise_id BIGINT NOT NULL,
  order_no    INT NOT NULL,
  planned_min INT NOT NULL,
  CONSTRAINT fk_wse_session FOREIGN KEY (session_id) REFERENCES workout_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_wse_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE feedbacks (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id          BIGINT NOT NULL UNIQUE,
  completed           BIT(1) NOT NULL,
  difficulty          INT NULL,
  fatigue             INT NULL,
  satisfaction        INT NULL,
  liked               BIT(1) NULL,
  not_completed_reason VARCHAR(30) NULL,
  created_at          DATETIME(6) NOT NULL,
  CONSTRAINT fk_fb_session FOREIGN KEY (session_id) REFERENCES workout_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE experiments (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(120) NOT NULL,
  start_date DATE,
  end_date   DATE,
  status     VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE experiment_assignments (
  experiment_id BIGINT NOT NULL,
  user_id       BIGINT NOT NULL,
  group_name    VARCHAR(20) NOT NULL,
  PRIMARY KEY (experiment_id, user_id),
  CONSTRAINT fk_ea_exp FOREIGN KEY (experiment_id) REFERENCES experiments(id) ON DELETE CASCADE,
  CONSTRAINT fk_ea_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outcome_labels (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  checkin_id BIGINT NOT NULL UNIQUE,
  dropout_7d BIT(1) NOT NULL,
  is_demo    BIT(1) NOT NULL,
  labeled_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_label_checkin FOREIGN KEY (checkin_id) REFERENCES daily_checkins(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
