package com.pomona.price.controller

import com.pomona.price.model.BuildPeriod
import com.pomona.price.model.DailyPrice
import com.pomona.price.model.ItemDailyVolume
import com.pomona.price.model.ItemMonthlyVolume
import com.pomona.price.model.ItemTopOrigins
import com.pomona.price.model.LatestPrice
import com.pomona.price.model.MonthlyVolume
import com.pomona.price.model.RecentVolume
import com.pomona.price.model.RetailPrice
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

    /** 품종 페이지의 최근 30일 추이. 품종별로 기준일 포함 30일 안의 거래일마다 한 행 */
    @GetMapping("/prices/daily")
    fun dailyPrices(): List<DailyPrice> = publicPriceService.dailyPrices()

    @GetMapping("/retail-prices")
    fun retailPrices(): List<RetailPrice> = publicPriceService.retailPrices()

    @GetMapping("/volumes")
    fun volumes(): List<MonthlyVolume> = publicPriceService.volumes()

    /** 메인의 "평소보다 많이 나오는 과일". 품종별 최근 14일 물량과 평소 14일 물량, 그 배수. */
    @GetMapping("/volumes/recent")
    fun recentVolumes(): List<RecentVolume> = publicPriceService.recentVolumes()

    @GetMapping("/volumes/items")
    fun itemVolumes(): List<ItemMonthlyVolume> = publicPriceService.itemVolumes()

    /** 과일 캘린더. 품목별 하루 물량, 12개월 전부 */
    @GetMapping("/volumes/items/daily")
    fun itemDailyVolumes(): List<ItemDailyVolume> = publicPriceService.itemDailyVolumes()

    @GetMapping("/trading-days")
    fun tradingDays(): Map<YearMonth, Int> = publicPriceService.tradingDays()

    @GetMapping("/origins")
    fun origins(): List<TopOrigins> = publicPriceService.origins()

    @GetMapping("/origins/items")
    fun itemOrigins(): List<ItemTopOrigins> = publicPriceService.itemOrigins()
}
