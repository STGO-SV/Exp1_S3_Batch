package com.duoc.banco_legacy.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.duoc.banco_legacy")
public class WebBffApplication {
    public static void main(String[] args) {

        SpringApplication.run(WebBffApplication.class, args);
    }
}