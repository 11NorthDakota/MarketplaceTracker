package by.northdakota.markettracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class MarketTrackerApplication {

    public static void main(String[] args) throws IOException {
        SpringApplication.run(MarketTrackerApplication.class, args);
    }

}
