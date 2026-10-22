package com.back.nbe12142team06.domain.user.entity;

import com.back.nbe12142team06.global.crypto.PiiConverter;
import com.back.nbe12142team06.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "client_profiles")
public class ClientProfile extends BaseTimeEntity {

    // 회원 개인 키 ID
    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 보호자 실명
    @Convert(converter = PiiConverter.class)
    @Column(nullable = false, length = 255)
    private String emergencyContactName;

    // 보호자 번호
    @Convert(converter = PiiConverter.class)
    @Column(nullable = false, length = 255)
    private String emergencyContactPhone;

    // 의뢰인 특이사항
    @Convert(converter = PiiConverter.class)
    @Column(columnDefinition = "TEXT")
    private String careNote;


    public ClientProfile(User user, String emergencyContactName,  String emergencyContactPhone, String careNote) {
        this.user = user;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
        this.careNote = careNote;
    }

    public void updateProfile(String emergencyContactName,  String emergencyContactPhone, String careNote){
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
        this.careNote = careNote;
    }
}
