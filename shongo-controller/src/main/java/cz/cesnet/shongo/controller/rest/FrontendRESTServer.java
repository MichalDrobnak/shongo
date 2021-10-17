package cz.cesnet.shongo.controller.rest;

import cz.cesnet.shongo.controller.Controller;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.net.URL;

public class FrontendRESTServer {
    private static final int PORT = 9999;
    private static final String SERVLET_PATH = "/";
    private static final String SERVLET_NAME = "frontend";

    public void start() {
        Server restServer = new Server(PORT);
        String resourceBase = this.getResourceBase();

        WebAppContext webAppContext = new WebAppContext();
        webAppContext.addServlet(new ServletHolder(SERVLET_NAME, DispatcherServlet.class), SERVLET_PATH);
        webAppContext.setResourceBase(resourceBase);
        webAppContext.setParentLoaderPriority(true);

        restServer.setHandler(webAppContext);

        try {
            restServer.start();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private String getResourceBase() {
        URL resourceBaseUrl = Controller.class.getClassLoader().getResource("WEB-INF");
        if (resourceBaseUrl == null) {
            throw new RuntimeException("WEB-INF is not in classpath.");
        }
        return resourceBaseUrl.toExternalForm().replace("/WEB-INF", "/");
    }
}
