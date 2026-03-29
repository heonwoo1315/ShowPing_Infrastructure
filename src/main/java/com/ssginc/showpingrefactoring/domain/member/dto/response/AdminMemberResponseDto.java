package com.ssginc.showpingrefactoring.domain.member.dto.response;

import com.ssginc.showpingrefactoring.domain.member.entity.Member;
import com.ssginc.showpingrefactoring.domain.member.entity.MemberRole;
import lombok.Getter;

@Getter
public class AdminMemberResponseDto {

    private final Long memberNo;
    private final String memberId;
    private final String memberName;
    private final String memberEmail;
    private final String memberPhone;
    private final String memberAddress;
    private final MemberRole memberRole;
    private final Long memberPoint;

    public AdminMemberResponseDto(Member member) {
        this.memberNo = member.getMemberNo();
        this.memberId = member.getMemberId();
        this.memberName = member.getMemberName();
        this.memberEmail = member.getMemberEmail();
        this.memberPhone = member.getMemberPhone();
        this.memberAddress = member.getMemberAddress();
        this.memberRole = member.getMemberRole();
        this.memberPoint = member.getMemberPoint();
    }
}
