package com.pomona.review.model

import java.time.LocalDate

/**
 * 리뷰 작성·수정 요청 본문. 값의 범위(별점 0~5, 가격·무게 0 초과)는 여기서 다시 검사하지 않는다 —
 * DB 체크 제약이 막고 [com.pomona.common.GlobalExceptionHandler] 가 400 으로 돌려준다.
 */
data class ReviewRequest(
    val varietyId: Long,
    val eatenDate: LocalDate,
    val title: String,
    val store: String,
    val origin: String?,
    val price: Int,
    /** 모르면 빼고 보낸다 */
    val weightGram: Int? = null,
    val rating: Int,
    val body: String,
)
