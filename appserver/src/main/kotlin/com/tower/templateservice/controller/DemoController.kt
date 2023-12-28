package com.tower.templateservice.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate

@RestController
class DemoController {
    @GetMapping("/test1")
    fun test1(): String  {
        val url = "http://dataserver:8083/test1"
        val restTemplate = RestTemplate()
        val responseEntity: ResponseEntity<String> = restTemplate.getForEntity(url, String::class.java)
        val resp: String? = responseEntity.body
        println("test1 resp: $resp")
        return "appserver test1$resp"
    }

    @GetMapping("/test2")
    fun test2(): String  {
        throw RuntimeException("appserver test2")
    }


    @GetMapping("/test3")
    fun test3(): String  {
        val url = "http://dataserver:8083/test3"
        val restTemplate = RestTemplate()
        val responseEntity: ResponseEntity<String> = restTemplate.getForEntity(url, String::class.java)
        val resp: String? = responseEntity.body
        println("test3 resp: $resp")
        return "appserver test3$resp"
    }
}

