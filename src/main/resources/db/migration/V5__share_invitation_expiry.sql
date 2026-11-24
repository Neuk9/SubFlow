-- 공유 구독 초대 만료·재초대

ALTER TABLE subscription_shares ADD COLUMN invited_at TIMESTAMP;
ALTER TABLE subscription_shares ADD COLUMN expires_at TIMESTAMP;
ALTER TABLE subscription_shares ADD COLUMN responded_at TIMESTAMP;

CREATE INDEX idx_subscription_shares_pending
    ON subscription_shares (member_email, status, expires_at);
