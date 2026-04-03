package com.ssginc.showpingrefactoring.domain.member.service;

import com.ssginc.showpingrefactoring.domain.member.dto.request.AdminLoginRequestDto;
import com.ssginc.showpingrefactoring.domain.member.dto.response.AdminMemberResponseDto;
import com.ssginc.showpingrefactoring.domain.member.dto.response.LoginResponseDto;
import com.ssginc.showpingrefactoring.domain.member.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminService {
    LoginResponseDto login(AdminLoginRequestDto request);

    Page<AdminMemberResponseDto> getMembers(Pageable pageable);
    AdminMemberResponseDto getMember(Long memberNo);
    PageResponse<AdminMemberResponseDto> searchMembers(String keyword, Pageable pageable);
}
