
package com.back.nbe12142team06.domain.application.service;

import com.back.nbe12142team06.domain.application.enums.ApplicationStatus;
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository;
import com.back.nbe12142team06.domain.post.entity.PostStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationInProgressServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private ApplicationService applicationService;

    @Test
    @DisplayName("진행 중인 동행이 있으면 true 반환")
    void hasInProgressEscort_true() {

        Long escortId = 1L;

        when(applicationRepository.existsInProgressByEscortId(
                escortId,
                ApplicationStatus.ACCEPTED,
                PostStatus.IN_PROGRESS
        )).thenReturn(true);

        boolean result = applicationService.hasInProgressEscort(escortId);

        assertTrue(result);
    }

    @Test
    @DisplayName("진행 중인 동행이 없으면 false 반환")
    void hasInProgressEscort_false() {

        Long escortId = 1L;

        when(applicationRepository.existsInProgressByEscortId(
                escortId,
                ApplicationStatus.ACCEPTED,
                PostStatus.IN_PROGRESS
        )).thenReturn(false);

        boolean result = applicationService.hasInProgressEscort(escortId);

        assertFalse(result);
    }
}
