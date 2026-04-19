package com.mapoker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MapokerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MapokerApplication.class, args);
	}

}
