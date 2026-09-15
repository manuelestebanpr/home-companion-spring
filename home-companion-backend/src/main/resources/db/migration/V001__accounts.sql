CREATE TABLE users (
 id uuid PRIMARY KEY, username varchar(60) UNIQUE NOT NULL, password_hash varchar(100) NOT NULL,
 state varchar(16) NOT NULL CHECK (state IN ('PENDING','APPROVED','REJECTED','DISABLED')),
 role varchar(10) NOT NULL CHECK (role IN ('MEMBER','ADMIN')), must_change_password boolean NOT NULL DEFAULT false
);
CREATE TABLE modules (code varchar(40) PRIMARY KEY, title varchar(80) NOT NULL, enabled boolean NOT NULL DEFAULT true);
INSERT INTO modules VALUES ('groceries','Despensa',true);
CREATE TABLE user_module_grants (
 user_id uuid REFERENCES users NOT NULL, module_code varchar(40) REFERENCES modules NOT NULL,
 permission varchar(4) NOT NULL CHECK(permission IN ('VIEW','EDIT')), PRIMARY KEY(user_id,module_code)
);
CREATE TABLE credentials (
 id uuid PRIMARY KEY, user_id uuid REFERENCES users NOT NULL, token_hash char(64) UNIQUE NOT NULL,
 prefix varchar(20) NOT NULL, kind varchar(8) NOT NULL CHECK(kind IN ('SESSION','KEY')),
 label varchar(80) NOT NULL, cap varchar(10) NOT NULL CHECK(cap IN ('READ','READ_WRITE')),
 module_code varchar(40) REFERENCES modules, expires_at timestamptz NOT NULL,
 revoked boolean NOT NULL DEFAULT false, last_used_at timestamptz
);
CREATE INDEX credentials_owner ON credentials(user_id);
