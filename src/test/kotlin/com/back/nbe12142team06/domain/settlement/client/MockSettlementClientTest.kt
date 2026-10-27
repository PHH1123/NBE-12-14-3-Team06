package com.back.nbe12142team06.domain.settlement.client

import com.back.nbe12142team06.global.exception.InternalServerErrorException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 금융결제원 API 를 대신하는 목 클라이언트 테스트.
 */
class MockSettlementClientTest {

    private val settlementClient = MockSettlementClient()

    @Test
    @DisplayName("[MockSettlementClient] 요청한 계좌·예금주·금액을 그대로 응답에 담는다")
    fun settlementRequest() {
        val request = SettlementClientRequest("123-000000-123", "동행매니저", 135_000)

        val response = settlementClient.settlementRequest(request)

        assertThat(response.res_cnt).isEqualTo(1)
        assertThat(response.res_list).hasSize(1)
        assertThat(response.res_list.first().account_num).isEqualTo("123-000000-123")
        assertThat(response.res_list.first().account_holder_name).isEqualTo("동행매니저")
        assertThat(response.res_list.first().tran_amt).isEqualTo(135_000)
    }

    @Test
    @DisplayName("[MockSettlementClient] 정산 요청 형식이 아니면 외부 API 호출 실패로 처리한다")
    fun settlementRequestWithInvalidRequest() {
        assertThatThrownBy { settlementClient.settlementRequest("잘못된 요청") }
            .isInstanceOf(InternalServerErrorException::class.java)
            .hasMessage("정산 외부 API 호출을 실패했습니다.")
    }
}
