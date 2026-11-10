package com.tripdiary.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 現在時刻は Clock を注入して取得する（テストで時刻を固定できるようにするため。docs/rules.md 2.2）。 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
