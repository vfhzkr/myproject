package com.example.myproject.service;

import com.example.myproject.entity.User;
import com.example.myproject.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 注册
     */
    public String register(String username, String password, String phone, String email) {
        if (username == null || username.trim().isEmpty()) {
            return "用户名不能为空";
        }
        if (password == null || password.length() < 4) {
            return "密码至少4位";
        }
        if (userMapper.findByUsername(username) != null) {
            return "用户名已存在";
        }
        if (phone != null && !phone.isEmpty() && userMapper.findByPhone(phone) != null) {
            return "手机号已被注册";
        }
        if (email != null && !email.isEmpty() && userMapper.findByEmail(email) != null) {
            return "邮箱已被注册";
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(phone != null ? phone.trim() : "");
        user.setEmail(email != null ? email.trim() : "");
        userMapper.insert(user);
        return "注册成功";
    }

    /**
     * 登录
     */
    public User login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        User user = userMapper.findByUsername(username.trim());
        if (user == null) {
            return null;
        }

        // 1. BCrypt 正常验证
        if (passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }

        // 2. 兼容旧版明文密码：检测到明文密码时自动升级为 BCrypt
        if (!user.getPassword().startsWith("$2")) {
            if (user.getPassword().equals(password)) {
                String encoded = passwordEncoder.encode(password);
                userMapper.updatePassword(user.getId(), encoded);
                user.setPassword(encoded);
                return user;
            }
        }

        return null;
    }

    public User findById(Long id) {
        return userMapper.findById(id);
    }
}
