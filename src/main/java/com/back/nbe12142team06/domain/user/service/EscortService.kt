package com.back.nbe12142team06.domain.user.service

import com.back.nbe12142team06.domain.user.entity.EscortGradeHistory
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.repository.EscortGradeHistoryRepository
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.global.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EscortService(
    private val escortProfileRepository: EscortProfileRepository,
    private val escortGradeHistoryRepository: EscortGradeHistoryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 현재 기준으로 등급을 다시 계산하고, 바뀌었으면 이력을 남긴다 (변경 여부 반환)
    @Transactional
    fun refreshGrade(escort: EscortProfile): Boolean {
        val previousGrade = escort.grade
        val newGrade = escort.calculateGrade()

        if (previousGrade == newGrade) return false

        escort.updateGrade(newGrade)
        escortGradeHistoryRepository.save(EscortGradeHistory(escort, previousGrade, newGrade))

        return true
    }

    // [관리자] 동행인 한 명의 등급을 수동으로 다시 계산한다.
    // 관리자만 실행 가능
    @Transactional
    fun escortGradeCal(adminId: Long, userId: Long): Boolean {
        val escort = escortProfileRepository.findByIdOrNull(userId)
            ?: throw NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다.")

        val changed = refreshGrade(escort)

        log.info("[등급 계산] adminId={}, userId={}, 변경여부={}", adminId, userId, changed)
        return changed
    }

    // 동행인 등급 재계산 (스케줄러에서 호출)
    // 동행 완료/노쇼 때 갱신이 누락됐거나 저장된 등급이 현재 기준과 다른 동행인을 맞춘다.
    @Transactional
    fun escortGradeCheck(): Int {
        // 모든 동행인을 회원 상태와 상관없이 한 명씩 갱신하고, 등급이 바뀐 경우만 센다
        val updated = escortProfileRepository.findAll().count { refreshGrade(it) }

        log.info("[동행인 등급 재계산] {}명", updated)
        return updated
    }
}