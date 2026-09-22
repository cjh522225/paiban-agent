package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.User;
import com.Firefire.paiban.mapper.UserMapper;
import com.Firefire.paiban.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

    @Override
    public User login(String account, String password) {
        // 先查用户是否存在
        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, account));
        if (user == null) {
            throw new RuntimeException("账号或密码错误");
        }
        // 检查是否被禁用
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用，无法登录");
        }
        // 校验密码: 兼容 BCrypt 加密存储与明文存储
        String storedPwd = user.getPassword();
        boolean match = false;
        if (storedPwd != null && storedPwd.startsWith("$2a$")) {
            match = passwordEncoder.matches(password, storedPwd);
        } else {
            match = password.equals(storedPwd);
        }
        if (!match) {
            throw new RuntimeException("账号或密码错误");
        }
        // 明文密码匹配成功 → 自动升级为 bcrypt 加密存储
        if (storedPwd != null && !storedPwd.startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(password));
            updateById(user);
        }
        return user;
    }

    @Override
    public boolean save(User user) {
        if (user.getPassword() != null && !user.getPassword().isEmpty() && !user.getPassword().startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        return super.save(user);
    }

    @Override
    public boolean updateById(User user) {
        if (user.getPassword() != null) {
            if (user.getPassword().isEmpty()) {
                // 编辑留空 = 不修改密码
                user.setPassword(null);
            } else if (!user.getPassword().startsWith("$2a$")) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
        }
        return super.updateById(user);
    }
}
