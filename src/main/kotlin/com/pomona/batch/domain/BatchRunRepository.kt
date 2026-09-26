package com.pomona.batch.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

/**
 * 배치 이력. 자연키가 없는 append-only 테이블이라 upsert 가 필요 없다.
 * 쓰기도 JPA 로 한다 — 한 번에 한 행만 쓰고 충돌할 일이 없다.
 */
interface BatchRunRepository : JpaRepository<BatchRun, Long> {

    /** 백오피스의 "최근 실행 이력". 최근에 시작한 순. */
    fun findTop50ByOrderByIdDesc(): List<BatchRun>

    fun findByJobNameAndTargetDateOrderByIdDesc(jobName: String, targetDate: LocalDate): List<BatchRun>

    /** 작업별 마지막 성공 날짜. 백오피스 현황의 "최신 수집일". */
    @Query("select b.jobName as jobName, max(b.targetDate) as targetDate from BatchRun b where b.status = :status group by b.jobName")
    fun findLastDates(status: BatchStatus = BatchStatus.SUCCESS): List<JobDate>

    /**
     * 아직 안 풀린 실패. 같은 작업·같은 params 로 **마지막에** 돈 실행이 실패인 것만 준다.
     * 다음 날 D-1~D-5 재수집이나 재실행으로 성공하면 빠지고, 재실행도 실패하면 최신 한 건만 남는다.
     *
     * `distinct on (job_name, params)` 은 묶음마다 `order by` 의 첫 행 하나만 남긴다. PostgreSQL 전용이다.
     */
    @Query(
        nativeQuery = true,
        value = """
            select * from (
                select distinct on (job_name, params) *
                  from batch_run
                 order by job_name, params, id desc
            ) latest
             where status = 'FAILED'
             order by id desc
        """,
    )
    fun findUnresolvedFailures(): List<BatchRun>
}

/** [BatchRunRepository.findLastDates] 한 줄. 인터페이스만 두면 Spring Data 가 구현을 만든다. */
interface JobDate {
    val jobName: String
    val targetDate: LocalDate
}
