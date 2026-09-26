package com.pomona.variety.controller

import com.pomona.price.domain.WholesaleDailyRepository
import com.pomona.variety.domain.ItemRepository
import com.pomona.variety.domain.PageVarietyRepository
import com.pomona.variety.model.Item
import com.pomona.variety.model.PageVariety
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 공개 빌드용. 어떤 품목·품종 페이지를 만들지와 셀렉트박스 목록. 기준일은 DB 의 마지막 거래일. */
@RestController
@RequestMapping("/api/public")
class VarietyController(
    private val pageVarieties: PageVarietyRepository,
    private val items: ItemRepository,
    private val wholesale: WholesaleDailyRepository,
) {

    @GetMapping("/items")
    fun items(): List<Item> = items.findAll(wholesale.findLastTradeDate())

    @GetMapping("/varieties")
    fun varieties(): List<PageVariety> = pageVarieties.findAll(wholesale.findLastTradeDate())
}
