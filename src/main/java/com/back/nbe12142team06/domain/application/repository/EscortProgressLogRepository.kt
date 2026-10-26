package com.back.nbe12142team06.domain.application.repository

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface EscortProgressLogRepository : JpaRepository<EscortProgressLog, Long> {
    fun findByApplicationAndProgress(
        application: Application,
        progress: EscortProgress,
    ): EscortProgressLog?

    fun existsByApplicationAndProgress(
        application: Application,
        progress: EscortProgress,
    ): Boolean

    fun findTopByApplicationOrderByOccurredAtDescIdDesc(
        application: Application,
    ): EscortProgressLog?
}
