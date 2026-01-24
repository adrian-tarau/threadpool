package net.microfalx.threadpool;

import net.microfalx.lang.ExceptionUtils;
import org.quartz.CronExpression;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;

/**
 * A trigger using CRON expressions.
 */
public class CronTrigger extends AbstractTrigger {

    private final String expression;
    private final CronExpression cronExpression;

    public CronTrigger(String expression) {
        requireNotEmpty(expression);
        this.expression = expression;
        cronExpression = parse(expression);
    }

    /**
     * Returns the CRON expression.
     *
     * @return a non-null instance
     */
    public String getExpression() {
        return expression;
    }

    @Override
    public Instant nextExecution() {
        Instant scheduled = getLastScheduledExecution();
        if (scheduled == null) scheduled = Instant.now().minusSeconds(1);
        return cronExpression.getNextValidTimeAfter(Date.from(scheduled)).toInstant();
    }

    /**
     * Returns the duration between executions of a task scheduled with a cron expression.
     *
     * @return the average duration between executions
     */
    public Duration getInterval() {
        Date startDate = cronExpression.getTimeAfter(new Date());
        Date date = new Date(startDate.getTime());
        int iterations = 10;
        for (int i = 1; i <= iterations; i++) {
            date = cronExpression.getTimeAfter(date);
        }
        return Duration.ofMillis((date.getTime() - startDate.getTime()) / iterations);
    }

    private CronExpression parse(String expression) {
        try {
            return new CronExpression(expression);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid cron expression '" + expression + "', root cause: " + ExceptionUtils.getRootCauseMessage(e));
        }
    }
}
