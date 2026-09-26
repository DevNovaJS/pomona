package com.pomona.variety.controller

import com.pomona.variety.model.RetailVarietyResponse
import com.pomona.variety.model.UnmappedVarietyResponse
import com.pomona.variety.model.VarietyMappingRequest
import com.pomona.variety.model.VarietyMappingResponse
import com.pomona.variety.service.VarietyMappingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 백오피스 품종 매핑. */
@RestController
@RequestMapping("/api/admin/mappings")
class VarietyMappingController(private val varietyMappingService: VarietyMappingService) {

    @GetMapping
    fun mappings(): List<VarietyMappingResponse> = varietyMappingService.mappings()

    @GetMapping("/unmapped")
    fun unmapped(): List<UnmappedVarietyResponse> = varietyMappingService.unmapped()

    @GetMapping("/retail-varieties")
    fun retailVarieties(): List<RetailVarietyResponse> = varietyMappingService.retailVarieties()

    /** 짝을 짓거나 바꾼다. 본문의 retailVarietyId 가 null 이면 "소매에 없음". */
    @PutMapping("/{varietyId}")
    fun map(@PathVariable varietyId: Long, @RequestBody request: VarietyMappingRequest): VarietyMappingResponse =
        varietyMappingService.map(varietyId, request.retailVarietyId)

    @DeleteMapping("/{varietyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unmap(@PathVariable varietyId: Long) {
        varietyMappingService.unmap(varietyId)
    }
}
