package com.back.nbe12142team06.global.response

import jakarta.servlet.http.HttpServletResponse
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class ResponseAspect(
    private val response: HttpServletResponse,
) {
    @Around(
        """
        (
            within
            (
                @org.springframework.web.bind.annotation.RestController *
            )
            &&
            (
                @annotation(org.springframework.web.bind.annotation.GetMapping)
                ||
                @annotation(org.springframework.web.bind.annotation.PostMapping)
                ||
                @annotation(org.springframework.web.bind.annotation.PutMapping)
                ||
                @annotation(org.springframework.web.bind.annotation.PatchMapping)
                ||
                @annotation(org.springframework.web.bind.annotation.DeleteMapping)
            )
        )
        ||
        @annotation(org.springframework.web.bind.annotation.ResponseBody)
        ||
        (
            within
            (
                @org.springframework.web.bind.annotation.RestControllerAdvice *
            )
            &&
            (
                @annotation(org.springframework.web.bind.annotation.ExceptionHandler)
            )
        )
        """
    )
    fun responseAspect(joinPoint: ProceedingJoinPoint): Any? =
        joinPoint.proceed().also { rst ->   // 실제 수행 메서드
            // RsData 를 반환하면 statusCode 앞자리를 HTTP 상태 코드로 사용
            if (rst is RsData<*>) {
                response.status = rst.simpleStatusCode()
            }
        }
}