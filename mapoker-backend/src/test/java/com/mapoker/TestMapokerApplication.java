package com.mapoker;

import org.springframework.boot.SpringApplication;

public class TestMapokerApplication {

	public static void main(String[] args) {
		SpringApplication.from(MapokerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
