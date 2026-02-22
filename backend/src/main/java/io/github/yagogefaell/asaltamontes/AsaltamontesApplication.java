package io.github.yagogefaell.asaltamontes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AsaltamontesApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsaltamontesApplication.class, args);
    }

}
