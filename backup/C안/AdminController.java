package com.ssginc.showpingrefactoring.domain.member.controller;

import com.ssginc.showpingrefactoring.domain.member.dto.request.AdminLoginRequestDto;
import com.ssginc.showpingrefactoring.domain.member.dto.response.AdminMemberResponseDto;
import com.ssginc.showpingrefactoring.domain.member.dto.response.LoginResponseDto;
import com.ssginc.showpingrefactoring.domain.member.dto.response.PageResponse;
import com.ssginc.showpingrefactoring.domain.member.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // -------------------------------------------------------
    // 인증
    // -------------------------------------------------------
    @Operation(summary = "관리자 로그인", description = "관리자 ID와 비밀번호로 로그인하여 토큰을 발급받습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "로그인 실패")
    })
    @PostMapping("/api/admin/auth/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody AdminLoginRequestDto request) {
        LoginResponseDto response = adminService.login(request);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------
    // 유저 조회
    // -------------------------------------------------------
    @Operation(summary = "전체 유저 목록 조회", description = "가입된 모든 유저를 페이지 단위로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/api/admin/members")
    public ResponseEntity<Page<AdminMemberResponseDto>> getMembers(
            @PageableDefault(size = 20, sort = "memberNo", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminService.getMembers(pageable));
    }

    @Operation(summary = "단건 유저 조회", description = "memberNo로 특정 유저를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "유저 없음"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/api/admin/members/{memberNo}")
    public ResponseEntity<AdminMemberResponseDto> getMember(@PathVariable Long memberNo) {
        return ResponseEntity.ok(adminService.getMember(memberNo));
    }

    @Operation(summary = "유저 키워드 검색", description = "memberId, memberName, memberEmail 중 하나라도 포함되면 반환합니다. keyword 미입력 시 전체 조회.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/api/admin/members/search")
    public ResponseEntity<PageResponse<AdminMemberResponseDto>> searchMembers(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "memberNo", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminService.searchMembers(keyword, pageable));
    }
}
