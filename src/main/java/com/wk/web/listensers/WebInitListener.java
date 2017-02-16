package com.wk.web.listensers;


import com.wk.web.context.WebContextLoader;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Created by 005689 on 2016/5/23.
 */
public class WebInitListener implements ServletContextListener {

    private final WebContextLoader contextLoader = WebContextLoader.getInstance();
//    private static final Logger log = LoggerFactory.getLogger(WebInitListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            contextLoader.init();
        } catch (Exception e) {
            throw new RuntimeException("系统初始化错误！", e);
        }

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

        contextLoader.destroy();
    }
}
