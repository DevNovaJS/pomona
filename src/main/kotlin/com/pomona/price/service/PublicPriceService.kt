package com.pomona.price.service

import com.pomona.price.domain.LatestPriceRepository
import com.pomona.price.domain.MonthlyVolumeRepository
import com.pomona.price.domain.TopOriginRepository
import com.pomona.price.domain.WeeklyPriceRepository
import com.pomona.price.model.BuildPeriod
import com.pomona.price.model.ItemMonthlyVolume
import com.pomona.price.model.ItemTopOrigins
import com.pomona.price.model.LatestPrice
import com.pomona.price.model.MonthlyVolume
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
    private val topOriginRepository: TopOriginRepository,
) {

    fun period(): BuildPeriod = buildPeriodService.current()

    fun latestPrices(): List<LatestPrice> = latestPriceRepository.findAll(buildPeriodService.current().baseDate)

    fun weeklyPrices(): List<WeeklyPrice> = weeklyPriceRepository.findAll(buildPeriodService.current().baseDate)

    fun volumes(): List<MonthlyVolume> = buildPeriodService.current().let { monthlyVolumeRepository.findAll(it.from, it.to) }

    fun itemVolumes(): List<ItemMonthlyVolume> = buildPeriodService.current().let { monthlyVolumeRepository.findItems(it.from, it.to) }

    fun tradingDays(): Map<YearMonth, Int> = buildPeriodService.current().let { monthlyVolumeRepository.countTradingDays(it.from, it.to) }

    fun origins(): List<TopOrigins> = buildPeriodService.current().let { topOriginRepository.findAll(it.from, it.to) }

    fun itemOrigins(): List<ItemTopOrigins> = buildPeriodService.current().let { topOriginRepository.findItems(it.from, it.to) }
}
