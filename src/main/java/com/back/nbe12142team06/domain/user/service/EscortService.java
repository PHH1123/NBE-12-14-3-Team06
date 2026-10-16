package com.back.nbe12142team06.domain.user.service;

import com.back.nbe12142team06.domain.user.entity.EscortGradeHistory;
import com.back.nbe12142team06.domain.user.entity.EscortProfile;
import com.back.nbe12142team06.domain.user.enums.EscortGrade;
import com.back.nbe12142team06.domain.user.repository.EscortGradeHistoryRepository;
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository;
import com.back.nbe12142team06.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscortService {

    private final EscortProfileRepository escortProfileRepository;
    private final EscortGradeHistoryRepository escortGradeHistoryRepository;

    @Transactional
    public boolean refreshGrade(EscortProfile escort) {

        EscortGrade previousGrade = escort.getGrade();
        EscortGrade newGrade = escort.calculateGrade();

        if (previousGrade == newGrade) {
            return false;
        }
        escort.updateGrade(newGrade);

        escortGradeHistoryRepository.save(
                EscortGradeHistory.builder()
                        .escortProfile(escort)
                        .previousGrade(previousGrade)
                        .newGrade(newGrade)
                        .build()
        );

        return true;
    }

    // [관리자] 동행인 한 명의 등급을 수동으로 다시 계산한다.
    // 관리자만 실행 가능 // TODO : CONFIG에 추가 관리자 경로를 막는 방식
    @Transactional
    public boolean escortGradeCal(Long adminId, Long userId) {

        EscortProfile escort = escortProfileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다."));

        boolean changed = refreshGrade(escort);

        log.info("[등급 계산] adminId={}, userId={}, 변경여부={}", adminId, userId, changed);
        return changed;
    }

    // 동행인 등급 재계산 (스케줄러에서 호출)
    // 동행 완료/노쇼 때 갱신이 누락됐거나 저장된 등급이 현재 기준과 다른 동행인을 맞춘다.
    @Transactional
    public int escortGradeCheck() {
        // 모든 동행인 조회 (회원 상태와 상관없이 계산)
        List<EscortProfile> escorts = escortProfileRepository.findAll();

        // 실제로 등급이 바뀐 동행인 수
        int updated = 0;

        for (EscortProfile escort : escorts) {
            // 한 명씩 갱신하고, 등급이 바뀐 경우만 센다
            if (refreshGrade(escort)) {
                updated++;
            }
        }
        log.info("[동행인 등급 재계산] {}명", updated);

        return updated;
    }

}
