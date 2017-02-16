package com.wk.web.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.Base64;

/**
 * Created by 005689 on 2016/12/6.
 */
public class WebUtils {

    public static final ObjectMapper mapper = new ObjectMapper();
    public static final String ENCODE_UTF8 = "UTF-8";

    public static Constructor getMatchedConstructor(Class cls, Class[] argClassArr) {

        Constructor[] cons = cls.getConstructors();
        first:
        for (Constructor con : cons) {

            if (con.getParameterCount() != argClassArr.length) continue;
            Class[] acls = con.getParameterTypes();
            for (int i = 0; i < acls.length; i++) {
                if (!acls[i].isAssignableFrom(argClassArr[i])) continue first;
            }
            return con;
        }
        return null;
    }

    public static void toJSON(HttpServletResponse response) {
        response.setContentType("application/json;charset=" + ENCODE_UTF8);
    }

    public static void toHTML(HttpServletResponse response) {
        response.setContentType("text/html;charset=" + ENCODE_UTF8);
    }

    public static String objToJsonText(Object src) {
        try {
            return mapper.writeValueAsString(src);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isFieldIgnored(Field field) {
        int m = field.getModifiers();
        return (!Modifier.isPublic(m)) || Modifier.isStatic(m) || Modifier.isFinal(m)
                || Modifier.isNative(m) || Modifier.isTransient(m);
    }

    public static Object caseValueByClass(String s, Class<?> type) {

        if (s == null) return null;

        if (int.class.isAssignableFrom(type) || Integer.class.isAssignableFrom(type)) {
            return Integer.parseInt(StringUtils.isBlank(s) ? "0" : s);
        } else if (Long.class.isAssignableFrom(type) || long.class.isAssignableFrom(type)) {
            return Long.parseLong(StringUtils.isBlank(s) ? "0L" : s);
        } else if (Boolean.class.isAssignableFrom(type) || boolean.class.isAssignableFrom(type)) {
            return Boolean.valueOf(StringUtils.isBlank(s) ? "false" : s);
        } else {
            return type.cast(s);
        }
    }

    /**
     * 对字符串进行摘要，并对结果进行base64编码
     */
    public static String md52Base64(String pwd) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(pwd.getBytes(ENCODE_UTF8));
            return Base64.getEncoder().encodeToString(m.digest());
        } catch (Exception e) {
            throw new RuntimeException("摘要发生错误：" + e.getMessage(), e);
        }
    }
}
