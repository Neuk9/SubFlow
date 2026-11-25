-- Google 프로필 사진 URL이 500자를 넘는 계정이 있어 최초 로그인 INSERT가 실패한다.
-- URL을 넉넉히 저장할 수 있도록 컬럼 길이를 늘린다.
ALTER TABLE users ALTER COLUMN picture_url TYPE VARCHAR(2048);
