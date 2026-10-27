package dev.hata.scheduler.common.ratelimit;

import java.lang.reflect.Method;

import dev.hata.scheduler.common.filter.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP aspect that evaluates {@link RateLimited} rules before controller execution,
 * resolving either the caller IP or a SpEL payload expression as the rate-limit key.
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer DISCOVERER = new DefaultParameterNameDiscoverer();

    private final RateLimitService rateLimitService;
    private final ClientIpResolver ipResolver;

    public RateLimitAspect(RateLimitService rateLimitService, ClientIpResolver ipResolver) {
        this.rateLimitService = rateLimitService;
        this.ipResolver = ipResolver;
    }

    @Before("@annotation(rateLimited)")
    public void enforceRateLimits(JoinPoint joinPoint, RateLimited rateLimited) {
        HttpServletRequest request = currentRequest(joinPoint.getArgs());
        String clientIp = request != null ? ipResolver.resolve(request) : "unknown";
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        for (RateLimited.Rule rule : rateLimited.value()) {
            String key = resolveKey(rule.keyExpression(), method, joinPoint.getArgs(), clientIp);
            if (key != null && !key.isBlank()) {
                rateLimitService.checkLimit(rule.policy(), key, rule.message());
            }
        }
    }

    private String resolveKey(String expression, Method method, Object[] args, String clientIp) {
        if (expression == null || expression.isBlank()) {
            return clientIp;
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(null, method, args, DISCOVERER);
        Object value = PARSER.parseExpression(expression).getValue(context);
        return value != null ? value.toString() : null;
    }

    private HttpServletRequest currentRequest(Object... args) {
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest req) {
                return req;
            }
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
