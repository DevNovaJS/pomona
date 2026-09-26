package com.pomona.variety.service

import com.pomona.variety.domain.RetailVariety
import com.pomona.variety.model.PageVariety
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RetailCandidatesTest {

    private val fuji = RetailVariety("400", "과일류", "411", "사과", "05", "후지")
    private val hongro = RetailVariety("400", "과일류", "411", "사과", "07", "홍로")
    private val tsugaru = RetailVariety("400", "과일류", "411", "사과", "06", "쓰가루(아오리)")
    private val singo = RetailVariety("400", "과일류", "412", "배", "01", "신고")
    private val kiwiDomestic = RetailVariety("400", "과일류", "419", "참다래", "01", "국산")
    private val kiwiGold = RetailVariety("400", "과일류", "437", "키위", "01", "골드")
    private val shine = RetailVariety("400", "과일류", "414", "포도", "12", "샤인머스켓")
    private val all = listOf(fuji, tsugaru, hongro, singo, kiwiDomestic, kiwiGold, shine)

    private fun variety(item: String, name: String?) = PageVariety(1, "06", "과실류", "01", item, "01", name)

    @Test
    fun `같은 품목의 소매 품종만 후보가 되고 이름이 겹치는 것이 앞에 온다`() {
        assertThat(candidatesFor(variety("사과", "로얄후지"), all)).containsExactly(fuji, tsugaru, hongro)
    }

    @Test
    fun `소매 이름에 괄호로 들어 있어도 겹친다고 본다`() {
        assertThat(candidatesFor(variety("사과", "아오리"), all).first()).isEqualTo(tsugaru)
    }

    @Test
    fun `품목 이름에 두 이름이 들어 있으면 둘 다 후보다`() {
        assertThat(candidatesFor(variety("참다래(키위)", "골드키위"), all)).containsExactly(kiwiGold, kiwiDomestic)
    }

    @Test
    fun `표기가 달라 이름이 안 겹쳐도 같은 품목이면 후보에 보인다`() {
        assertThat(candidatesFor(variety("포도", "샤인마스캇"), all)).containsExactly(shine)
    }

    @Test
    fun `품종 이름이 없으면 품목만 보고 원래 순서대로 준다`() {
        assertThat(candidatesFor(variety("사과", null), all)).containsExactly(fuji, tsugaru, hongro)
    }

    @Test
    fun `소매에 없는 품목이면 후보가 없다`() {
        assertThat(candidatesFor(variety("자두", "대석"), all)).isEmpty()
    }
}
