CREATE TABLE account_recovery (
 user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 answers_hash TEXT NOT NULL,
 updated_at INTEGER NOT NULL
);
