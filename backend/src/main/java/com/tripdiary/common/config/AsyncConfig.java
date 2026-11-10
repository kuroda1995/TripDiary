package com.tripdiary.common.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 非同期処理（{@code @Async}）と定期実行（{@code @Scheduled}）の有効化。
 *
 * <p>非同期処理のログにも呼び出し元の requestId / userId が出るよう、MDC をワーカースレッドへ引き継ぐ。 （Spring Boot が自動構成する
 * applicationTaskExecutor に {@link TaskDecorator} の Bean が適用される。）
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean
    TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        };
    }
}
