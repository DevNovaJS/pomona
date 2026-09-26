package com.pomona.variety.service

import com.pomona.price.service.BuildPeriodService
import com.pomona.variety.domain.ItemRepository
import com.pomona.variety.domain.PageVarietyRepository
import com.pomona.variety.model.Item
import com.pomona.variety.model.PageVariety
import org.springframework.stereotype.Service

/** 공개 빌드용 품목·품종 목록. 어떤 페이지를 만들지를 [BuildPeriodService] 의 기준일로 정한다. */
@Service
class PublicVarietyService(
    private val buildPeriodService: BuildPeriodService,
    private val itemRepository: ItemRepository,
    private val pageVarietyRepository: PageVarietyRepository,
) {

    fun items(): List<Item> = itemRepository.findAll(buildPeriodService.current().baseDate)

    fun varieties(): List<PageVariety> = pageVarietyRepository.findAll(buildPeriodService.current().baseDate)
}
