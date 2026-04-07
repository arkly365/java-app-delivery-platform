package com.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HelloController {

    @Value("${app.environment:unknown}")
    private String environment;

    @Value("${app.branch:unknown}")
    private String branch;

    @Value("${app.imageTag:unknown}")
    private String imageTag;

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Hello DevOps");
        result.put("environment", environment);
        result.put("branch", branch);
        result.put("imageTag", imageTag);
        return result;
    }

    @GetMapping("/version")
    public Map<String, Object> version() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("environment", environment);
        result.put("branch", branch);
        result.put("imageTag", imageTag);
        return result;
    }
}