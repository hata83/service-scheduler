package dev.hata.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ServiceSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceSchedulerApplication.class, args);
    }
}
