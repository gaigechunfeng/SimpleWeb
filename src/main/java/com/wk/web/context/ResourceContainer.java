package com.wk.web.context;

import org.eclipse.jetty.util.ConcurrentHashSet;

import java.util.Set;

/**
 * Created by 005689 on 2016/6/15.
 */
public class ResourceContainer {

    private final Set<Resource> resources = new ConcurrentHashSet<>();

    public void addResource(Resource resource) {
        resources.add(resource);
    }

    public Resource getResource(String requestUri) {

        for (Resource resource : resources) {

            String content = resource.content;
            ChooseType type = resource.type;

            if (type.equals(ChooseType.PATH) && requestUri.startsWith(content)) return resource;
            if (type.equals(ChooseType.REGEXP) && requestUri.matches(content)) return resource;
        }
        return null;
    }

    static class Resource {

        final String content;
        final ChooseType type;
        final boolean needLogin;

        public Resource(String path, ChooseType type, boolean needLogin) {
            this.content = path;
            this.type = type;
            this.needLogin = needLogin;
        }
    }

    enum ChooseType {
        PATH, REGEXP
    }
}
