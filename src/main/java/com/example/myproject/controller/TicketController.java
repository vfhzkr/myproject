package com.example.myproject.controller;

import com.example.myproject.entity.Train;
import com.example.myproject.entity.TicketOrder;
import com.example.myproject.entity.Waitlist;
import com.example.myproject.service.TicketService;
import com.example.myproject.service.TrainService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TicketController {

    @Autowired
    private TrainService trainService;

    @Autowired
    private TicketService ticketService;

    /**
     * 获取当前用户ID，未登录返回null
     */
    private Long getLoginUserId(HttpSession session) {
        return UserController.getUserId(session);
    }

    /**
     * 未登录响应
     */
    private Map<String, Object> noLogin() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", -1);
        result.put("msg", "请先登录");
        return result;
    }

    /**
     * 查询所有车次
     */
    @GetMapping("/trains")
    public Map<String, Object> listTrains(
            @RequestParam(required = false) String departure,
            @RequestParam(required = false) String destination) {

        List<Train> trains;
        if (departure != null && !departure.isEmpty()
                && destination != null && !destination.isEmpty()) {
            trains = trainService.search(departure, destination);
        } else {
            trains = trainService.findAll();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", trains);
        return result;
    }

    /**
     * 查询单个车次
     */
    @GetMapping("/train/{id}")
    public Map<String, Object> getTrain(@PathVariable Long id) {
        Train train = trainService.findById(id);
        Map<String, Object> result = new HashMap<>();
        if (train == null) {
            result.put("code", -1);
            result.put("msg", "车次不存在");
        } else {
            result.put("code", 0);
            result.put("data", train);
        }
        return result;
    }

    /**
     * 购票
     */
    @PostMapping("/buy")
    public Map<String, Object> buyTicket(@RequestParam Long trainId,
                                          @RequestParam(defaultValue = "1") int count,
                                          HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return noLogin();

        String msg = ticketService.buyTicket(userId, trainId, count);
        Map<String, Object> result = new HashMap<>();
        result.put("code", msg.startsWith("购票成功") ? 0 : -1);
        result.put("msg", msg);
        return result;
    }

    /**
     * 退票
     */
    @PostMapping("/refund")
    public Map<String, Object> refundTicket(@RequestParam String orderNo,
                                             HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return noLogin();

        String msg = ticketService.refundTicket(userId, orderNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", msg.startsWith("退票成功") ? 0 : -1);
        result.put("msg", msg);
        return result;
    }

    /**
     * 候补
     */
    @PostMapping("/waitlist")
    public Map<String, Object> addWaitlist(@RequestParam Long trainId,
                                            @RequestParam(defaultValue = "1") int count,
                                            HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return noLogin();

        String msg = ticketService.addWaitlist(userId, trainId, count);
        Map<String, Object> result = new HashMap<>();
        result.put("code", msg.startsWith("候补成功") ? 0 : -1);
        result.put("msg", msg);
        return result;
    }

    /**
     * 取消候补
     */
    @PostMapping("/waitlist/cancel")
    public Map<String, Object> cancelWaitlist(@RequestParam Long waitlistId,
                                               HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return noLogin();

        String msg = ticketService.cancelWaitlist(userId, waitlistId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", msg.equals("候补已取消") ? 0 : -1);
        result.put("msg", msg);
        return result;
    }

    /**
     * 我的订单
     */
    @GetMapping("/orders")
    public Map<String, Object> myOrders(HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return noLogin();

        List<TicketOrder> orders = ticketService.myOrders(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", orders);
        return result;
    }

    /**
     * 我的候补
     */
    @GetMapping("/waitlist/my")
    public Map<String, Object> myWaitlist(HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return noLogin();

        List<Waitlist> waitlists = ticketService.myWaitlist(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", waitlists);
        return result;
    }
}
