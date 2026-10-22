package com.back.nbe12142team06.domain.settlement.service;

import com.back.nbe12142team06.domain.settlement.dto.AccountDto;
import com.back.nbe12142team06.domain.settlement.entity.Settlement;
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus;
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository;
import com.back.nbe12142team06.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementPersistenceService {

    private final SettlementRepository settlementRepository;

    public List<AccountDto> findAllByStatusAndDate() {
        return settlementRepository.findAllByStatusAndDate();
    }

    @Transactional
    public Settlement updateSettlement(Long settlementId, SettlementStatus status) {
        Settlement settlement = settlementRepository.fetchJoinById(settlementId)
                .orElseThrow(() -> new NotFoundException(71, "정산 데이터를 찾을 수 없습니다."));

        if (status == SettlementStatus.COMPLETED) {
            settlement.settlementCompleted();
        } else if (status == SettlementStatus.FAILED) {
            settlement.settlementFailed();
        }

        return settlement;
    }

    public AccountDto findAccountDto(Long userId, Long settlementId) {
        // 정산 데이터 조회
        return settlementRepository.findByIdAndState(userId, settlementId)
                .orElseThrow(() -> new NotFoundException(71, "정산 데이터를 찾을 수 없습니다."));
    }

    @Transactional
    public int processingSettlement(Long settlementId) {
        return settlementRepository.updateProcessing(settlementId);
    }

    public Settlement findById(Long settlementId) {
        return settlementRepository.findJoinFetchById(settlementId)
                .orElseThrow(() -> new NotFoundException(71, "정산 데이터를 찾을 수 없습니다."));
    }

    public List<Settlement> getAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        return settlementRepository.findAnalytics(startDate, endDate);
    }

    public Page<Settlement> getSettlementForAdmin(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return settlementRepository.findByPeriod(startDate, endDate, pageable);
    }

    public AccountDto findAccountDto(Long settlementId) {
        // 정산 데이터 조회
        return settlementRepository.findByIdAndState(settlementId)
                .orElseThrow(() -> new NotFoundException(71, "정산 데이터를 찾을 수 없습니다."));
    }

    @Transactional
    public int processingSettlementForAdmin(Long settlementId) {
        return settlementRepository.updateProcessingForAdmin(settlementId);
    }
}
