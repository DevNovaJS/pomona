package com.pomona.price.model

import java.time.LocalDate

/**
 * 품종 페이지에 붙이는 소매가. 매핑된 소매 품종의 최근 7일 조사값에서 낸다.
 *
 * 가격은 늘 "[unitSize] [unit] 당" 이다. 무게 단위로 조사된 품목은 1 kg 으로 맞추고(도매가와 비교된다),
 * 개수 단위로 조사된 품목은 조사 단위 그대로 둔다(사과 10개).
 */
data class RetailPrice(
    /** 정산 품종 id. 품종 페이지와 이어 붙이는 키 */
    val varietyId: Long,
    /** 소매 쪽 이름. 정산 품종과 표기가 다를 수 있다(샤인마스캇 / 샤인머스켓) */
    val itemName: String,
    val retailVarietyName: String,
    val from: LocalDate,
    val to: LocalDate,
    val unit: String,
    val unitSize: Int,
    /** 등급별 가격. 대표 등급은 고르지 않는다. 등급 코드 순 */
    val grades: List<RetailGradePrice>,
)

data class RetailGradePrice(
    val grdCd: String,
    val grdNm: String,
    /** 점포마다 7일 중앙값을 낸 뒤 그 점포 값들의 중앙값. 원 단위 반올림 */
    val price: Long,
    /** 값을 낸 점포 수 */
    val storeCount: Int,
)
