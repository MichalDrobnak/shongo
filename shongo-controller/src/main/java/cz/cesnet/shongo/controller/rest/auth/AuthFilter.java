package cz.cesnet.shongo.controller.rest.auth;

import cz.cesnet.shongo.controller.ControllerReportSet;
import cz.cesnet.shongo.controller.api.SecurityToken;
import cz.cesnet.shongo.controller.authorization.Authorization;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class AuthFilter extends GenericFilterBean {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ControllerReportSet.SecurityInvalidTokenException, ServletException, IOException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        Authorization authorization = Authorization.getInstance();

        String accessToken = httpRequest.getHeader("Authorization");
        String sanitizedToken = accessToken.split("Bearer")[1].strip();
        SecurityToken securityToken = new SecurityToken(sanitizedToken);

        authorization.validate(securityToken);
        chain.doFilter(request, response);
    }
}