package com.pomona.common

import com.pomona.batch.service.BatchRunNotFoundException
import com.pomona.batch.service.CollectAlreadyRunningException
import com.pomona.review.service.ReviewNotFoundException
import com.pomona.review.service.UnknownVarietyException
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
 * 예외는 구체적인 타입으로만 잡는다. 예를 들어 `NoSuchElementException` 전체를 404 로 잡으면
 * 코드 버그로 난 `single()` 실패까지 "없음" 으로 가려진다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BatchRunNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun batchRunNotFound(e: BatchRunNotFoundException) = ErrorResponse(e.message)

    @ExceptionHandler(CollectAlreadyRunningException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun collectAlreadyRunning(e: CollectAlreadyRunningException) = ErrorResponse(e.message)

    @ExceptionHandler(ReviewNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun reviewNotFound(e: ReviewNotFoundException) = ErrorResponse(e.message)

    @ExceptionHandler(UnknownVarietyException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun unknownVariety(e: UnknownVarietyException) = ErrorResponse(e.message)

    /**
     * 요청 값이 DB 제약(별점 0~5, 가격·무게 0 초과 등)에 걸렸을 때. 같은 검사를 코드에 다시 쓰지 않고 DB 가 막게 둔다.
     * 사유에는 걸린 제약 이름(`ck_review_rating` 등)이 들어 있다.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun dataIntegrityViolation(e: DataIntegrityViolationException) = ErrorResponse(e.mostSpecificCause.message)
}
