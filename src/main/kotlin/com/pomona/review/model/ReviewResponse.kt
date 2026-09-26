package com.pomona.review.model

import com.pomona.price.model.MarketPrice
import com.pomona.review.domain.Review
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/** 리뷰 응답. 품종은 id 와 함께 이름을 붙인다 — 페이지가 없는 소량 품종이면 공개 품종 목록에 없어서 이름을 거기서 찾을 수 없다. */
data class ReviewResponse(
    val id: Long,
    val varietyId: Long,
    val itemName: String,
    val varietyName: String?,
    val eatenDate: LocalDate,
    val title: String,
    val store: String,
    val origin: String?,
    val price: Int,
    val weightGram: Int?,
    /** kg당 산 가격. 무게를 모르면 null */
    val pricePerKg: BigDecimal?,
    val rating: Int,
    val body: String,
    /** 먹은 날 당일 또는 그 전 마지막 거래일의 도매 시세. 먹은 날 포함 7일 안에 거래가 없으면 null */
    val marketPrice: MarketPrice?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

fun Review.toResponse(marketPrice: MarketPrice?) = ReviewResponse(
    id = id!!, varietyId = variety.id!!, itemName = variety.mclsfNm, varietyName = variety.sclsfNm,
    eatenDate = eatenDate, title = title, store = store, origin = origin,
    price = price, weightGram = weightGram, pricePerKg = pricePerKg, rating = rating, body = body,
    marketPrice = marketPrice, createdAt = createdAt, updatedAt = updatedAt,
)
