package com.ssginc.showpingrefactoring.domain.member.repository;

import com.ssginc.showpingrefactoring.domain.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface MemberRepository extends JpaRepository<Member, Long> {

    // 로그인용
    Optional<Member> findByMemberId(String memberId);
    Optional<Member> findByMemberEmail(String email);
    boolean existsByMemberId(String memberId);
    boolean existsByMemberEmail(String email);
    boolean existsByMemberPhone(String phone);

    // 관리자 유저 검색: Full-Text Index (ngram 파서) — MATCH AGAINST IN BOOLEAN MODE
    @Query(value = "SELECT * FROM member WHERE MATCH(member_id, member_name, member_email) AGAINST(:keyword IN BOOLEAN MODE) ORDER BY member_no DESC",
           countQuery = "SELECT COUNT(*) FROM member WHERE MATCH(member_id, member_name, member_email) AGAINST(:keyword IN BOOLEAN MODE)",
           nativeQuery = true)
    Page<Member> findByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
