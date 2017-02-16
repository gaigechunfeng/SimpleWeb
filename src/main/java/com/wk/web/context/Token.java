package com.wk.web.context;

import com.wk.web.utils.User;

/**
 * Created by 005689 on 2016/12/13.
 */
public class Token {

    private String id;
    private User user;

    public Token(String tokenId, User user) {
        this.id = tokenId;
        this.user = user;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
