package com.pomona.review.domain

import com.pomona.variety.domain.VarietyMaster
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class ReviewTest {

    private val banana = VarietyMaster("06", "과실류", "16", "바나나", "01", "바나나")

    private fun review(price: Int, weightGram: Int?) = Review(
        variety = banana, eatenDate = LocalDate.of(2026, 9, 20), title = "바나나", store = "동네 마트",
        origin = null, price = price, weightGram = weightGram, rating = 4, body = "달다",
    )

    @Test
    fun `무게를 알면 kg당 가격을 원 단위로 반올림해 준다`() {
        assertThat(review(price = 25_000, weightGram = 2_000).pricePerKg).isEqualByComparingTo("12500")
        // 4,980 ÷ 1.3kg = 3,830.769...
        assertThat(review(price = 4_980, weightGram = 1_300).pricePerKg).isEqualByComparingTo("3831")
    }

    @Test
    fun `무게를 모르면 kg당 가격이 없다`() {
        assertThat(review(price = 4_980, weightGram = null).pricePerKg).isNull()
    }

    @Test
    fun `수정하면 값이 바뀌고 수정 시각이 갱신된다`() {
        val review = review(price = 4_980, weightGram = null)
        val before = review.updatedAt

        review.update(
            variety = banana, eatenDate = LocalDate.of(2026, 9, 21), title = "다시 먹은 바나나", store = "시장",
            origin = "필리핀", price = 3_000, weightGram = 1_000, rating = 5, body = "더 달다",
        )

        assertThat(review.title).isEqualTo("다시 먹은 바나나")
        assertThat(review.pricePerKg).isEqualByComparingTo("3000")
        assertThat(review.updatedAt).isAfterOrEqualTo(before)
        assertThat(review.createdAt).isBeforeOrEqualTo(OffsetDateTime.now())
    }
}
