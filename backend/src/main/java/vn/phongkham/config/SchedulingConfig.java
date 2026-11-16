package vn.phongkham.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật @Scheduled cho các tác vụ nền (đánh dấu không đến khám...) */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
