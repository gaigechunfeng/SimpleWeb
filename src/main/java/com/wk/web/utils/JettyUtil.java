package com.wk.web.utils;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.webapp.WebAppContext;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.concurrent.TimeUnit;

public class JettyUtil {
    public static void runJetty(String contextPath, int port, String homePath) {
        runJetty(contextPath, port, homePath, null, null);
    }

    public static Server createJetty(String contextPath, int port, String homePath) {

        return createJetty(contextPath, port, homePath, null, null);
    }

    /**
     * 启动jetty服务器
     */
    public static void runJetty(String contextPath, int port, String homePath, ArrayList<? extends EventListener> listeners, ServletHandler servletHandler) {

        Server server = createJetty(contextPath, port, homePath, listeners, servletHandler);
        try {
            server.start();
            System.out.println(String.format("jetty server started at port %s, context path %s", port, contextPath));
//            TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Server createJetty(String contextPath, int port, String homePath, ArrayList<? extends EventListener> listeners, ServletHandler servletHandler) {

        String webPath = homePath + "/web";
        String workPath = homePath + "/work";
        System.setProperty("org.apache.jasper.compiler.disablejsr199", "true");
        final Server server = new Server(port);
        server.setStopAtShutdown(true);
        WebAppContext context = new WebAppContext(webPath, contextPath);
        context.getInitParams().put("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
        context.setServer(server);
        context.setTempDirectory(new File(workPath));


        if (!CollectionUtils.isEmpty(listeners)) {
            for (EventListener listener : listeners) {
                try {
                    context.addEventListener(listener);
                } catch (Exception e) {
                    throw new RuntimeException("add listener error", e);
                }
            }
        }
        if (servletHandler != null) {
            context.setServletHandler(servletHandler);
        }
        server.setHandler(context);
        return server;
    }
}
