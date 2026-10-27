package io.github.yagogefaell.asaltamontes.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async execution for notifications (T18). The pool and its queue are bounded and, when they are full, the work is
 * dropped with a log line: the request thread is never blocked and memory never grows without limit.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String NOTIFICATIONS_EXECUTOR = "notificationExecutor";

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(NOTIFICATIONS_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notifications-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setRejectedExecutionHandler(dropAndLog());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    private static RejectedExecutionHandler dropAndLog() {
        return (Runnable task, ThreadPoolExecutor pool) ->
                log.warn("Notification queue full ({} queued): dropping a notification task", pool.getQueue().size());
    }
}
