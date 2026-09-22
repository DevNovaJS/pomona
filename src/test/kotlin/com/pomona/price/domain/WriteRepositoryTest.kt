package com.pomona.price.domain

import com.pomona.price.model.RetailDailyRow
import com.pomona.price.model.WholesaleDailyRow
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
 * 쓰기 경로 검증. 확정 지연 때문에 매일 D-1~D-5 를 다시 수집한다.
 * 도매·소매는 **범위를 지우고 다시 넣는다** — 재수집 결과에서 사라진 키도 정리돼야 하기 때문이다.
 * 품종 마스터는 도매 행이 FK 로 참조하므로 지우지 않고 upsert 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class, RetailDailyWriteRepository::class)
class WriteRepositoryTest {

    @Autowired private lateinit var varieties: VarietyUpsertRepository
    @Autowired private lateinit var wholesale: WholesaleDailyWriteRepository
    @Autowired private lateinit var retail: RetailDailyWriteRepository
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val day = LocalDate.of(2099, 1, 4)
    private val garak = "110001"

    private fun variety(sclsfNm: String? = "시험홍로") = VarietyUpsert(
        lclsfCd = "ZZ", lclsfNm = "시험대분류",
        mclsfCd = "ZZ", mclsfNm = "시험중분류",
        sclsfCd = "01", sclsfNm = sclsfNm,
    )

    private fun wholesaleRow(
        varietyId: Long, grdCd: String = "11", totPrc: Long = 479_000, totQty: String = "410.000",
        count: Int = 3, date: LocalDate = day,
    ): WholesaleDailyRow {
        val grdNm = if (grdCd == "11") {
            "특"
        } else {
            "상"
        }
        return WholesaleDailyRow(
            trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
            trdSe = "경매", grdCd = grdCd, grdNm = grdNm,
            plorCd = "367000", plorNm = "충청북도 괴산군", unitNm = "kg",
            totPrc = totPrc, totQty = BigDecimal(totQty),
            lowPrcPerKg = BigDecimal("1800.00"), highPrcPerKg = BigDecimal("3800.00"),
            tradeCount = count,
        )
    }

    private fun retailRow(prc: Long, itemCd: String = "411", mrktCd: String = "9999999", date: LocalDate = day) =
        RetailDailyRow(
            exmnYmd = date, seCd = "01", seNm = "소매",
            ctgryCd = "400", ctgryNm = "과일류", itemCd = itemCd, itemNm = "사과",
            vrtyCd = "07", vrtyNm = "홍로", grdCd = "04", grdNm = "상품",
            sggCd = "9999", sggNm = "시험시", mrktCd = mrktCd, mrktNm = "시험점포",
            unit = "개", unitSz = "10", exmnDdPrc = prc, exmnDdCnvsPrc = prc, orgnlRegDt = null,
        )

    private fun wholesaleRows(on: LocalDate = day) =
        jdbc.queryForList("select * from wholesale_daily where trd_clcln_ymd = ?", on)

    private fun retailRows(itemCd: String = "411") =
        jdbc.queryForList("select * from retail_daily where exmn_ymd = ? and item_cd = ?", day, itemCd)

    // ── 품종: upsert 유지 ───────────────────────────────────────────

    @Test
    fun `품종을 두 번 넣어도 같은 id 가 돌아오고 행이 늘지 않는다`() {
        val before = jdbc.queryForObject("select count(*) from variety_master", Int::class.java)!!

        val first = varieties.upsert(variety())
        val second = varieties.upsert(variety(sclsfNm = "이름바뀜"))

        assertThat(second).isEqualTo(first)
        assertThat(jdbc.queryForObject("select count(*) from variety_master", Int::class.java)).isEqualTo(before + 1)
        assertThat(jdbc.queryForObject("select sclsf_nm from variety_master where id = ?", String::class.java, first))
            .isEqualTo("이름바뀜")
    }

    @Test
    fun `품종명이 null 이어도 id 를 돌려준다`() {
        assertThat(varieties.upsert(variety(sclsfNm = null))).isNotNull()
    }

    // ── 도매: 날짜 단위로 지우고 다시 넣기 ─────────────────────────────

    @Test
    fun `같은 날짜를 다시 넣으면 새 값으로 바뀌고 행은 늘지 않는다`() {
        val varietyId = varieties.upsert(variety())
        wholesale.replaceDay(day, garak, listOf(wholesaleRow(varietyId, totPrc = 479_000, totQty = "410.000", count = 3)))

        // D-1 에 16% 가 아직 안 들어와 있다가 며칠 뒤 채워지는 상황
        wholesale.replaceDay(day, garak, listOf(wholesaleRow(varietyId, totPrc = 913_000, totQty = "810.500", count = 7)))

        val rows = wholesaleRows()
        assertThat(rows).hasSize(1)
        assertThat(rows.first()["tot_prc"]).isEqualTo(913_000L)
        assertThat(rows.first()["tot_qty"]).isEqualTo(BigDecimal("810.500"))   // bigint 였으면 810 으로 잘린다
        assertThat(rows.first()["trade_count"]).isEqualTo(7)
    }

    @Test
    fun `재수집 결과에서 사라진 키는 지워진다`() {
        // 등급이 '상'으로 잘못 올라왔다가 다음 날 '특'으로 정정된 경우.
        // upsert 였다면 '상' 행이 남아 그날 물량이 두 번 잡힌다.
        val varietyId = varieties.upsert(variety())
        wholesale.replaceDay(day, garak, listOf(wholesaleRow(varietyId, grdCd = "12")))

        wholesale.replaceDay(day, garak, listOf(wholesaleRow(varietyId, grdCd = "11")))

        assertThat(wholesaleRows().map { it["grd_cd"] }).containsExactly("11")
    }

    @Test
    fun `다른 날짜는 건드리지 않는다`() {
        val varietyId = varieties.upsert(variety())
        val nextDay = day.plusDays(1)
        wholesale.replaceDay(day, garak, listOf(wholesaleRow(varietyId)))
        wholesale.replaceDay(nextDay, garak, listOf(wholesaleRow(varietyId, date = nextDay)))

        wholesale.replaceDay(nextDay, garak, listOf(wholesaleRow(varietyId, date = nextDay, totPrc = 1)))

        assertThat(wholesaleRows(day)).hasSize(1)
        assertThat(wholesaleRows(day).first()["tot_prc"]).isEqualTo(479_000L)
    }

    @Test
    fun `여러 행을 한 번에 넣는다`() {
        val varietyId = varieties.upsert(variety())
        val rows = listOf("367000", "568000", "597000").map { wholesaleRow(varietyId).copy(plorCd = it) }

        assertThat(wholesale.replaceDay(day, garak, rows)).isEqualTo(3)
        assertThat(wholesaleRows()).hasSize(3)
    }

    @Test
    fun `빈 목록이면 그 날짜가 비워진다`() {
        // API 가 0행을 줬다는 건 그날 데이터가 없다는 뜻이다. 남아 있던 행도 치운다.
        val varietyId = varieties.upsert(variety())
        wholesale.replaceDay(day, garak, listOf(wholesaleRow(varietyId)))

        assertThat(wholesale.replaceDay(day, garak, emptyList())).isZero()
        assertThat(wholesaleRows()).isEmpty()
    }

    // ── 소매: 품목 x 기간 단위로 지우고 다시 넣기 ───────────────────────

    @Test
    fun `소매도 같은 범위를 다시 넣으면 새 값으로 바뀐다`() {
        retail.replaceRange("01", "400", "411", day, day, listOf(retailRow(21_800)))
        retail.replaceRange("01", "400", "411", day, day, listOf(retailRow(23_000)))

        assertThat(retailRows()).hasSize(1)
        assertThat(retailRows().first()["exmn_dd_prc"]).isEqualTo(23_000L)
    }

    @Test
    fun `소매 재수집에서 사라진 점포는 지워진다`() {
        retail.replaceRange("01", "400", "411", day, day, listOf(retailRow(21_800, mrktCd = "9999991"), retailRow(22_000, mrktCd = "9999992")))

        retail.replaceRange("01", "400", "411", day, day, listOf(retailRow(21_800, mrktCd = "9999991")))

        assertThat(retailRows().map { it["mrkt_cd"] }).containsExactly("9999991")
    }

    @Test
    fun `소매는 다른 품목을 건드리지 않는다`() {
        retail.replaceRange("01", "400", "411", day, day, listOf(retailRow(21_800, itemCd = "411")))
        retail.replaceRange("01", "400", "412", day, day, listOf(retailRow(30_000, itemCd = "412")))

        retail.replaceRange("01", "400", "412", day, day, emptyList())

        assertThat(retailRows("411")).hasSize(1)
        assertThat(retailRows("412")).isEmpty()
    }

}
