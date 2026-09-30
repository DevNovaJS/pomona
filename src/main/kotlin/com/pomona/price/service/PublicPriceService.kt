package com.pomona.price.service

import com.pomona.price.domain.LatestPriceRepository
import com.pomona.price.domain.MonthlyVolumeRepository
import com.pomona.price.domain.RecentVolumeRepository
import com.pomona.price.domain.RetailPriceRepository
import com.pomona.price.domain.TopOriginRepository
import com.pomona.price.domain.WeeklyPriceRepository
import com.pomona.price.model.BuildPeriod
import com.pomona.price.model.ItemDailyVolume
import com.pomona.price.model.ItemMonthlyVolume
import com.pomona.price.model.ItemTopOrigins
import com.pomona.price.model.LatestPrice
import com.pomona.price.model.MonthlyVolume
import com.pomona.price.model.RecentVolume
import com.pomona.price.model.RetailPrice
import com.pomona.price.model.TopOrigins
import com.pomona.price.model.WeeklyPrice
import org.springframework.stereotype.Service
import java.time.YearMonth

/** 공개 빌드용 가격·물량·산지. 모두 [BuildPeriodService] 가 정한 기준일과 12개월로 조회한다. */
@Service
class PublicPriceService(
    private val buildPeriodService: BuildPeriodService,
    private val latestPriceRepository: LatestPriceRepository,
    private val weeklyPriceRepository: WeeklyPriceRepository,
    private val monthlyVolumeRepository: MonthlyVolumeRepository,
    private val recentVolumeRepository: RecentVolumeRepository,
    private val topOriginRepository: TopOriginRepository,
    private val retailPriceRepository: RetailPriceRepository,
) {

    fun period(): BuildPeriod = buildPeriodService.current()

    fun latestPrices(): List<LatestPrice> = latestPriceRepository.findAll(buildPeriodService.current().baseDate)

    fun weeklyPrices(): List<WeeklyPrice> = weeklyPriceRepository.findAll(buildPeriodService.current().baseDate)

    fun volumes(): List<MonthlyVolume> = buildPeriodService.current().let { monthlyVolumeRepository.findAll(it.from, it.to) }

    /** 메인의 "평소보다 많이 나오는 과일"용 최근 14일·평소 14일 물량. */
    fun recentVolumes(): List<RecentVolume> = recentVolumeRepository.findAll(buildPeriodService.current().baseDate)

    fun itemVolumes(): List<ItemMonthlyVolume> = buildPeriodService.current().let { monthlyVolumeRepository.findItems(it.from, it.to) }

    /** 과일 캘린더용 품목 일별 물량. 12개월 전부 */
    fun itemDailyVolumes(): List<ItemDailyVolume> = buildPeriodService.current().let { monthlyVolumeRepository.findItemDays(it.from, it.to) }

    fun tradingDays(): Map<YearMonth, Int> = buildPeriodService.current().let { monthlyVolumeRepository.countTradingDays(it.from, it.to) }

    fun origins(): List<TopOrigins> = buildPeriodService.current().let { topOriginRepository.findAll(it.from, it.to) }

    fun itemOrigins(): List<ItemTopOrigins> = buildPeriodService.current().let { topOriginRepository.findItems(it.from, it.to) }

    /** 매핑된 품종의 소매가. 기간은 도매 기준일이 아니라 소매 조사의 마지막 날부터 센다(소매 조사는 평일만 한다). */
    fun retailPrices(): List<RetailPrice> = retailPriceRepository.findAll()
}
