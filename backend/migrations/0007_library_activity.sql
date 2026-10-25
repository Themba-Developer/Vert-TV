CREATE TABLE library_activity (
 user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 content_id TEXT NOT NULL REFERENCES content(id) ON DELETE CASCADE,
 loved INTEGER NOT NULL DEFAULT 0 CHECK(loved IN (0,1)),
 last_opened INTEGER,
 PRIMARY KEY(user_id,content_id)
);
CREATE INDEX library_activity_recent ON library_activity(user_id,last_opened DESC);
