package com.unicauca.ms_notifications.infraestructure.output.multitenancy.interceptor;

import com.unicauca.ms_notifications.infraestructure.output.messageBroker.aspect.JwtTokenService;
import com.unicauca.ms_notifications.infraestructure.output.multitenancy.utils.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.WebRequestInterceptor;

@Component
@RequiredArgsConstructor
public class TenantInterceptor implements WebRequestInterceptor {
    private final JwtTokenService jwtTokenService;

    @Override
    public void preHandle(WebRequest request) {
        String tenantId = jwtTokenService.getTenantId();
        if (tenantId == null || tenantId.isBlank()) throw new SecurityException("JWT sin tenant");
        TenantContext.setTenantId(tenantId);
    }

    @Override
    public void postHandle(WebRequest request, ModelMap model) {
        // The transaction may still be completing.
    }

    @Override
    public void afterCompletion(WebRequest request, Exception ex) {
        TenantContext.clear();
    }
}
