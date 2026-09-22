package com.Firefire.paiban;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.Firefire.paiban.mapper")
public class PaibanApplication {

    private static final Logger log = LoggerFactory.getLogger(PaibanApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PaibanApplication.class, args);
        log.info("==== 排班系统启动成功 ====");
    }
}
