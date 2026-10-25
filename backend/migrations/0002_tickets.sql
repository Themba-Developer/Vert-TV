CREATE TABLE checkout_tickets(ticket_hash TEXT PRIMARY KEY, order_id TEXT NOT NULL REFERENCES orders(id), expires_at INTEGER NOT NULL);
CREATE TABLE media_tickets(ticket_hash TEXT PRIMARY KEY, content_id TEXT NOT NULL REFERENCES content(id), expires_at INTEGER NOT NULL);
CREATE INDEX media_expiry ON media_tickets(expires_at);
CREATE INDEX checkout_expiry ON checkout_tickets(expires_at);
