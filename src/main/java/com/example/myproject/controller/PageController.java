package com.example.myproject.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/trains")
    public String trains() {
        return "train-list";
    }

    @GetMapping("/buy")
    public String buy() {
        return "buy";
    }

    @GetMapping("/orders")
    public String orders() {
        return "orders";
    }

    @GetMapping("/waitlist")
    public String waitlist() {
        return "waitlist";
    }
}
