package com.pomona.batch.service

import com.pomona.batch.BatchRunNotFoundException
import com.pomona.batch.domain.BatchRunRepository
import com.pomona.batch.model.BatchOverviewResponse
import com.pomona.batch.model.BatchRunResponse
import com.pomona.batch.model.JobStatusResponse
import com.pomona.batch.model.toResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDate

/** 백오피스 배치 관리. 수집 현황·이력을 모아 보여주고, 재실행과 기간 재수집을 [RangeCollector] 로 넘긴다. */
@Service
class BatchAdminService(
    private val batchRunRepository: BatchRunRepository,
    private val rangeCollector: RangeCollector,
) {

    /** 수집 중인지와 작업별 최신 성공일·안 풀린 실패 건수. 작업은 도매·소매 두 개를 항상 이 순서로 준다. */
    fun overview(): BatchOverviewResponse {
        val lastDates = batchRunRepository.findLastDates().associate { it.jobName to it.targetDate }
        val failures = batchRunRepository.findUnresolvedFailures().groupingBy { it.jobName }.eachCount()
        return BatchOverviewResponse(
            running = rangeCollector.isRunning,
            jobs = listOf(WHOLESALE_JOB, RETAIL_JOB).map { JobStatusResponse(it, lastDates[it], failures[it] ?: 0) },
        )
    }

    fun recentRuns(): List<BatchRunResponse> = batchRunRepository.findTop50ByOrderByIdDesc().map { it.toResponse() }

    fun failures(): List<BatchRunResponse> = batchRunRepository.findUnresolvedFailures().map { it.toResponse() }

    /** 같은 조건으로 한 건 다시. 하루치나 품목 하나라 금방 끝나므로 기다렸다가 새 실행 기록을 준다. */
    fun retry(id: Long): BatchRunResponse {
        val run = batchRunRepository.findByIdOrNull(id) ?: throw BatchRunNotFoundException(id)
        return rangeCollector.retry(run).toResponse()
    }

    /** 기간 재수집. 뒤에서 돌리고 바로 돌아온다. 진행은 [overview] 의 running 과 [recentRuns] 로 본다. */
    fun collect(from: LocalDate, to: LocalDate) = rangeCollector.startAll(from, to)
}
