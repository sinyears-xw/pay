package com.jiuzhe.app.pay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import org.springframework.context.annotation.Bean;

//@EnableDiscoveryClient
@EnableTransactionManagement
@SpringBootApplication
public class PayApplication extends SpringBootServletInitializer {
	public static void main(String[] args) {
		SpringApplication.run(PayApplication.class, args);
	}
}
