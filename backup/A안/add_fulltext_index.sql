-- 기존 인덱스가 있을 경우 제거 후 재생성
ALTER TABLE member DROP INDEX IF EXISTS ft_member_search;

ALTER TABLE member
    ADD FULLTEXT INDEX ft_member_search (member_id, member_name, member_email)
    WITH PARSER ngram;

-- 인덱스 생성 확인
SHOW INDEX FROM member WHERE Key_name = 'ft_member_search';