package com.ssginc.showpingrefactoring.domain.member.service.implement;


import com.ssginc.showpingrefactoring.common.exception.CustomException;
import com.ssginc.showpingrefactoring.common.exception.ErrorCode;
import com.ssginc.showpingrefactoring.common.jwt.JwtTokenProvider;
import com.ssginc.showpingrefactoring.domain.member.dto.request.AdminLoginRequestDto;
import com.ssginc.showpingrefactoring.domain.member.dto.response.AdminMemberResponseDto;
import com.ssginc.showpingrefactoring.domain.member.dto.response.LoginResponseDto;
import com.ssginc.showpingrefactoring.domain.member.dto.response.PageResponse;
import com.ssginc.showpingrefactoring.domain.member.entity.Member;
import com.ssginc.showpingrefactoring.domain.member.entity.MemberRole;
import com.ssginc.showpingrefactoring.domain.member.repository.MemberRepository;
import com.ssginc.showpingrefactoring.domain.member.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenServiceImpl redisTokenService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponseDto login(AdminLoginRequestDto request) {
        Member admin = memberRepository.findByMemberId(request.getMemberId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), admin.getMemberPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        if (!admin.getMemberRole().equals(MemberRole.ROLE_ADMIN)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        String refreshToken = jwtTokenProvider.generateRefreshToken(admin.getMemberId());

        redisTokenService.saveRefreshToken(admin.getMemberId(), refreshToken);

        log.info("관리자 로그인 성공: {}", admin.getMemberId());

        return new LoginResponseDto("LOGIN_SUCCESS");
    }

    @Override
    public Page<AdminMemberResponseDto> getMembers(Pageable pageable) {
        return memberRepository.findAll(pageable)
                .map(AdminMemberResponseDto::new);
    }

    @Override
    public AdminMemberResponseDto getMember(Long memberNo) {
        Member member = memberRepository.findById(memberNo)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return new AdminMemberResponseDto(member);
    }

    @Override
    @Cacheable(value = "memberSearch", key = "#keyword + '_' + #pageable.pageNumber")
    public PageResponse<AdminMemberResponseDto> searchMembers(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return PageResponse.from(memberRepository.findAll(pageable).map(AdminMemberResponseDto::new));
        }
        String ftKeyword = keyword.trim() + "*";
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(memberRepository.findByKeyword(ftKeyword, unsorted)
                .map(AdminMemberResponseDto::new));
    }
}
