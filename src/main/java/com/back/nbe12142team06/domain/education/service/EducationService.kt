package com.back.nbe12142team06.domain.education.service

import com.back.nbe12142team06.domain.education.entity.EducationProgress
import com.back.nbe12142team06.domain.education.entity.WatchProgressLog
import com.back.nbe12142team06.domain.education.repository.EducationProgressRepository
import com.back.nbe12142team06.domain.education.repository.EducationVideoRepository
import com.back.nbe12142team06.domain.education.repository.WatchProgressLogRepository
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.global.exception.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class EducationService(
    private val escortProfileRepository: EscortProfileRepository,
    private val educationVideoRepository: EducationVideoRepository,
    private val educationProgressRepository: EducationProgressRepository,
    private val watchProgressLogRepository: WatchProgressLogRepository,
    private val clock: Clock,
) {

    // 시청 기록
    @Transactional
    fun recordProgress(userId: Long, videoId: Long, positionSec: Double): EducationProgress {
        val escortProfile = escortProfileRepository.findByIdOrNull(userId)
            ?: throw NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다.")

        val educationVideo = educationVideoRepository.findByIdOrNull(videoId)
            ?: throw NotFoundException(101, "존재하지않는 교육 영상입니다.")

        val progress = educationProgressRepository.findByEscortProfileAndEducationVideo(escortProfile, educationVideo)
            ?: throw NotFoundException(102, "교육 진행 정보가 존재하지 않습니다.")

        // 이미 이수한 경우 더 기록하지 않음
        if (escortProfile.verified) return progress

        val now = LocalDateTime.now(clock)
        val accepted = progress.record(positionSec, now)
        watchProgressLogRepository.save(WatchProgressLog(progress, positionSec, now, accepted))

        // 필수 영상 전부 시청 시 동행인 프로필 신원인증 처리
        if (progress.completed && isAllRequiredCompleted(escortProfile)) {
            escortProfileRepository.verify(userId, now)

            // 벌크 UPDATE는 메모리 객체를 갱신하지 않으므로, 응답용으로 최신 상태를 다시 조회
            val refreshed = escortProfileRepository.findById(userId).orElseThrow()
            return educationProgressRepository.findByEscortProfileAndEducationVideo(refreshed, educationVideo)!!
        }

        return progress
    }

    // 교육 영상 목록 + 진행 상황
    @Transactional(readOnly = true)
    fun getProgresses(userId: Long): List<EducationProgress> {
        checkEscortProfileExists(userId)
        return educationProgressRepository.findAllByEscortProfileUserIdOrderByEducationVideoIdAsc(userId)
    }

    // 교육 영상 단건 + 진행 상황
    @Transactional(readOnly = true)
    fun getProgress(userId: Long, videoId: Long): EducationProgress {
        checkEscortProfileExists(userId)
        return educationProgressRepository.findByEscortProfileUserIdAndEducationVideoId(userId, videoId)
            ?: throw NotFoundException(103, "존재하지않는 교육 영상입니다.")
    }

    // 동행 매니저 프로필 생성 시 모든 영상에 대한 진행 상황 생성
    @Transactional
    fun createProgresses(escortProfile: EscortProfile) {
        val progresses = educationVideoRepository.findAll().map { EducationProgress(escortProfile, it) }
        educationProgressRepository.saveAll(progresses)
    }

    // 동행 매니저 탈퇴 시 교육 데이터 삭제 (로그 → 진행 상황 순서)
    @Transactional
    fun deleteProgresses(userId: Long) {
        watchProgressLogRepository.deleteAllByEscortUserId(userId)
        educationProgressRepository.deleteAllByEscortUserId(userId)
    }

    private fun checkEscortProfileExists(userId: Long) {
        if (!escortProfileRepository.existsById(userId)) {
            throw NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다.")
        }
    }

    // 필수 영상을 모두 완료했는지
    private fun isAllRequiredCompleted(escortProfile: EscortProfile): Boolean {
        // 필수 영상 중 시청 완료한 영상 수
        val completedCount =
            educationProgressRepository.countByEscortProfileAndCompletedTrueAndEducationVideoRequiredTrue(escortProfile)
        // 필수 영상 수
        val requiredCount = educationVideoRepository.countByRequiredTrue()

        return completedCount == requiredCount
    }
}