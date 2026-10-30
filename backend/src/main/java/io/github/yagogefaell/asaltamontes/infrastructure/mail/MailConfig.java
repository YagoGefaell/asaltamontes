package io.github.yagogefaell.asaltamontes.infrastructure.mail;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Chooses the {@link MailSender}. Built by hand from {@link MailProperties} (and not with spring.mail.*) so that an empty
 * MAIL_HOST is unambiguous: log only.
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler((Runnable task, ThreadPoolExecutor pool) ->
                log.warn("Mail queue full ({} queued): dropping an email", pool.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Bean
    public MailSender mailSender(MailProperties properties) {
        if (!properties.isConfigured()) {
            log.warn("MAIL_HOST is empty: emails will be written to the log instead of being sent");
            return new LoggingMailSender(properties.isLogBody());
        }
        boolean auth = !properties.getUsername().isBlank();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setDefaultEncoding("UTF-8");
        if (auth) {
            sender.setUsername(properties.getUsername());
            sender.setPassword(properties.getPassword());
        }
        Properties smtp = sender.getJavaMailProperties();
        smtp.put("mail.smtp.auth", String.valueOf(auth));
        smtp.put("mail.smtp.starttls.enable", String.valueOf(properties.isStarttls()));
        smtp.put("mail.smtp.starttls.required", String.valueOf(properties.isStarttls()));
        smtp.put("mail.smtp.ssl.enable", String.valueOf(properties.isSsl()));
        smtp.put("mail.smtp.connectiontimeout", "5000");
        smtp.put("mail.smtp.timeout", "10000");
        smtp.put("mail.smtp.writetimeout", "10000");
        return new SmtpMailSender(sender, properties.getFrom());
    }
}
