package com.wk.web.context;

import com.wk.web.exceptions.WebException;
import com.wk.web.utils.Msg;
import com.wk.web.utils.Permissions;
import com.wk.web.utils.User;
import com.wk.web.utils.WebUtils;
import com.wk.web.annotations.*;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.*;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中心控制器
 * Created by 005689 on 2016/6/13.
 */
public class WebDispacherServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(WebDispacherServlet.class);
    private static final String LOGIN_URL = "/login.html";
    public static final String NOT_LOGIN = "notLogin";
    private final Map<String, ControllerMap> controllers = new ConcurrentHashMap<>();
    private static final ThreadLocal<HttpSession> localSession = new ThreadLocal<>();

    /**
     * 登录页面
     */
    private String loginPage = LOGIN_URL;

    /**
     * 默认访问应用所需权限
     */
    private int defaultPms = Permissions.ADMIN;

    public void setLoginPage(String loginPage) {
        this.loginPage = loginPage;
    }

    public void setDefaultPms(int defaultPms) {
        this.defaultPms = defaultPms;
    }

    @Override
    public void init() throws ServletException {

        super.init();

        initialize();
    }

    private void initialize() {

        Map<String, Object> beans = WebContextLoader.getInstance().getAllBeans();
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Annotation annotation = bean.getClass().getAnnotation(Controller.class);
            if (annotation != null) {
                RequestMapping classMapping = bean.getClass().getAnnotation(RequestMapping.class);
                if (classMapping == null) {
                    log.error("controller的requestmapping为空！[" + bean + "]");
                    continue;
                }
                Method[] methods = bean.getClass().getMethods();
                for (Method method : methods) {

                    RequestMapping methodMapping = method.getAnnotation(RequestMapping.class);
                    if (methodMapping == null) continue;
                    controllers.put(mergeMapping(classMapping, methodMapping), new ControllerMap(bean, method));
                }
            }
        }

        String lp = getServletConfig().getInitParameter("login_page");
        String dp = getServletConfig().getInitParameter("default_pms");
        if (!StringUtils.isBlank(lp)) {
            setLoginPage(lp);
        }
        if (!StringUtils.isBlank(dp) && NumberUtils.isDigits(dp)) {
            setDefaultPms(Integer.parseInt(dp));
        }
    }

    private String mergeMapping(RequestMapping classMapping, RequestMapping methodMapping) {

        String cm = classMapping.value().trim();
        String mm = methodMapping.value().trim();

        String m = cm + mm;

        return m.replaceAll("//", "/");
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        bindLocalSession(request);
        applyCharacterEncoding(response);

        String requestUri = request.getRequestURI();
        requestUri = requestUri.substring(request.getContextPath().length());
        log.debug("requestURI:::" + requestUri);

        try {
            ControllerMap cMap = getMatchedMethod(requestUri);
            if (handleCsrf(request, response, cMap)) {
                return;
            }
            //处理静态文件，静态文件访问不需要登录
            if (handleStaticResource(requestUri, request, response)) {
                return;
            }

            if (cMap == null) {
                throw new RuntimeException("未发现匹配路径【" + requestUri + "】的controller！");
            }
            applyPermission(cMap, request, response);

            Object obj = cMap.method.invoke(cMap.controller, buildParams(cMap, request, response));
            response.setHeader("newSessionId",request.getSession().getId());
            Cookie[] cookies = request.getCookies();
            for (Cookie cookie:cookies ) {
                if (cookie.getName().equals("JSESSIONID")){
                    cookie.setHttpOnly(true);
                    response.addCookie(cookie);
                }
            }
            if (obj != null && !response.isCommitted()) {
                String outStr;
                if (isJson(cMap.method)) {
                    WebUtils.toJSON(response);
                    outStr = WebUtils.objToJsonText(obj);
                } else {
//                    WebUtils.toHTML(response);
                    outStr = obj.toString();
                }
                response.getWriter().write(outStr);
                response.getWriter().flush();
            }
        } catch (Exception e) {
            handleException(e instanceof InvocationTargetException ? (Exception) e.getCause() : e, request, response);
        }
    }

    /**
     * 防止Csrf攻击
     */
    private boolean handleCsrf(HttpServletRequest request, HttpServletResponse response, ControllerMap cMap) {

        if (!isLogin(request.getRequestURI()) && !needntCsrf(cMap)) {

            String referer = request.getHeader("Referer");
            if (!isSameDomain(referer, request.getRequestURL().toString())) {
                try {
                    if (isXmlHttpRequest(request)) {

                        WebUtils.toJSON(response);
                        response.getWriter().write(WebUtils.objToJsonText(new Msg(false, "访问方式不合法！")));
                    } else {

                        WebUtils.toHTML(response);
                        toLogin(request, response);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean needntCsrf(ControllerMap cMap) {

        if (cMap != null) {
            if (cMap.method.getAnnotation(NoCsrf.class) != null || cMap.controller.getClass().getAnnotation(NoCsrf.class) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameDomain(String url1, String url2) {

        try {
            if (StringUtils.isBlank(url1) || StringUtils.isBlank(url2)) {
                return false;
            }
            URL u1 = new URL(url1);
            URL u2 = new URL(url2);
            return u1.getProtocol().equals(u2.getProtocol())
                    && u1.getHost().equals(u2.getHost())
                    && u1.getPort() == u2.getPort();
        } catch (Exception e) {

            log.error("比对URL域失败！[" + url1 + "][" + url2 + "]");
            return false;
        }
    }

    /**
     * 验证权限，
     */
    protected void applyPermission(ControllerMap cMap, HttpServletRequest request, HttpServletResponse response) {
        if (noNeedLogin(cMap)) return;

        User userEntity = getCurrentUser();
        if (userEntity == null) {
            throw new WebException(NOT_LOGIN);
        }
        int userPms = userEntity.getPms();
        int requiredPms = defaultPms;

        Permission permission = cMap.method.getAnnotation(Permission.class);
        if (permission != null) {
            requiredPms = permission.value();
        }
        //从XMl配置文件中查询配置的权限
        Map<String, Integer> pmsMap = WebContextLoader.getInstance().getPermissions();
        String simReqUri = getSimpleRequestUri(request);
        if (pmsMap.containsKey(simReqUri)) {
            requiredPms = MapUtils.getIntValue(pmsMap, simReqUri, defaultPms);
        }

        if (userPms < requiredPms) {
            throw new WebException("权限不足");
        }
    }

    private static String getSimpleRequestUri(HttpServletRequest request) {

        String requestUri = request.getRequestURI();
        requestUri = requestUri.substring(request.getContextPath().length());

        if (requestUri.contains("?")) {
            requestUri = requestUri.substring(0, requestUri.indexOf("?"));
        }
        return requestUri;
    }

    private void toLogin(HttpServletRequest request, HttpServletResponse response) {
        try {
            if (isXmlHttpRequest(request)) {
                response.getWriter().write(WebUtils.objToJsonText(new Msg(false, "notLogin")));
            } else {
                response.sendRedirect(request.getContextPath() + loginPage + "?_ret=" + request.getRequestURI());
            }
        } catch (IOException e) {
            throw new RuntimeException("return to login error", e);
        }
    }

    private static boolean isXmlHttpRequest(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    private static boolean noNeedLogin(ControllerMap controllerMap) {

        Object controller = controllerMap.controller;

        if (controller.getClass().getAnnotation(NoLogin.class) != null) {
            return true;
        }

        Method method = controllerMap.method;
        return method.getAnnotation(NoLogin.class) != null;
        //        return ArrayUtils.contains(noNeedLoginUrls, requestUri);
    }

    private static void bindLocalSession(HttpServletRequest req) {
        localSession.set(req.getSession());
    }

    private static Object[] buildParams(ControllerMap controllerMap, HttpServletRequest request, HttpServletResponse response) {

        Map<String, String> params = controllerMap.params;
        Method method = controllerMap.method;

        Parameter[] parameters = method.getParameters();
        Object[] values = new Object[parameters.length];
        for (int i = 0, len = parameters.length; i < len; i++) {

            Parameter parameter = parameters[i];
            Class type = parameter.getType();
            if (type.isAssignableFrom(HttpServletRequest.class)) {
                values[i] = request;
            } else if (type.isAssignableFrom(HttpServletResponse.class)) {
                values[i] = response;
            } else if (parameter.getAnnotation(CurrUser.class) != null) {

                if (type == String.class) {
                    values[i] = getCurrentUserName();
                } else if (User.class.isAssignableFrom(type)) {
                    values[i] = getCurrentUser();
                } else {
                    log.error("绑定@CurrUser失败，必须是String或者User！[" + type + "]");
                    values[i] = null;
                }
            } else {
                String injectValue = null;
                PathParam pathParam = parameter.getAnnotation(PathParam.class);
                if (pathParam != null && params != null) {
                    injectValue = params.get(pathParam.value());
                }

                values[i] = extractFromRequest(type, request, injectValue);
            }

        }

        return values;
    }

    private static Object extractFromRequest(Class type, HttpServletRequest request, String injectValue) {

        try {
            if (injectValue != null) {
                return string2Obj(injectValue, type);
            }
            Object object = type.newInstance();
            Field[] fields = type.getFields();
            for (Field field : fields) {
                if (WebUtils.isFieldIgnored(field)) continue;
                String fieldName = field.getName();

                String reqVal = request.getParameter(fieldName);
                if (StringUtils.isBlank(reqVal)) continue;

                try {
                    field.set(object, WebUtils.caseValueByClass(reqVal, field.getType()));
                } catch (Exception e) {
                    log.debug("绑定属性[name=" + fieldName + "]值到对象[class=" + type + "]失败！跳过~~");
                }
            }

            return object;
        } catch (Exception e) {
            throw new RuntimeException("解析参数生成对象[class=" + type + "]失败！", e);
        }
    }

    private static Object string2Obj(String value, Class type) {

        if (StringUtils.isBlank(value)) return null;

        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        } else if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        } else if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        } else if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (type == String.class) {
            return value;
        } else {
            throw new RuntimeException("unsupported type![" + type + "]");
        }
    }

    private static void applyCharacterEncoding(HttpServletResponse response) {
        response.setCharacterEncoding(WebUtils.ENCODE_UTF8);
    }

    private boolean handleStaticResource(String requestUri, HttpServletRequest request, HttpServletResponse response) {

        ResourceContainer resourceContainer = WebContextLoader.getInstance().getResourceContainer();
        if (resourceContainer == null) return false;

        ResourceContainer.Resource resource = resourceContainer.getResource(requestUri);
        if (resource == null) return false;
        if (StringUtils.isBlank(getCurrentUserName()) && resource.needLogin && !isLogin(requestUri)) {

            toLogin(request, response);
            return true;
        }

        File resourceFile = new File(request.getServletContext().getRealPath(requestUri));
        try {
            if (!resourceFile.exists() || !resourceFile.isFile())
                throw new FileNotFoundException("文件不存在【" + resourceFile + "】");

//            if(applyCache(resourceFile,request,response)) return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String mimeType = request.getServletContext().getMimeType(resourceFile.getAbsolutePath());

        try (DataOutputStream dos = new DataOutputStream(response.getOutputStream())) {

            response.setContentType(mimeType);

            dos.write(FileUtils.readFileToByteArray(resourceFile));
            dos.flush();

            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isLogin(String requestUri) {

        String page = requestUri.contains("/") ? requestUri.substring(requestUri.lastIndexOf("/")) : requestUri;
        return page.equalsIgnoreCase(loginPage);
    }

    private static boolean applyCache(File resourceFile, HttpServletRequest request, HttpServletResponse response) {
        try {
//           String s = DigestUtils.sha256Hex(FileUtils.readFileToByteArray(resourceFile));//通过监视文件内容是否改变
            String s = String.valueOf(resourceFile.lastModified());//通过监视文件的最后修改时间
            if (s.equals(request.getHeader("If-None-Match"))) {
                response.setStatus(304);
                return true;
            }
            response.addHeader("Cache-Control", "max-age=" + (3600 * 1000));
            response.addHeader("ETag", s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    private static boolean isJson(Method method) {

        return method.getDeclaredAnnotation(Json.class) != null;
    }

    protected void handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        String msg = e.getMessage();

        if (NOT_LOGIN.equals(msg)) {
            toLogin(request, response);
            return;
        }
        if (e instanceof WebException) {
            log.error(msg);
        } else {
            log.error(msg, e);
        }

        try {
            if (e instanceof FileNotFoundException || (e.getCause() != null && e.getCause() instanceof FileNotFoundException)) {
                response.setStatus(404);
                return;
            }
            String resMsg;
            if (isXmlHttpRequest(request)) {
                WebUtils.toJSON(response);
                resMsg = WebUtils.objToJsonText(new Msg(false, msg));
            } else {
                WebUtils.toHTML(response);
                resMsg = msg;
            }
            response.getWriter().write(resMsg);
        } catch (IOException e1) {
            log.error("输出失败！", e1);
        }
    }

    private ControllerMap getMatchedMethod(String requestURI) {

        assert requestURI != null;
        requestURI = requestURI.toUpperCase();
        for (Map.Entry<String, ControllerMap> entry : controllers.entrySet()) {
            String mapping = entry.getKey();
            ControllerMap controllerMap = entry.getValue();
            if (mapping.equalsIgnoreCase(requestURI)) return controllerMap;

            if (mapping.contains("{") && mapping.contains("}")) {
                String[] ms = mapping.split("/");
                String[] rs = requestURI.split("/");

                if (ms.length == rs.length) {
                    Map<String, String> params = new HashMap<>();
                    for (int i = 0; i < ms.length; i++) {
                        String msv = ms[i];
                        String rsv = rs[i];
                        if (!msv.equalsIgnoreCase(rsv)) {
                            if (!msv.contains("{") || !msv.contains("}")) {
                                return null;
                            } else {
                                String key = msv.substring(msv.indexOf("{") + 1, msv.indexOf("}"));
                                params.put(key, rsv);
                            }
                        }
                    }
                    controllerMap.params = params;
                    return controllerMap;
                }
            }

        }
        return null;
    }

    public static String getCurrentUserName() {

        User userEntity = getCurrentUser();
        if (userEntity != null) {
            return userEntity.getUserName();
        }
        return null;
    }

    private static User getCurrentUser() {
        HttpSession session = localSession.get();
        if (session == null) return null;

        Object obj = session.getAttribute(User.USER_KEY);
        if (obj != null) {
            return (User) obj;
        }

        return null;
    }

    static class ControllerMap {

        Object controller;
        Method method;
        Map<String, String> params;

        public ControllerMap(Object bean, Method method) {
            this.controller = bean;
            this.method = method;
        }
    }
}
