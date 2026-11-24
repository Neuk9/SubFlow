-- 공유 구독 이메일 초대와 양방향 정산

ALTER TABLE subscription_shares ADD COLUMN member_email VARCHAR(255);
ALTER TABLE subscription_shares ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_subscription_shares_member_email ON subscription_shares (member_email, status);

ALTER TABLE settlement_records ADD COLUMN member_paid BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE settlement_records ADD COLUMN member_paid_at DATE;
