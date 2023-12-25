package com.tower.templateservice.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DemoController {
    @GetMapping("/test1")
    fun test1(): String  {
        return "data test1"
    }

    @GetMapping("/test2")
    fun test2(): String  {
        throw RuntimeException("data test2")
    }

    @GetMapping("/test3")
    fun test3(): String  {
        throw RuntimeException("data test3")
    }
}

