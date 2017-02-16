package com.wk.web;

import com.wk.web.utils.WebUtils;
import org.hamcrest.SelfDescribing;
import org.junit.Test;
import org.springframework.util.DigestUtils;

/**
 * Created by 005689 on 2016/12/7.
 */
public class test {

    @Test
    public void t(){

        String pwd = "000000";
//
//        try {
//            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
//            messageDigest.update(pwd.getBytes("utf-8"));
//            System.out.println(Base64.getEncoder().encodeToString(messageDigest.digest()));
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }

        System.out.println(WebUtils.md52Base64(pwd));
    }
}
