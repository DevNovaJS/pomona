package com.pomona.price.domain

import com.pomona.price.model.RetailDailyUpsert
import com.pomona.price.model.WholesaleDailyUpsert
import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.VarietyUpsert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 쓰기 경로 검증. 확정 지연 때문에 **매일 D-1~D-5 를 다시 수집**하므로
 * 같은 자연키가 반복해 들어와도 행이 늘지 않고 값만 정정돼야 한다. 설계 전체가 여기 걸려 있다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(VarietyUpsertRepository::class, WholesaleDailyUpsertRepository::class, RetailDailyUpsertRepository::class)
class UpsertRepositoryTest {

    @Autowired private lateinit var varieties: VarietyUpsertRepository
    @Autowired private lateinit var wholesale: WholesaleDailyUpsertRepository
    @Autowired private lateinit var retail: RetailDailyUpsertRepository
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val 날짜 = LocalDate.of(2099, 1, 4)

    private fun 품종(sclsfNm: String? = "시험홍로") = VarietyUpsert(
        lclsfCd = "ZZ", lclsfNm = "시험대분류",
        mclsfCd = "ZZ", mclsfNm = "시험중분류",
        sclsfCd = "01", sclsfNm = sclsfNm,
    )

    private fun 도매(varietyId: Long, totPrc: Long, totQty: String, count: Int) = WholesaleDailyUpsert(
        trdClclnYmd = 날짜, whslMrktCd = "110001", varietyId = varietyId,
        trdSe = "경매", grdCd = "11", grdNm = "특",
        plorCd = "367000", plorNm = "충청북도 괴산군", unitNm = "kg",
        totPrc = totPrc, totQty = BigDecimal(totQty),
        lowPrcPerKg = BigDecimal("1800.00"), highPrcPerKg = BigDecimal("3800.00"),
        tradeCount = count,
    )

    private fun 소매(prc: Long) = RetailDailyUpsert(
        exmnYmd = 날짜, seCd = "01", seNm = "소매",
        ctgryCd = "400", ctgryNm = "과일류", itemCd = "411", itemNm = "사과",
        vrtyCd = "07", vrtyNm = "홍로", grdCd = "04", grdNm = "상품",
        sggCd = "9999", sggNm = "시험시", mrktCd = "9999999", mrktNm = "시험점포",
        unit = "개", unitSz = "10", exmnDdPrc = prc, exmnDdCnvsPrc = prc, orgnlRegDt = null,
    )

    private fun 도매행수() = jdbc.queryForObject(
        "select count(*) from wholesale_daily where trd_clcln_ymd = ?", Int::class.java, 날짜)

    @Test
    fun `품종을 두 번 넣어도 같은 id 가 돌아오고 행이 늘지 않는다`() {
        val before = jdbc.queryForObject("select count(*) from variety_master", Int::class.java)!!

        val first = varieties.upsert(품종())
        val second = varieties.upsert(품종(sclsfNm = "이름바뀜"))

        assertThat(second).isEqualTo(first)
        assertThat(jdbc.queryForObject("select count(*) from variety_master", Int::class.java)).isEqualTo(before + 1)
        assertThat(jdbc.queryForObject("select sclsf_nm from variety_master where id = ?", String::class.java, first))
            .isEqualTo("이름바뀜")
    }

    @Test
    fun `품종명이 null 이어도 id 를 돌려준다`() {
        assertThat(varieties.upsert(품종(sclsfNm = null))).isNotNull()
    }

    @Test
    fun `재수집하면 도매 집계가 정정되고 행은 그대로다`() {
        val varietyId = varieties.upsert(품종())
        wholesale.upsertAll(listOf(도매(varietyId, 479_000, "410.000", 3)))
        assertThat(도매행수()).isEqualTo(1)

        // D-1 에 16% 가 아직 안 들어와 있다가 며칠 뒤 채워지는 상황
        wholesale.upsertAll(listOf(도매(varietyId, 913_000, "810.500", 7)))

        assertThat(도매행수()).isEqualTo(1)
        val row = jdbc.queryForMap("select * from wholesale_daily where trd_clcln_ymd = ?", 날짜)
        assertThat(row["tot_prc"]).isEqualTo(913_000L)
        assertThat(row["tot_qty"]).isEqualTo(BigDecimal("810.500"))   // bigint 였으면 810 으로 잘린다
        assertThat(row["trade_count"]).isEqualTo(7)
    }

    @Test
    fun `여러 행을 한 번에 넣는다`() {
        val varietyId = varieties.upsert(품종())
        val rows = listOf("367000", "568000", "597000").map {
            도매(varietyId, 100_000, "50.000", 1).copy(plorCd = it)
        }

        assertThat(wholesale.upsertAll(rows)).isEqualTo(3)
        assertThat(도매행수()).isEqualTo(3)
    }

    @Test
    fun `빈 목록은 호출하지 않는다`() {
        assertThat(wholesale.upsertAll(emptyList())).isZero()
        assertThat(retail.upsertAll(emptyList())).isZero()
    }

    @Test
    fun `소매도 같은 자연키면 값만 바뀐다`() {
        retail.upsertAll(listOf(소매(21_800)))
        retail.upsertAll(listOf(소매(23_000)))

        val rows = jdbc.queryForList("select exmn_dd_prc from retail_daily where exmn_ymd = ?", 날짜)
        assertThat(rows).hasSize(1)
        assertThat(rows.first()["exmn_dd_prc"]).isEqualTo(23_000L)
    }
}
