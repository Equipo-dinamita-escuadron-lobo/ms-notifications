package com.unicauca.ms_notifications.infraestructure.output.messageBroker.aspect;

import com.rabbitmq.client.LongString;
import com.unicauca.ms_notifications.infraestructure.output.multitenancy.utils.TenantContext;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JWTContextRabbitMqAspect {
    private final JwtTokenService jwtTokenService;
    private final JwtDecoder jwtDecoder;
    private final String resourceId;
    private final String paymentAudience;
    private final String paymentAzp;

    public JWTContextRabbitMqAspect(JwtTokenService jwtTokenService, JwtDecoder jwtDecoder,
            @Value("${jwt.auth.converter.resource-id:microservices_client}") String resourceId,
            @Value("${notifications.security.payment-audience:ms-notifications}") String paymentAudience,
            @Value("${notifications.security.payment-azp:treasury-service}") String paymentAzp) {
        this.jwtTokenService = jwtTokenService;
        this.jwtDecoder = jwtDecoder;
        this.resourceId = resourceId;
        this.paymentAudience = paymentAudience;
        this.paymentAzp = paymentAzp;
    }

    @Around("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public Object authenticateMessage(ProceedingJoinPoint joinPoint) throws Throwable {
        Message message = findMessage(joinPoint.getArgs());
        if (message == null) throw new SecurityException("El listener Rabbit debe recibir Message");
        String encoded = header(message, "x-jwt-token");
        if (encoded == null) throw new SecurityException("Mensaje Rabbit sin JWT");
        String token = encoded.startsWith("Bearer ") ? encoded.substring(7) : encoded;
        Jwt jwt = jwtDecoder.decode(token);
        String eventType = header(message, "eventType");
        boolean serviceAccount = serviceAccount(jwt);
        String tenantId = firstHeader(message, "x-tenant-id", "tenantId");
        if (tenantId == null && !serviceAccount) tenantId = jwt.getSubject();
        if (tenantId == null) throw new SecurityException("Mensaje de servicio sin tenant");
        if ("PAYMENT_VOUCHER_POSTED".equals(eventType)) {
            if (!paymentAzp.equals(jwt.getClaimAsString("azp"))) throw new SecurityException("azp no autorizado para notificacion de pago");
            if (jwt.getAudience() == null || !jwt.getAudience().contains(paymentAudience)) throw new SecurityException("Audience invalida para notificaciones");
            Collection<String> tenantIds=stringCollection(jwt.getClaim("tenant_ids"));
            if(tenantIds.contains("*"))throw new SecurityException("tenant_ids no admite comodines");
            if(!tenantIds.contains(tenantId))throw new SecurityException("Tenant no autorizado por tenant_ids");
        }
        if (!serviceAccount && !tenantId.equals(jwt.getSubject())) {
            throw new SecurityException("Tenant Rabbit diferente del JWT");
        }
        if (!serviceAccount && !operationalRole(jwt)) {
            throw new SecurityException("JWT Rabbit sin rol autorizado");
        }
        jwtTokenService.setRabbitJwtToken(encoded.startsWith("Bearer ") ? encoded : "Bearer " + encoded);
        jwtTokenService.setRabbitTenantId(tenantId);
        TenantContext.setTenantId(tenantId);
        try {
            return joinPoint.proceed();
        } finally {
            TenantContext.clear();
            jwtTokenService.clearRabbitContext();
        }
    }

    private boolean serviceAccount(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username != null && username.startsWith("service-account-");
    }

    private Collection<String> stringCollection(Object claim){if(claim instanceof Collection<?> values)return values.stream().map(String::valueOf).toList();if(claim instanceof String value&&!value.isBlank())return List.of(value);return List.of();}

    @SuppressWarnings("unchecked")
    private boolean operationalRole(Jwt jwt) {
        Map<String, Object> access = jwt.getClaim("resource_access");
        if (access == null || !(access.get(resourceId) instanceof Map<?, ?> client)) return false;
        if (!(client.get("roles") instanceof Collection<?> roles)) return false;
        return roles.stream().map(String::valueOf).anyMatch(role ->
                role.equals("Estudiante") || role.equals("Profesor") || role.equals("Administrador"));
    }

    private Message findMessage(Object[] args) {
        for (Object arg : args) if (arg instanceof Message message) return message;
        return null;
    }

    private String firstHeader(Message message, String... names) {
        for (String name : names) {
            String value = header(message, name);
            if (value != null) return value;
        }
        return null;
    }

    private String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        if (value instanceof LongString longString) return longString.toString();
        if (value instanceof String text && !text.isBlank() && !text.equals("null")) return text;
        return null;
    }
}
