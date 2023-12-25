package com.tower.templateservice.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate

@RestController
class DemoController {
    @GetMapping("/test1")
    fun test1(): String  {
        val url = "http://appserver:8082/test1"
        val restTemplate = RestTemplate()
        val responseEntity: ResponseEntity<String> = restTemplate.getForEntity(url, String::class.java)
        val resp: String? = responseEntity.body
        println("test1 resp: $resp")
        return "call test1$resp"
    }

    @GetMapping("/test2")
    fun test2(): String  {
        val url = "http://appserver:8082/test2"
        val restTemplate = RestTemplate()
        val responseEntity: ResponseEntity<String> = restTemplate.getForEntity(url, String::class.java)
        val resp: String? = responseEntity.body
        println("test2 resp: $resp")
        return "call test2" + resp
    }

    @GetMapping("/test3")
    fun test3(): String  {
        val url = "http://appserver:8082/test3"
        val restTemplate = RestTemplate()
        val responseEntity: ResponseEntity<String> = restTemplate.getForEntity(url, String::class.java)
        val resp: String? = responseEntity.body
        println("test3 resp: $resp")
        return "call test3"
    }

}

