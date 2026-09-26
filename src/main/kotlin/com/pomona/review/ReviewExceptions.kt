package com.pomona.review

import com.pomona.common.BadRequestException
import com.pomona.common.NotFoundException

/** 찾는 리뷰가 없을 때. */
class ReviewNotFoundException(id: Long) : NotFoundException("리뷰가 없다: $id")

/** 요청에 적힌 품종이 없을 때. 요청 본문이 잘못된 것이라 404 가 아니라 400 이다. */
class UnknownVarietyException(varietyId: Long) : BadRequestException("없는 품종이다: $varietyId")
