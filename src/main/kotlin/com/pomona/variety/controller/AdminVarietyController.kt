package com.pomona.variety.controller

import com.pomona.variety.model.VarietyResponse
import com.pomona.variety.service.AdminVarietyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 백오피스 품종 목록. 리뷰의 품종 연결과 품종 매핑에서 고른다. */
@RestController
@RequestMapping("/api/admin/varieties")
class AdminVarietyController(private val adminVarietyService: AdminVarietyService) {

    @GetMapping
    fun varieties(): List<VarietyResponse> = adminVarietyService.varieties()
}
