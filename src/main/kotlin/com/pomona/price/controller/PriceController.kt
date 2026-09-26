package com.pomona.price.controller

import com.pomona.price.model.BuildPeriod
import com.pomona.price.model.ItemMonthlyVolume
import com.pomona.price.model.ItemTopOrigins
import com.pomona.price.model.LatestPrice
import com.pomona.price.model.MonthlyVolume
import com.pomona.price.model.TopOrigins
import com.pomona.price.model.WeeklyPrice
import com.pomona.price.service.PublicPriceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

/**
 * 공개 빌드용. 가격·물량·산지를 종류별로 전부 한 번에 준다. 품종·품목 id 로 [com.pomona.variety.controller.VarietyController] 의
 * 목록과 이어 붙인다.
 */
@RestController
@RequestMapping("/api/public")
class PriceController(private val publicPriceService: PublicPriceService) {

    /** 기준일과 12개월 범위. 막대 그래프가 거래 없는 달까지 12칸을 그리는 데 쓴다. */
    @GetMapping("/period")
    fun period(): BuildPeriod = publicPriceService.period()

    @GetMapping("/prices/latest")
    fun latestPrices(): List<LatestPrice> = publicPriceService.latestPrices()

    @GetMapping("/prices/weekly")
    fun weeklyPrices(): List<WeeklyPrice> = publicPriceService.weeklyPrices()

    @GetMapping("/volumes")
    fun volumes(): List<MonthlyVolume> = publicPriceService.volumes()

    @GetMapping("/volumes/items")
    fun itemVolumes(): List<ItemMonthlyVolume> = publicPriceService.itemVolumes()

    @GetMapping("/trading-days")
    fun tradingDays(): Map<YearMonth, Int> = publicPriceService.tradingDays()

    @GetMapping("/origins")
    fun origins(): List<TopOrigins> = publicPriceService.origins()

    @GetMapping("/origins/items")
    fun itemOrigins(): List<ItemTopOrigins> = publicPriceService.itemOrigins()
}
