package com.pomona.price.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class WeeklyPriceTest {

    @Test
    fun `등락률은 소수 1자리로 반올림한다`() {
        val price = WeeklyPrice(varietyId = 1, thisWeekPerKg = BigDecimal("1000"), lastYearPerKg = BigDecimal("3000"))

        // (1000 - 3000) × 100 ÷ 3000 = -66.666...
        assertThat(price.changeRate).isEqualByComparingTo("-66.7")
    }
}
