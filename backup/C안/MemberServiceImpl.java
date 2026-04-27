package com.ssginc.showpingrefactoring.domain.member.service.implement;


import com.ssginc.showpingrefactoring.domain.member.entity.Member;
import com.ssginc.showpingrefactoring.domain.member.entity.MemberRole;
import com.ssginc.showpingrefactoring.domain.member.dto.request.SignupRequestDto;
import com.ssginc.showpingrefactoring.domain.member.dto.request.UpdateMemberRequestDto;
import com.ssginc.showpingrefactoring.domain.member.dto.object.MemberDto;
import com.ssginc.showpingrefactoring.common.exception.CustomException;
import com.ssginc.showpingrefactoring.common.exception.ErrorCode;
import com.ssginc.showpingrefactoring.domain.member.repository.MemberRepository;
import com.ssginc.showpingrefactoring.domain.member.service.MemberService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.beans.Transient;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Map<String, Boolean> verifiedEmailStorage = new HashMap<>();

    @Override
    public Member findMemberById(String memberId) {
        return memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + memberId));
    }

    @Override
    public Member findMember(String memberId, String password) {

        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (member != null) {
            if (!passwordEncoder.matches(password, member.getMemberPassword())) {
                throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
            }
            else {
                return member;
            }
        } else {
            return null;
        }
    }

    @CacheEvict(value = "memberSearch", allEntries = true)
    @Transactional
    @Override
    public Member registerMember(MemberDto dto) {
        try {
            return memberRepository.save(Member.builder()
                    .memberId(dto.getMemberId())
                    .memberEmail(dto.getMemberEmail())
                    .memberPhone(dto.getMemberPhone())
                    .memberName(dto.getMemberName())
                    .memberPassword(passwordEncoder.encode(dto.getMemberPassword()))
                    .memberAddress(dto.getMemberAddress())
                    .memberRole(MemberRole.ROLE_USER)
                    .streamKey(UUID.randomUUID().toString())
                    .memberPoint(0L)
                    .build());
        } catch (DataIntegrityViolationException e) {
            String causeMessage = e.getMostSpecificCause().getMessage();

            if (causeMessage.contains("member_id")) {
                throw new CustomException(ErrorCode.DUPLICATED_MEMBER_ID);
            } else if (causeMessage.contains("member_email")) {
                throw new CustomException(ErrorCode.DUPLICATED_EMAIL);
            } else if (causeMessage.contains("member_phone")) {
                throw new CustomException(ErrorCode.DUPLICATED_PHONE);
            }

            throw new CustomException(ErrorCode.DATABASE_ERROR); // 필요 시 추가 정의
        }
    }

    @Override
    public boolean isDuplicateId(String memberId) {
        return memberRepository.existsByMemberId(memberId);
    }

    @Override
    public boolean isDuplicateEmail(String memberEmail) {
        return memberRepository.existsByMemberEmail(memberEmail);
    }

    @Override
    public boolean isDuplicatePhone(String memberPhone) {
        return memberRepository.existsByMemberPhone(memberPhone);
    }

    @Override
    public MemberDto getMemberInfo(String memberId) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return new MemberDto(
                member.getMemberNo(),
                member.getMemberPassword(),
                member.getMemberId(),
                member.getMemberName(),
                member.getMemberEmail(),
                member.getMemberPhone(),
                member.getMemberAddress()
        );
    }

    @CacheEvict(value = "memberSearch", allEntries = true)
    @Override
    public void updateMember(String memberId, UpdateMemberRequestDto request) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (request.getMemberName() != null) {
            member.setMemberName(request.getMemberName());
        }
        if (request.getPassword() != null) {
            member.setMemberPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getAddress() != null) {
            member.setMemberAddress(request.getAddress());
        }

        memberRepository.save(member);
    }

    @CacheEvict(value = "memberSearch", allEntries = true)
    @Override
    public void deleteMember(String memberId) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        memberRepository.delete(member);
    }
}
