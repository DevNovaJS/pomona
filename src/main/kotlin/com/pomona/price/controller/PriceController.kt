package com.pomona.price.controller

import com.pomona.price.domain.LatestPriceRepository
import com.pomona.price.domain.MonthlyVolumeRepository
import com.pomona.price.domain.TopOriginRepository
import com.pomona.price.domain.WeeklyPriceRepository
import com.pomona.price.domain.WholesaleDailyRepository
import com.pomona.price.model.BuildPeriod
import com.pomona.price.model.ItemMonthlyVolume
import com.pomona.price.model.ItemTopOrigins
import com.pomona.price.model.LatestPrice
import com.pomona.price.model.MonthlyVolume
import com.pomona.price.model.TopOrigins
import com.pomona.price.model.WeeklyPrice
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

/**
 * 공개 빌드용. 가격·물량·산지를 종류별로 전부 한 번에 준다. 품종·품목 id 로 [com.pomona.variety.controller.VarietyController] 의
 * 목록과 이어 붙인다. 기준일은 DB 의 마지막 거래일이고 기간은 [BuildPeriod] 가 정한다.
 */
@RestController
@RequestMapping("/api/public")
class PriceController(
    private val wholesale: WholesaleDailyRepository,
    private val latestPrices: LatestPriceRepository,
    private val weeklyPrices: WeeklyPriceRepository,
    private val volumes: MonthlyVolumeRepository,
    private val origins: TopOriginRepository,
) {

    /** 기준일과 12개월 범위. 막대 그래프가 거래 없는 달까지 12칸을 그리는 데 쓴다. */
    @GetMapping("/period")
    fun period(): BuildPeriod = currentPeriod()

    @GetMapping("/prices/latest")
    fun latestPrices(): List<LatestPrice> = latestPrices.findAll(currentPeriod().baseDate)

    @GetMapping("/prices/weekly")
    fun weeklyPrices(): List<WeeklyPrice> = weeklyPrices.findAll(currentPeriod().baseDate)

    @GetMapping("/volumes")
    fun volumes(): List<MonthlyVolume> = currentPeriod().let { volumes.findAll(it.from, it.to) }

    @GetMapping("/volumes/items")
    fun itemVolumes(): List<ItemMonthlyVolume> = currentPeriod().let { volumes.findItems(it.from, it.to) }

    @GetMapping("/trading-days")
    fun tradingDays(): Map<YearMonth, Int> = currentPeriod().let { volumes.countTradingDays(it.from, it.to) }

    @GetMapping("/origins")
    fun origins(): List<TopOrigins> = currentPeriod().let { origins.findAll(it.from, it.to) }

    @GetMapping("/origins/items")
    fun itemOrigins(): List<ItemTopOrigins> = currentPeriod().let { origins.findItems(it.from, it.to) }

    private fun currentPeriod() = BuildPeriod(wholesale.findLastTradeDate())
}
