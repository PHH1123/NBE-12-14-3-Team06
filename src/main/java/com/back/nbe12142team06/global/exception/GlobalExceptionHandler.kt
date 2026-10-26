package com.back.nbe12142team06.global.exception

import com.back.nbe12142team06.global.response.RsData
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

// @ExceptionHandler 는 파라미터 타입으로 처리할 예외를 정하므로, 본문에서 안 쓰는 파라미터도 지우면 안 된다
@Suppress("UNUSED_PARAMETER")
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    // DTO로 변환은 가능하지만 DTO 검증에서 걸리는 경우 예시로 @NotBlank 위반
    @ExceptionHandler
    fun methodArgumentNotValidExceptionHandler(e: MethodArgumentNotValidException): RsData<*> {
        val msg = e.bindingResult.fieldErrors
            .map { "${it.field}: ${it.defaultMessage}" }
            .sorted()
            .joinToString(", ")

        return RsData<Void>("400-01", msg)
    }

    // DTO로 변환 자체가 불가할때 예시로 우리 role에는 없는 "role": "GG" 같은 요청
    @ExceptionHandler
    fun httpMessageNotReadableExceptionHandler(e: HttpMessageNotReadableException): RsData<*> {
        return RsData<Void>("400-02", "요청 본문의 형식이 올바르지 않습니다.")
    }

    @ExceptionHandler
    fun businessExceptionHandler(e: BusinessException): RsData<*> {
        return RsData<Void>(e.statusCode, e.message)
    }

    @ExceptionHandler
    fun notFoundExceptionHandler(e: NotFoundException): RsData<*> {
        log.warn("[{}] {}", e.statusCode, e.message)
        return RsData<Void>(e.statusCode, e.message)
    }

    @ExceptionHandler
    fun invalidExceptionHandler(e: InvalidException): RsData<*> {
        return RsData<Void>(e.statusCode, e.message)
    }

    @ExceptionHandler
    fun internalServerErrorExceptionHandler(e: InternalServerErrorException): RsData<*> {
        return RsData<Void>(e.statusCode, e.message)
    }

    @ExceptionHandler
    fun unAuthorizedExceptionHandler(e: UnauthorizedException): RsData<*> {
        return RsData<Void>(e.statusCode, e.message)
    }

    @ExceptionHandler
    fun forbiddenExceptionHandler(e: ForbiddenException): RsData<*> {
        return RsData<Void>(e.statusCode, e.message)
    }

    // 서비스 계층에서 미처 잡지 못한 DB 유니크 제약 위반이 여기까지 새어나왔을 때의
    // 최후 방어선. 동시 요청 경쟁 등으로 발생하며, 원문 메시지에는 제약 이름·테이블
    // 구조가 그대로 들어있어 그대로 응답에 담으면 내부 스키마가 노출된다.
    // 아래 catch-all exceptionHandler(Exception) 보다 위에 둬야 한다 — 안 그러면
    // 이 예외가 거기서 500 으로 뭉개진다.
    @ExceptionHandler
    fun dataIntegrityViolationExceptionHandler(e: DataIntegrityViolationException): RsData<*> {
        return RsData<Void>("409-02", "이미 처리된 요청입니다.")
    }

    // 스프링이 던지는 "요청 자체가 잘못된" 예외들. 아래 Exception 핸들러가 가로채면 사용자 실수가 500 으로 나가서 따로 처리한다.

    // GET /api/v1/posts/abc (숫자 자리에 문자), ?page=abc
    @ExceptionHandler
    fun methodArgumentTypeMismatchExceptionHandler(e: MethodArgumentTypeMismatchException): RsData<*> {
        log.warn("[400-01] 요청 파라미터 타입 오류: {}", e.name)
        return RsData<Void>("400-03", "요청 파라미터의 형식이 올바르지 않습니다.")
    }

    // 필수 쿼리 파라미터 누락
    @ExceptionHandler
    fun missingServletRequestParameterExceptionHandler(e: MissingServletRequestParameterException): RsData<*> {
        log.warn("[400-02] 필수 요청 파라미터 누락: {}", e.parameterName)
        return RsData<Void>("400-04", "필수 요청 파라미터가 없습니다: " + e.parameterName)
    }

    // 없는 URL 호출
    @ExceptionHandler
    fun noResourceFoundExceptionHandler(e: NoResourceFoundException): RsData<*> {
        log.warn("[404-0] 존재하지 않는 경로: {}", e.resourcePath)
        return RsData<Void>("404-0", "존재하지 않는 경로입니다.")
    }

    // 지원하지 않는 HTTP 메서드 (GET 만 되는 곳에 DELETE 등)
    @ExceptionHandler
    fun httpRequestMethodNotSupportedExceptionHandler(e: HttpRequestMethodNotSupportedException): RsData<*> {
        log.warn("[405-0] 지원하지 않는 HTTP 메서드: {}", e.method)
        return RsData<Void>("405-0", "지원하지 않는 HTTP 메서드입니다.")
    }

    @ExceptionHandler
    fun exceptionHandler(e: Exception): RsData<*> {
        log.error("처리되지 않은 예외 발생", e)
        return RsData<Void>("500-0", "서버 오류가 발생했습니다.")
    }

    // 같은 데이터를 동시에 수정해 낙관적 락(@Version) 충돌이 난 경우
    @ExceptionHandler
    fun concurrentUpdateHandler(e: OptimisticLockingFailureException): RsData<*> {
        return RsData<Void>("409-01", "다른 요청과 충돌했습니다. 다시 시도해주세요.")
    }

    // SSE 연결 종료 시 잡는 예외
    @ExceptionHandler
    fun asyncRequestNotUsableHandler(e: AsyncRequestNotUsableException) {
        log.debug("SSE 연결이 이미 종료됨: {}", e.message)
    }
}
