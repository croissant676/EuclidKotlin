package org.euclid.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EuclidKotlinApplication

fun main(args: Array<String>) {
    runApplication<EuclidKotlinApplication>(*args)
}
