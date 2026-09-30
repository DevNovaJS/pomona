package com.pomona.price.domain

import com.pomona.price.model.RetailGradePrice
import com.pomona.price.model.RetailPrice
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 품종 페이지 소매가. 저장하지 않고 조회할 때마다 계산한다(7일치 수천 행이라 수 ms).
 *
 * 소매 조사는 품종 하나에 점포 수십 곳 × 등급 × 조사일만큼 값이 와서, 이걸 등급마다 숫자 하나로 줄인다.
 * 한 통에 넣고 중앙값을 내면 자주 조사된 점포(7일 중 5일)가 드물게 조사된 점포(1일)보다 다섯 배 표를 가져
 * 조사 빈도가 값을 움직인다(실측 배 원황 30,000 vs 24,300). 그래서 점포마다 먼저 하나로 줄인 뒤 점포끼리 중앙값을 낸다.
 */
@Repository
class RetailPriceRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 매핑된 정산 품종 전부. 품종 id 순, 등급 코드 순. */
    fun findAll(): List<RetailPrice> =
        jdbcTemplate.query(SQL) { rs, _ ->
            RetailPriceRow(
                varietyId = rs.getLong("variety_id"),
                itemName = rs.getString("item_nm"),
                retailVarietyName = rs.getString("vrty_nm"),
                from = rs.getObject("from_date", LocalDate::class.java),
                to = rs.getObject("to_date", LocalDate::class.java),
                unit = rs.getString("price_unit"),
                unitSize = rs.getInt("price_unit_size"),
                grade = RetailGradePrice(rs.getString("grd_cd"), rs.getString("grd_nm"), rs.getLong("price"), rs.getInt("store_count")),
            )
        }
            .groupBy { Triple(it.varietyId, it.unit, it.unitSize) }
            .map { (_, rows) ->
                val first = rows.first()
                RetailPrice(
                    varietyId = first.varietyId, itemName = first.itemName, retailVarietyName = first.retailVarietyName,
                    from = first.from, to = first.to, unit = first.unit, unitSize = first.unitSize,
                    grades = rows.map { it.grade },
                )
            }
}

/** 쿼리 한 행: 품종 하나의 등급 하나. 품종별로 모아 [RetailPrice] 를 만든다. */
private class RetailPriceRow(
    val varietyId: Long,
    val itemName: String,
    val retailVarietyName: String,
    val from: LocalDate,
    val to: LocalDate,
    val unit: String,
    val unitSize: Int,
    val grade: RetailGradePrice,
)

/** 마지막 조사일 포함 7일. 소매 조사는 평일만 해서 조사일로는 5일 안팎이다. */
private const val DAYS = 7

private const val IS_WEIGHT = "d.unit in ('g', 'kg')"

/**
 * 가격. 무게 단위면 API 가 준 kg 환산가, 개수 단위면 조사가.
 * 환산가는 무게 단위일 때만 kg당이다(100g ×10, 500g ×2, 2kg ×0.5). 2026-03~09 저장분 103,817행과
 * 2024-05 무작위 한 주를 대조해 예외가 없었다. 개수 단위는 환산가가 조사가를 그대로 복사한 값이지만,
 * 그 동작에 기대지 않고 조사가를 직접 쓴다.
 */
private const val PRICE = "case when $IS_WEIGHT then d.exmn_dd_cnvs_prc else d.exmn_dd_prc end"

/** 가격의 기준 단위. 무게 단위는 1 kg 으로 모은다 — 같은 품종이 1kg·2kg 으로 섞여 조사돼도 한 묶음이 된다. */
private const val PRICE_UNIT = "case when $IS_WEIGHT then 'kg' else d.unit end"
private const val PRICE_UNIT_SIZE = "case when $IS_WEIGHT then 1 else d.unit_sz::int end"

/**
 * 1. `per_store`: 소매 품종 × 등급 × 단위 × 점포마다 7일 값의 중앙값. 점포 하나당 값 하나가 된다.
 * 2. `retail_price`: 그 점포 값들의 중앙값. 점포마다 한 표다.
 * 3. 소매 품종 기준으로 낸 값을 매핑으로 정산 품종에 붙인다. 정산 품종 여러 개가 같은 소매 품종을 가리키면 같은 값이 붙는다.
 *
 * 7일은 소매 데이터 전체의 마지막 조사일부터 센다. 그 7일에 조사가 없는 품종(철이 지나 조사가 끊긴 품종)은 빠진다.
 * 별칭은 원래 컬럼과 이름이 겹치지 않게 짓는다 — `unit` 으로 지으면 GROUP BY 가 원래 컬럼 d.unit 으로 묶는다.
 */
private const val SQL = """
    with base as (
        select max(exmn_ymd) as end_date from retail_daily
    ),
    per_store as (
        select d.ctgry_cd, d.item_cd, d.vrty_cd, d.grd_cd, d.grd_nm,
               $PRICE_UNIT as price_unit, $PRICE_UNIT_SIZE as price_unit_size, d.mrkt_cd,
               percentile_cont(0.5) within group (order by $PRICE) as store_price
          from retail_daily d
         cross join base
         where d.exmn_ymd > base.end_date - $DAYS and d.exmn_ymd <= base.end_date
         group by d.ctgry_cd, d.item_cd, d.vrty_cd, d.grd_cd, d.grd_nm, price_unit, price_unit_size, d.mrkt_cd
    ),
    retail_price as (
        select ctgry_cd, item_cd, vrty_cd, grd_cd, grd_nm, price_unit, price_unit_size,
               round(percentile_cont(0.5) within group (order by store_price))::bigint as price,
               count(*) as store_count
          from per_store
         group by ctgry_cd, item_cd, vrty_cd, grd_cd, grd_nm, price_unit, price_unit_size
    )
    select m.variety_id, rv.item_nm, rv.vrty_nm,
           base.end_date - ($DAYS - 1) as from_date, base.end_date as to_date,
           p.grd_cd, p.grd_nm, p.price_unit, p.price_unit_size, p.price, p.store_count
      from variety_retail_mapping m
      join retail_variety rv on rv.id = m.retail_variety_id
      join retail_price p on (p.ctgry_cd, p.item_cd, p.vrty_cd) = (rv.ctgry_cd, rv.item_cd, rv.vrty_cd)
     cross join base
     order by m.variety_id, p.price_unit, p.price_unit_size, p.grd_cd
"""
