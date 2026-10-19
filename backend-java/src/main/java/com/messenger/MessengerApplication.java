package com.messenger;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

// Точка входа приложения. Порт и настройки БД — в application.properties.
@SpringBootApplication
public class MessengerApplication {
    // Запускает Spring Boot.
    public static void main(String[] args) {
        SpringApplication.run(MessengerApplication.class, args);
    }

    // При старте сбрасывает онлайн-статус всем пользователям (после аварийной остановки могли остаться "онлайн").
    @Bean
    public CommandLineRunner resetOnlineStatusOnStartup(JdbcTemplate jdbc) {
        return args -> jdbc.update("UPDATE users SET is_online = false");
    }
}
