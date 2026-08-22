CREATE TABLE IF NOT EXISTS conversation_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    tokens INT
);

CREATE INDEX IF NOT EXISTS idx_conversation_session_timestamp ON conversation_messages(session_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_conversation_timestamp ON conversation_messages(timestamp);