package com.pomona

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PomonaApplication

fun main(args: Array<String>) {
    runApplication<PomonaApplication>(*args)
}
