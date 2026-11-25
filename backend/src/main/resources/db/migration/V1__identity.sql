CREATE TABLE write_guard (id INT PRIMARY KEY, initialized BOOLEAN NOT NULL);
INSERT INTO write_guard VALUES (1, FALSE);
CREATE TABLE account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  login VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL UNIQUE,
  display_name VARCHAR(50) NOT NULL,
  secret_hash VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  temporary BOOLEAN NOT NULL DEFAULT TRUE,
  revision BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX account_order (created_at, id)
);
CREATE TABLE access_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL UNIQUE,
  name VARCHAR(50) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  revision BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX role_order (created_at, id)
);
CREATE TABLE account_role (
  account_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY(account_id, role_id),
  FOREIGN KEY (account_id) REFERENCES account(id),
  FOREIGN KEY (role_id) REFERENCES access_role(id)
);
CREATE TABLE role_permission (
  role_id BIGINT NOT NULL,
  permission VARCHAR(32) NOT NULL,
  PRIMARY KEY(role_id, permission),
  FOREIGN KEY (role_id) REFERENCES access_role(id)
);
CREATE TABLE login_session (
  token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  account_id BIGINT NOT NULL,
  started_at BIGINT NOT NULL,
  active_at BIGINT NOT NULL,
  FOREIGN KEY (account_id) REFERENCES account(id),
  INDEX session_expiry (active_at),
  INDEX session_absolute_expiry (started_at)
);
CREATE TABLE login_throttle (
  bucket CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  failures VARCHAR(100) NOT NULL DEFAULT '',
  blocked_until BIGINT NOT NULL DEFAULT 0,
  touched_at BIGINT NOT NULL,
  INDEX throttle_expiry (touched_at)
);
CREATE TABLE audit_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id BIGINT NULL,
  operation VARCHAR(40) NOT NULL,
  target_id BIGINT NULL,
  occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
