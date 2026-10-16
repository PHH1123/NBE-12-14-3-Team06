package com.back.nbe12142team06.domain.user.entity;

import com.back.nbe12142team06.domain.user.enums.EscortGrade;
import com.back.nbe12142team06.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "escort_grade_history")
public class EscortGradeHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 등급이 바뀐 동행 매니저
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escort_id", nullable = false)
    private EscortProfile escortProfile;

    // 변경 전 등급 (최초 등급 부여 시에는 null)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EscortGrade previousGrade;

    // 변경 후 등급
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscortGrade newGrade;

}
