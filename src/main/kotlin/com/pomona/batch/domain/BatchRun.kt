package com.pomona.batch.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.OffsetDateTime

/** 배치 실행 결과. 휴장·미조사로 0행인 것은 실패가 아니라 [EMPTY] 다. */
enum class BatchStatus { RUNNING, SUCCESS, EMPTY, FAILED }

/**
 * 수집 배치 실행 이력. API 에서 온 컬럼이 하나도 없다.
 *
 * 같은 날짜를 여러 번 재수집하고 그 시도를 전부 남기는 게 목적이라 **자연키가 없다.**
 * 중복이 곧 이력이므로 upsert 가 아니라 append 다.
 */
@Entity
@Table(name = "batch_run")
class BatchRun(

    @Column(nullable = false, length = 40)
    val jobName: String,

    /** 수집 대상 날짜. 확정 지연 때문에 매일 D-1~D-5 를 다시 긁으므로 같은 날짜가 여러 번 나온다. */
    @Column(nullable = false)
    val targetDate: LocalDate,

    /** 재실행 버튼이 그대로 다시 쓰는 호출 파라미터. 두 API 의 모양이 달라 컬럼으로 못 펼친다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val params: String,

    /** 실행 시작 시각. 만드는 순간으로 정해지고 바뀌지 않는다. 기본값이 있어 생략하면 지금 시각이 들어간다. */
    @Column(nullable = false)
    val startedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var status: BatchStatus = BatchStatus.RUNNING
        protected set

    @Column(nullable = false)
    var rowCount: Int = 0
        protected set

    @Column(columnDefinition = "text")
    var message: String? = null
        protected set

    @Column
    var finishedAt: OffsetDateTime? = null
        protected set

    fun succeed(rowCount: Int) {
        val status = if (rowCount == 0) {
            BatchStatus.EMPTY
        } else {
            BatchStatus.SUCCESS
        }

        finish(status, rowCount, null)
    }

    fun fail(message: String) = finish(BatchStatus.FAILED, 0, message)

    private fun finish(status: BatchStatus, rowCount: Int, message: String?) {
        check(this.status == BatchStatus.RUNNING) { "이미 끝난 실행이다: ${this.status}" }
        this.status = status
        this.rowCount = rowCount
        this.message = message
        this.finishedAt = OffsetDateTime.now()
    }

    override fun toString(): String = "BatchRun(id=$id, $jobName $targetDate $status ${rowCount}행)"
}
