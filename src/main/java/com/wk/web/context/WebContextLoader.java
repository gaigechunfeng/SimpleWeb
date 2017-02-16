package com.wk.web.context;

import com.wk.web.annotations.Bean;
import com.wk.web.annotations.Component;
import com.wk.web.annotations.Controller;
import com.wk.web.utils.Permissions;
import com.wk.web.utils.WebUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by 005689 on 2016/5/23.
 */
public class WebContextLoader {

    private static final String BEANS_FILE = "/beans.xml";
    private static final WebContextLoader contextLoader = new WebContextLoader();
    private Element root;
    private Map<String, Object> ctx = new ConcurrentHashMap<>();
    private volatile ContextLoadStatus status = ContextLoadStatus.NOTINIT;
    private static final Logger log = LoggerFactory.getLogger(WebContextLoader.class);
    private final Map<String, Integer> permissions = new ConcurrentHashMap<>();
    private List<Element> xmlBeans = new ArrayList<>();

    private WebContextLoader() {

    }

    public static WebContextLoader getInstance() {
        return contextLoader;
    }

    public synchronized void init() throws Exception {

        if (!status.equals(ContextLoadStatus.NOTINIT)) {
            //log.info("当前状态无法进行WebContext初始化！");
            return;
        }
        try {
            status = ContextLoadStatus.INITING;
            Document document = getXmlDoc();

            if (document != null) {
                root = document.getRootElement();

            }
            /**
             * 解析通过xml中配置的bean
             */
            parseXmlBeans();

            /**
             * 解析通过Annotation配置的bean
             */
            parseAnnotation();

            /**
             * 给bean的各个属性赋值
             */
            associationFields();

            /**
             * 解析静态资源
             */
            parseResources();

            /**
             * 加载XML配置权限信息
             */
            loadPermissions();
            status = ContextLoadStatus.INITED;
        } catch (DocumentException e) {
            log.error("WebContextLoader初始化失败！");
        }
    }

    public Map<String, Integer> getPermissions() {
        return permissions;
    }

    private void parseXmlBeans() {

        if (root == null) return;
        List beans = root.selectNodes("/beans/bean");
        for (Object bean : beans) {
            Element element = (Element) bean;

            try {
                initBean(element);
            } catch (Exception e) {
                log.error("解析bean失败！[" + element + "]" + e.getMessage(), e);
            }
        }
    }

    private void loadPermissions() {

        if (root == null) return;
        List permissions = root.selectNodes("/beans/permissions/permission");
        for (Object o : permissions) {
            Element element = (Element) o;
            this.permissions.put(getAttrValue(element, "path"), Integer.parseInt(getAttrValue(element, "value", Permissions.ADMIN + "")));
        }
    }

    private String getAttrValue(Element element, String value, String df) {
        String s = getAttrValue(element, value);

        if (s == null) return df;
        return s;
    }

    private void associationFields() {

        for (Map.Entry<String, Object> entry : ctx.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();

            Class cls = bean.getClass();
            Field[] fields = cls.getDeclaredFields();
            if (ArrayUtils.isEmpty(fields)) continue;

            for (Field field : fields) {

                Bean b = field.getAnnotation(Bean.class);
                if (b == null) continue;
                log.debug("开始解析bean[name:" + beanName + ",class=" + bean + "]的属性[name:" + field.getName() + "]");
                String bn = b.name();
                if (StringUtils.isBlank(bn)) {
                    bn = getBeanNameByClassName(field.getType());
                }
                Object bv = ctx.get(bn);
                if (bv == null) {
                    log.error("解析bean的属性出错！[Key=" + bn + "]的值为空！");
                    continue;
                }
                ReflectionUtils.makeAccessible(field);
                ReflectionUtils.setField(field, bean, bv);
                log.debug("解析bean[name:" + beanName + ",class=" + bean + "]的属性[name:" + field.getName() + "]完成~~");
            }
        }

        for (Element bean : xmlBeans) {
            String beanName = getAttrValue(bean, "name");
            Object beanObj = ctx.get(beanName);
            if (beanObj == null) {
                log.error("解析XML Bean[name=" + beanName + "]属性出错，bean为空！");
                continue;
            }

            List<Element> properties = bean.selectNodes("property");
            if (!CollectionUtils.isEmpty(properties)) {

                for (Element property : properties) {
                    String proName = getAttrValue(property, "name");
                    String ref = getAttrValue(property, "ref");
                    log.debug("开始解析bean[name:" + beanName + ",class=" + beanObj + "]的属性[name:" + proName + "]");
                    Object refValue = ctx.get(ref);
                    if (refValue == null) {
                        log.error("解析XML Bean[name=" + beanName + "]属性[name=" + proName + "]出错，bean[ref=" + ref + "]为空！");
                        continue;
                    }
                    Field f = ReflectionUtils.findField(beanObj.getClass(), proName);
                    ReflectionUtils.makeAccessible(f);
                    ReflectionUtils.setField(f, beanObj, refValue);
                    log.debug("解析bean[name:" + beanName + ",class=" + beanObj + "]的属性[name:" + proName + "]完成~~");
                }
            }
        }
    }

    private void parseResources() {
        log.debug("开始解析静态资源~~");
        if (root == null) return;
        List<Element> elements = root.selectNodes("/beans/resources/resource");
        if (CollectionUtils.isEmpty(elements)) return;

        Class resourceClass = getDefaultResourceContainer();
        String beanName = getBeanNameByClassName(resourceClass);
        Object bean;
        if (!ctx.containsKey(beanName)) {
            bean = initBean(resourceClass);
        } else {
            bean = ctx.get(beanName);
        }
        for (Element element : elements) {

            String path = element.attributeValue("path");
            ResourceContainer.ChooseType type;
            if (org.apache.commons.lang3.StringUtils.isBlank(path)) {
                type = ResourceContainer.ChooseType.REGEXP;
                path = element.attributeValue("regexp");
            } else {
                type = ResourceContainer.ChooseType.PATH;
            }
            boolean needLogin = "true".equalsIgnoreCase(element.attributeValue("login"));
            ((ResourceContainer) bean).addResource(new ResourceContainer.Resource(path, type, needLogin));
        }
        log.debug("解析静态资源完成~~");
    }

    private Object initBean(Class cls) {
        String beanName = getBeanNameByClassName(cls);
        Object bean;
        try {
            bean = cls.newInstance();
            ctx.put(beanName, bean);

            return bean;
        } catch (Exception e) {
            log.error("初始化bean失败！", e);
            throw new RuntimeException(e);
        }
    }

    protected Class getDefaultResourceContainer() {

        return ResourceContainer.class;
    }

    @SuppressWarnings("unchecked")
    private void parseAnnotation() {

        if (root == null) return;
        log.debug("开始解析 Annotation ~~");
        List<Element> anno = root.selectNodes("/beans/annotation-scan");
        if (CollectionUtils.isEmpty(anno)) return;

        for (Element an : anno) {
            String pg = getAttrValue(an, "package");
            if (StringUtils.isBlank(pg)) continue;

            parsePackage(pg);
        }
        log.debug("解析 Annotation 完成~~");
    }

    private void parsePackage(String pg) {
        Reflections reflections = new Reflections(ConfigurationBuilder.build(pg).addScanners(new MethodAnnotationsScanner()));

        //解析bean,通过静态方法
        Set<Method> methods = reflections.getMethodsAnnotatedWith(Component.class);
        if (!CollectionUtils.isEmpty(methods)) {
            for (Method method : methods) {
                Component component = method.getAnnotation(Component.class);
                parseBeanByMethod(component.value(), method);
            }
        }
        //解析bean
        Set<Class<?>> beans = reflections.getTypesAnnotatedWith(Component.class);
        if (!CollectionUtils.isEmpty(beans)) {
            for (Class<?> bean : beans) {
                Component component = bean.getAnnotation(Component.class);
                parseBean(component.value(), bean);
            }
        }
        //解析controller
        Set<Class<?>> clss = reflections.getTypesAnnotatedWith(Controller.class);
        if (!CollectionUtils.isEmpty(clss)) {
            for (Class<?> cls : clss) {
                Controller controller = cls.getAnnotation(Controller.class);
                parseBean(controller.name(), cls);
            }

        }
    }

    private void parseBeanByMethod(String beanName, Method method) {
        try {
            Class cls = method.getReturnType();
            if (StringUtils.isBlank(beanName)) {
                beanName = getBeanNameByClassName(cls);
            }
            log.debug("开始初始化bean【name：" + beanName + "，class：" + cls + "，通过静态方法】~~");

            Object obj = method.invoke(null);
            ctx.putIfAbsent(beanName, obj);
            log.debug("初始化bean【name:" + beanName + "，class:" + cls + "】完成~");
//            parseField(obj);
        } catch (Exception e) {
            log.error("添加Bean失败！", e);
        }
    }

    private void parseBean(String beanName, Class bean) {

        if (StringUtils.isBlank(beanName)) {
            beanName = getBeanNameByClassName(bean);
        }
        try {
            log.debug("开始初始化bean【name：" + beanName + "，class：" + bean + "】~~");
            Object obj = bean.newInstance();
            ctx.putIfAbsent(beanName, obj);
            log.debug("初始化bean【name:" + beanName + "，class:" + bean + "】完成~");
//            parseField(obj);
        } catch (Exception e) {
            log.error("添加Bean失败！", e);
        }
    }

//    private void parseField(Object obj) {
//        Class cls = obj.getClass();
//        Field[] fields = cls.getDeclaredFields();
//        for (Field field : fields) {
//            String proName = field.getName();
//            Annotation annotation = field.getAnnotation(Bean.class);
//            if(annotation == null) continue;
//            try{
//                Bean bean = (Bean) annotation;
//                String beanName = bean.name();
//                if(StringUtils.isBlank(beanName)){
//                    beanName = getBeanNameByClassName(field.getType());
//                }
//                if(!ctx.containsKey(beanName)){
//                    initBean(findByName(beanName));
//                }
//
//                Object refValue = ctx.get(beanName);
//                String setMethodName = getSetterMethodName(proName);
//                cls.getMethod(setMethodName, FieldUtils.getField(cls,proName,true).getType()).invoke(obj, refValue);
//            }catch ( Exception e){
//                log.error("解析属性出错【name="+proName+"】",e);
//            }
//        }
//    }

    private static String getBeanNameByClassName(Class cls) {
        if (cls == null || cls.equals(Void.class)) throw new RuntimeException("cls can not be null or void");
        String clsName = cls.getSimpleName();
        return clsName.substring(0, 1).toLowerCase() + clsName.substring(1);
    }

    /**
     * 通过XML文件，实例化bean
     *
     * @param bean
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private void initBean(Element bean) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        Object obj;
        String beanName = getAttrValue(bean, "name");
        String className = getAttrValue(bean, "class");
        Class cls = Class.forName(className);

        if (isStatic(bean)) {
            String initMethod = getAttrValue(bean, "init-method");
            assert initMethod != null;
            Method method = cls.getMethod(initMethod);

            obj = method.invoke(null);
        } else {

            List<Element> args = bean.selectNodes("constructor/arg");
            if (CollectionUtils.isEmpty(args)) {
                obj = cls.newInstance();
            } else {
                Class[] argClassArr = new Class[args.size()];
                Object[] argValueArr = new Object[args.size()];
                int i = 0;
                for (Element arg : args) {
                    String ref = getAttrValue(arg, "ref");
                    if (!ctx.containsKey(ref)) {
                        initBean(findByName(ref));
                    }
                    Object refValue = ctx.get(ref);
                    argClassArr[i] = refValue.getClass();
                    argValueArr[i] = refValue;

                    i++;
                }

                Constructor cons = WebUtils.getMatchedConstructor(cls, argClassArr);
                if (cons == null) throw new NoSuchMethodException("类【" + cls + "】中没有匹配【" + Arrays.toString(argClassArr)
                        + "】的构造方法！");
                obj = cons.newInstance(argValueArr);
            }
        }
        ctx.put(beanName, obj);
        log.debug("初始化bean【name:" + beanName + "，class:" + className + "】完成~");
        xmlBeans.add(bean);

    }

    private static String getSetterMethodName(String field) {

        return "set" + field.substring(0, 1).toUpperCase() + field.substring(1);
    }

    //
    private Element findByName(String ref) {

        List<Element> elements = root.selectNodes("/beans/bean[@name='" + ref + "']");
        return elements.get(0);
    }

    private static synchronized String getAttrValue(Element bean, String s) {
        Attribute attr = bean.attribute(s);
        if (attr == null) {
            return null;
        }
        return attr.getValue();
    }

    private static boolean isStatic(Element bean) {

        return "true".equals(getAttrValue(bean, "static-init"));
    }

    private Document getXmlDoc() throws DocumentException {

        SAXReader reader = new SAXReader();
        InputStream is = WebContextLoader.class.getResourceAsStream(BEANS_FILE);

        if (is == null) {
            log.error("配置文件[" + BEANS_FILE + "]未找到！");
            return null;
        }
        return reader.read(is);
    }

    public Map getAllBeans() {

        return Collections.unmodifiableMap(ctx);
    }

    public synchronized Object getBean(String service1) {
        return ctx.get(service1);
    }

//    public synchronized boolean notInit() {
//
//        return status.equals(ContextLoadStatus.NOTINIT);
//    }

    public void destroy() {
        ctx.clear();
        ctx = null;
    }

    public ResourceContainer getResourceContainer() {

        Object o = ctx.get(getBeanNameByClassName(getDefaultResourceContainer()));
        if (o != null) return (ResourceContainer) o;
        return null;
    }
}
