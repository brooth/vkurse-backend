/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.logging;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Scheduled;
import vkurse.core.telemetry.Telemetry;

/**
 * Wraps every {@code @Scheduled} method. While the job runs, {@code job} ({@code StorageChargeJob.chargeToday}) is in
 * the MDC, so each line it logs says which job wrote it. A job that throws is logged once here, at ERROR with its
 * stack trace and how long it ran. A run that ends well is one DEBUG line on the logger {@value #LOGGER}: a job says
 * what it did itself, at INFO, and only when it did something.
 */
public class JobLog extends AbstractPointcutAdvisor {
    public static final String LOGGER = "vkurse.job";

    static final String JOB = "job";

    private static final Logger log = LoggerFactory.getLogger(LOGGER);

    private final Pointcut pointcut = AnnotationMatchingPointcut.forMethodAnnotation(Scheduled.class);
    private final MethodInterceptor advice = this::run;
    private final ObjectProvider<Telemetry> telemetry;

    /** @param telemetry looked up at each run: an advisor is made before the beans it would need */
    public JobLog(ObjectProvider<Telemetry> telemetry) {
        this.telemetry = telemetry;
        // Outside ShedLock's lock, so a run another node took is seen as well.
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return advice;
    }

    private @Nullable Object run(MethodInvocation invocation) throws Throwable {
        var method = invocation.getMethod();
        var job = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        var startedAt = System.nanoTime();
        MDC.put(JOB, job);
        try {
            var result = invocation.proceed();
            log.debug("Job {} finished in {}ms", job, millisSince(startedAt));
            tell(job, startedAt, null);
            return result;
        } catch (Exception e) {
            // Not rethrown: the scheduler would only log it again, without the job's name.
            log.error("Job {} failed after {}ms", job, millisSince(startedAt), e);
            tell(job, startedAt, e);
            return null;
        } finally {
            MDC.remove(JOB);
        }
    }

    private void tell(String job, long startedAt, @Nullable Throwable failure) {
        var current = telemetry.getIfAvailable();
        if (current != null) {
            current.job(job, millisSince(startedAt), failure);
        }
    }

    private static long millisSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
