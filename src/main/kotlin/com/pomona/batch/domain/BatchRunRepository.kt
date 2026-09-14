package com.pomona.batch.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

/**
 * 배치 이력. 자연키가 없는 append-only 테이블이라 upsert 가 필요 없다.
 * 쓰기도 JPA 로 한다 — 한 번에 한 행만 쓰고 충돌할 일이 없다.
 */
interface BatchRunRepository : JpaRepository<BatchRun, Long> {

    /** 백오피스의 "최근 실행 이력". `ix_batch_run_job_date` 를 탄다. */
    fun findTop20ByJobNameOrderByTargetDateDescIdDesc(jobName: String): List<BatchRun>

    fun findFirstByJobNameAndStatusOrderByTargetDateDesc(jobName: String, status: BatchStatus): BatchRun?

    fun findByJobNameAndTargetDateOrderByIdDesc(jobName: String, targetDate: LocalDate): List<BatchRun>
}
