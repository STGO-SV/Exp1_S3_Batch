package com.duoc.banco_legacy.atm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.duoc.banco_legacy")
public class AtmBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(AtmBffApplication.class, args);
    }
}
