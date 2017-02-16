# 简易web框架

## 准备

- 安装git环境

- 安装maven环境

## 安装

- mkdir workspace && cd workspace && git clone https://github.com/gaigechunfeng/SimpleWeb

- mvn install

## 使用

### 配置

- 引入

```xml
    <dependency>
        <groupId>com.wk</groupId>
        <artifactId>web</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
```

- web.xml添加listener，WebContextLoader

```xml
    <listener>
        <listener-class>com.wk.web.listener.WebInitListener</listener-class>
    </listener>
```

- web.xml添加入口servlet，WebDispacherServlet

```xml
    <servlet>
        <servlet-name>WebDispatcher</servlet-name>
        <servlet-class>com.wk.web.context.WebDispacherServlet</servlet-class>
    </servlet>
    <servlet-mapping>
        <servlet-name>WebDispatcher</servlet-name>
        <url-pattern>/</url-pattern>
    </servlet-mapping>
```
- 实现User接口

```java
    public class WebUser extends UserEntity implements User {
        @Override
        public String getUserName() {
            return wid;
        }
    }
```

- 在classpath下定义beans.xml

```xml
    <?xml version="1.0" encoding="UTF-8" ?>
    <beans>
        <!--扫描Bean-->
        <annotation-scan package="com.wk.web"></annotation-scan>
    
        <!--静态资源-->
        <resources>
            <resource path="/css"></resource>
            <resource path="/js"></resource>
            <resource path="/img"></resource>
            <resource path="/lib"></resource>
            <resource regexp="^(.*)?\.html$" login="true"></resource>
        </resources>
    
        <permissions>
            <permission path="/user/list" value="30"></permission>
        </permissions>
    </beans>
```

### 使用

- @Component

- @Controller

- @Bean

- @RequestMapping

- @Json

- @CurrUser

- @NoCsrf

- @NoLogin

- @PathParam

- @Permission

