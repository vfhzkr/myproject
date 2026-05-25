package com.example.myproject.controller;

import com.example.myproject.entity.User;
import com.example.myproject.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final String SESSION_KEY = "LOGIN_USER";

    @Autowired
    private UserService userService;

    /**
     * 注册
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestParam String username,
                                         @RequestParam String password,
                                         @RequestParam(required = false) String phone,
                                         @RequestParam(required = false) String email) {
        String msg = userService.register(username, password, phone, email);
        Map<String, Object> result = new HashMap<>();
        result.put("code", msg.equals("注册成功") ? 0 : -1);
        result.put("msg", msg);
        return result;
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username,
                                      @RequestParam String password,
                                      HttpSession session) {
        User user = userService.login(username, password);
        Map<String, Object> result = new HashMap<>();
        if (user != null) {
            session.setAttribute(SESSION_KEY, user);
            result.put("code", 0);
            result.put("msg", "登录成功");
            Map<String, Object> data = new HashMap<>();
            data.put("username", user.getUsername());
            result.put("data", data);
        } else {
            result.put("code", -1);
            result.put("msg", "用户名或密码错误");
        }
        return result;
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/current")
    public Map<String, Object> current(HttpSession session) {
        User user = (User) session.getAttribute(SESSION_KEY);
        Map<String, Object> result = new HashMap<>();
        if (user != null) {
            result.put("code", 0);
            Map<String, Object> data = new HashMap<>();
            data.put("id", user.getId());
            data.put("username", user.getUsername());
            result.put("data", data);
        } else {
            result.put("code", -1);
            result.put("msg", "未登录");
        }
        return result;
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("msg", "已退出");
        return result;
    }

    /**
     * 从 Session 获取当前用户ID（供内部调用）
     */
    public static Long getUserId(HttpSession session) {
        User user = (User) session.getAttribute(SESSION_KEY);
        return user != null ? user.getId() : null;
    }
}
