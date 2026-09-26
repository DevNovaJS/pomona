package com.pomona.variety.controller

import com.pomona.variety.model.Item
import com.pomona.variety.model.PageVariety
import com.pomona.variety.service.PublicVarietyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 공개 빌드용. 어떤 품목·품종 페이지를 만들지와 셀렉트박스 목록. */
@RestController
@RequestMapping("/api/public")
class VarietyController(private val publicVarietyService: PublicVarietyService) {

    @GetMapping("/items")
    fun items(): List<Item> = publicVarietyService.items()

    @GetMapping("/varieties")
    fun varieties(): List<PageVariety> = publicVarietyService.varieties()
}
