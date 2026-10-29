package com.back.nbe12142team06.domain.settlement.client

data class SettlementClientRequest( // 금융결제원 AccessToken
    val Authorization: String,  // 약정 계좌/계정구분
    val cntr_account_type: String,  // 약정 계좌/계정 번호
    val cntr_account_num: String,  // 입금이체용 암호문구
    val wd_pass_phrase: String,  // 출금계좌인자내역
    val wd_print_content: String,  // 수취인성명검증 여부(on:검증함) (미지정 시 기본값: "on")
    val name_check_option: String,  // 요청일시
    val tran_dtime: String,  // 입금요청건수
    val req_cnt: Int,  // 입금요청목록
    val req_list: List<Req>
) {
    constructor(account: String, name: String, amount: Int) : this(
        Authorization = "Bearer <access_token>",
        cntr_account_type = "N",
        cntr_account_num = "1101230000678",
        wd_pass_phrase = "790d56ed........821a69",
        wd_print_content = "출금계좌인자내역",
        name_check_option = "off",
        tran_dtime = "20190910101921",
        req_cnt = 1,
        req_list = listOf(
            Req(
                tran_no = 1,
                bank_tran_id = "F123456789U4BC34239Z",
                bank_code_std = "097",
                account_num = account,
                account_holder_name = name,
                print_content = "동행 매니저 급여",
                tran_amt = amount,
                req_client_name = "병원 동행 서비스 가지",
                req_client_num = "HONGGILDONG1234",
                transfer_purpose = "TR"
            )
        )
    )

    data class Req( // 거래순번
        val tran_no: Int,  // 은행거래고유번호
        val bank_tran_id: String,  // 입금은행.표준코드
        val bank_code_std: String,  // 계좌번호
        val account_num: String,  // 입금계좌예금주명
        val account_holder_name: String,  // 입금계좌인자내역
        val print_content: String,  // 거래금액
        val tran_amt: Int,  // 요청고객성명
        val req_client_name: String,  // 요청고객회원번호
        val req_client_num: String,  // 이체용도
        val transfer_purpose: String
    )
}
