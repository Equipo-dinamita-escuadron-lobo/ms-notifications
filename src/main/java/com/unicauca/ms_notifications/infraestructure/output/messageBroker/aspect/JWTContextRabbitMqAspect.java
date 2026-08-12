package com.unicauca.ms_notifications.infraestructure.output.messageBroker.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.LongString;
import com.unicauca.ms_notifications.infraestructure.output.multitenancy.utils.TenantContext;
import com.unicauca.ms_notifications.infraestructure.output.security.JwtDecoder;


/**
 * @brief Aspect for setting tenant context from RabbitMQ message headers
 * 
 * This advice intercepts methods annotated with @RabbitListener to extract
 * a JWT token from message headers, decode the tenantId, set it in the
 * TenantContext, execute the listener method, and finally clear the context.
 */
@Aspect
@Component
public class JWTContextRabbitMqAspect {

    private static final Logger logger = LoggerFactory.getLogger(JWTContextRabbitMqAspect.class);
    private static final String JWT_TOKEN_HEADER = "x-jwt-token";

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    /**
     * @brief Sets tenant context around methods annotated with @RabbitListener
     * @param joinPoint The proceeding join point
     * @return The result of the listener method execution
     * @throws Throwable If an error occurs during processing
     */
    @Around("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public Object setTenantContext(ProceedingJoinPoint joinPoint) throws Throwable {
        
        logger.info("============== [ASPECT-START] Interceptando listener: {} ==============", joinPoint.getSignature().getName());

        // Find the 'Message' object in the listener method's arguments
        Message message = findMessageArgument(joinPoint.getArgs());

        if (message == null) {
            logger.warn("Listener [{}] does not receive a 'Message' object. Cannot set tenant context.", joinPoint.getSignature().getName());
            return joinPoint.proceed(); // Execute without context
        }

        Object tokenObject = message.getMessageProperties().getHeaders().get(JWT_TOKEN_HEADER);

        // ===== LOG DE DIAGNÓSTICO 2: ¿Estamos recibiendo el header? =====
        logger.info("[ASPECT-LOG] Header '{}' encontrado con valor: {}", JWT_TOKEN_HEADER, tokenObject);

        String jwtToken = extractTokenFromObject(tokenObject);

        if (jwtToken == null || jwtToken.isEmpty()) {
            logger.error("Message received without the '{}' header. Processing without tenant context.", JWT_TOKEN_HEADER);
            return joinPoint.proceed(); // Execute without context
        }

        try {
            // 1. Decode the token to extract the tenantId
            String tenantId = jwtDecoder.extractTenantId(jwtToken);

            // ===== LOG DE DIAGNÓSTICO 3: ¿Se extrajo correctamente el tenantId? =====
            logger.info("[ASPECT-LOG] Tenant ID extraído del token: '{}'", tenantId);
            
            if (tenantId == null || tenantId.isEmpty()) {
                logger.error("Could not extract tenant ID from JWT token. Processing without tenant context.");
                return joinPoint.proceed();
            }

            // 2. Set the RabbitMQ context in the unified service
            jwtTokenService.setRabbitJwtToken(jwtToken);
            jwtTokenService.setRabbitTenantId(tenantId);

            // 3. Set the Tenant context for this thread
            TenantContext.setTenantId(tenantId);
            logger.info("Tenant context '{}' set for listener [{}].", tenantId, joinPoint.getSignature().getName());

            // 4. Execute the original listener method
            return joinPoint.proceed();

        } finally {
            logger.info("Clearing tenant context.");
            TenantContext.clear();
            jwtTokenService.clearRabbitContext();
        }
    }

    /**
     * @brief Utility method to find the Message argument
     * @param args The arguments of the listener method
     * @return The Message object, or null if not found
     */
    private Message findMessageArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Message) {
                return (Message) arg;
            }
        }
        return null;
    }

    /**
     * @brief Extracts JWT token from header object (String or LongString)
     * @param tokenObject The header object
     * @return The JWT token as a String, or null if not found or invalid type
     */
    private String extractTokenFromObject(Object tokenObject) {
        if (tokenObject instanceof LongString) {
            return tokenObject.toString();
        } else if (tokenObject instanceof String) {
            return (String) tokenObject;
        }
        return null;
    }
}
