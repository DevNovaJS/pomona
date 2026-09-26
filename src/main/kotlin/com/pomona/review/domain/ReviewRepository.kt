package com.pomona.review.domain

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewRepository : JpaRepository<Review, Long> {

    /** 최근에 먹은 순. 품종을 같이 읽어 응답에 품종 이름을 붙일 때 리뷰마다 따로 조회하지 않게 한다. */
    @EntityGraph(attributePaths = ["variety"])
    fun findAllByOrderByEatenDateDescIdDesc(): List<Review>
}
