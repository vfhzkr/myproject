package com.example.myproject.controller;

import com.example.myproject.common.Result;
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
    public Result<?> register(@RequestParam String username,
                              @RequestParam String password,
                              @RequestParam(required = false) String phone,
                              @RequestParam(required = false) String email) {
        String msg = userService.register(username, password, phone, email);
        if (msg.equals("注册成功")) {
            return Result.ok();
        }
        return Result.fail(msg);
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<?> login(@RequestParam String username,
                           @RequestParam String password,
                           HttpSession session) {
        User user = userService.login(username, password);
        if (user != null) {
            session.setAttribute(SESSION_KEY, user);
            Map<String, Object> data = new HashMap<>();
            data.put("username", user.getUsername());
            return Result.ok(data);
        }
        return Result.fail("用户名或密码错误");
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/current")
    public Result<?> current(HttpSession session) {
        User user = (User) session.getAttribute(SESSION_KEY);
        if (user != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", user.getId());
            data.put("username", user.getUsername());
            return Result.ok(data);
        }
        return Result.fail("未登录");
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<?> logout(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return Result.ok();
    }

    /**
     * 从 Session 获取当前用户ID（供内部调用）
     */
    public static Long getUserId(HttpSession session) {
        User user = (User) session.getAttribute(SESSION_KEY);
        return user != null ? user.getId() : null;
    }
}
