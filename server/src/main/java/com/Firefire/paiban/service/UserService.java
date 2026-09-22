package com.Firefire.paiban.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.Firefire.paiban.entity.User;

public interface UserService extends IService<User> {
    /**
     * 用户登录
     */
    User login(String username, String password);
}
