package com.ssginc.showpingrefactoring.domain.member.dto.response;

import com.ssginc.showpingrefactoring.domain.member.entity.Member;
import com.ssginc.showpingrefactoring.domain.member.entity.MemberRole;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminMemberResponseDto {

    private Long memberNo;
    private String memberId;
    private String memberName;
    private String memberEmail;
    private String memberPhone;
    private String memberAddress;
    private MemberRole memberRole;
    private Long memberPoint;

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
