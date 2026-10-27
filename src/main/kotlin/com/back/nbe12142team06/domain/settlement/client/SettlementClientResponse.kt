package com.back.nbe12142team06.domain.settlement.client

data class SettlementClientResponse( // 거래고유번호(API)
    val api_tran_id: String,  // 거래일시(밀리세컨드)
    val api_tran_dtm: String,  // 응답코드(API)
    val rsp_code: String,  // 응답메시지(API)
    val rsp_message: String,  // 출금기관.표준코드
    val wd_bank_code_std: String,  // 출금기관.점별코드
    val wd_bank_code_sub: String,  // 출금기관명
    val wd_bank_name: String,  // 출금계좌번호(출력용)
    val wd_account_num_masked: String,  // 출금계좌인자내역
    val wd_print_content: String,  // 송금인성명
    val wd_account_holder_name: String,  // 입금건수
    val res_cnt: Int,  // 입금목록
    val res_list: List<Res>
) {
    constructor(account: String, name: String, amount: Int) : this(
        "2ffd133a-d17a-431d-a6a5",
        "20190910101921567",
        "A0000",
        "",
        "097",
        "1230001",
        "오픈은행",
        "000-1230000-***",
        "$name 동행 매니저님 정산 완료",
        "병원 동행 서비스 가지",
        1,
        listOf(
            Res(
                tran_no = 1,
                bank_tran_id = "F123456789U4BC34239Z",
                bank_tran_date = 20190910,
                bank_code_tran = "097",
                bank_rsp_code = "000",
                bank_rsp_message = "",
                bank_code_std = "097",
                bank_code_sub = "1230001",
                bank_name = "오픈은행",
                savings_bank_name = "오픈저축은행",
                account_num = account,
                account_seq = "001",
                account_num_masked = "${account.substring(0, 3)}-${account.substring(3, account.length - 3)}-***",
                print_content = "쇼핑몰환불",
                account_holder_name = name,
                tran_amt = amount,
                cms_num = "93848103221"
            )
        )
    )

    data class Res( // 거래순번
        val tran_no: Int,  // 거래고유번호(참가은행)
        val bank_tran_id: String,  // 거래일자(참가은행)
        val bank_tran_date: Int,  // 응답코드를 부여한 참가은행.표준코드
        val bank_code_tran: String,  // 응답코드(참가은행)
        val bank_rsp_code: String,  // 응답메시지(참가은행)
        val bank_rsp_message: String,  // 입금(개설)기관.표준코드
        val bank_code_std: String,  // 입금(개설)기관.점별코드
        val bank_code_sub: String,  // 입금(개설)기관명
        val bank_name: String,  // 개별저축은행명
        val savings_bank_name: String,  // 입금계좌번호
        val account_num: String,  // 회차번호
        val account_seq: String,  // 입금계좌번호(출력용)
        val account_num_masked: String,  // 입금계좌인자내역
        val print_content: String,  // 수취인성명
        val account_holder_name: String,  // 거래금액
        val tran_amt: Int,  // CMS 번호
        val cms_num: String
    )
}
