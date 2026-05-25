package com.example.myproject.service;

import com.example.myproject.entity.User;
import com.example.myproject.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

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
        user.setPassword(password); // 演示项目，明文存储
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
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }

    public User findById(Long id) {
        return userMapper.findById(id);
    }
}
