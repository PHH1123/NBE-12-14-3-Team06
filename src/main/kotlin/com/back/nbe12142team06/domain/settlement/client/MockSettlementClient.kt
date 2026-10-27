package com.back.nbe12142team06.domain.settlement.client

import com.back.nbe12142team06.global.exception.InternalServerErrorException
import org.springframework.stereotype.Component

@Component
class MockSettlementClient : SettlementClient {
    override fun settlementRequest(request: Any): SettlementClientResponse {
        // 정산 로직 수행 중
        if (request is SettlementClientRequest) {
            val req: SettlementClientRequest.Req = request.req_list.first()

            return SettlementClientResponse(req.account_num, req.account_holder_name, req.tran_amt)
        }

        throw InternalServerErrorException(73, "정산 외부 API 호출을 실패했습니다.")
    }
}
