package com.pomona.common

import com.pomona.batch.CollectAlreadyRunningException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/** 에러 응답 본문. */
data class ErrorResponse(val message: String?)

/**
 * 모든 컨트롤러의 예외를 HTTP 상태 코드로 바꾼다. 서비스는 웹을 모르고 자기 예외만 던진다.
 *
 * 서비스 예외는 [NotFoundException]·[BadRequestException] 을 물려받아 여기서 한 번에 처리한다.
 * 자바 표준 예외(`NoSuchElementException` 등)로는 잡지 않는다 — 그러면 코드 버그로 난 `single()` 실패까지
 * "없음" 으로 가려진다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NotFoundException) = ErrorResponse(e.message)

    @ExceptionHandler(BadRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: BadRequestException) = ErrorResponse(e.message)

    @ExceptionHandler(CollectAlreadyRunningException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun collectAlreadyRunning(e: CollectAlreadyRunningException) = ErrorResponse(e.message)

    /**
     * 요청 값이 DB 제약(별점 0~5, 가격·무게 0 초과 등)에 걸렸을 때. 같은 검사를 코드에 다시 쓰지 않고 DB 가 막게 둔다.
     * 사유에는 걸린 제약 이름(`ck_review_rating` 등)이 들어 있다.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun dataIntegrityViolation(e: DataIntegrityViolationException) = ErrorResponse(e.mostSpecificCause.message)
}
