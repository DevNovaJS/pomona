package com.pomona.batch.controller

import com.pomona.batch.model.BatchOverviewResponse
import com.pomona.batch.model.BatchRunResponse
import com.pomona.batch.service.BatchAdminService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** 백오피스 배치 관리. 수집 현황·이력을 보고, 실패 건을 재실행하고, 기간을 정해 다시 수집한다. */
@RestController
@RequestMapping("/api/admin/batch")
class BatchController(private val batchAdminService: BatchAdminService) {

    @GetMapping("/status")
    fun status(): BatchOverviewResponse = batchAdminService.overview()

    @GetMapping("/runs")
    fun recentRuns(): List<BatchRunResponse> = batchAdminService.recentRuns()

    @GetMapping("/failures")
    fun failures(): List<BatchRunResponse> = batchAdminService.failures()

    @PostMapping("/runs/{id}/retry")
    fun retry(@PathVariable id: Long): BatchRunResponse = batchAdminService.retry(id)

    /** 뒤에서 돌리므로 끝을 기다리지 않고 202 를 준다. */
    @PostMapping("/collect")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun collect(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ) {
        batchAdminService.collect(from, to)
    }
}
