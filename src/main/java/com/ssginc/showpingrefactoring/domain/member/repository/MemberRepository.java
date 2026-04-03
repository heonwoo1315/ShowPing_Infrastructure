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

    // 관리자 유저 검색: memberId, memberName, memberEmail 중 하나라도 포함되면 반환
    @Query(value = "SELECT m FROM Member m WHERE " +
            "m.memberId LIKE %:keyword% OR " +
            "m.memberName LIKE %:keyword% OR " +
            "m.memberEmail LIKE %:keyword%",
            countQuery = "SELECT COUNT(m.memberNo) FROM Member m WHERE " +
            "m.memberId LIKE %:keyword% OR " +
            "m.memberName LIKE %:keyword% OR " +
            "m.memberEmail LIKE %:keyword%")
    Page<Member> findByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
