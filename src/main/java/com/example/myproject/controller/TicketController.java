package com.example.myproject.controller;

import com.example.myproject.common.Result;
import com.example.myproject.entity.Train;
import com.example.myproject.entity.TicketOrder;
import com.example.myproject.entity.Waitlist;
import com.example.myproject.service.TicketService;
import com.example.myproject.service.TrainService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 查询所有车次
     */
    @GetMapping("/trains")
    public Result<?> listTrains(
            @RequestParam(required = false) String departure,
            @RequestParam(required = false) String destination) {

        List<Train> trains;
        if (departure != null && !departure.isEmpty()
                && destination != null && !destination.isEmpty()) {
            trains = trainService.search(departure, destination);
        } else {
            trains = trainService.findAll();
        }
        return Result.ok(trains);
    }

    /**
     * 查询单个车次
     */
    @GetMapping("/train/{id}")
    public Result<?> getTrain(@PathVariable Long id) {
        Train train = trainService.findById(id);
        if (train == null) {
            return Result.fail("车次不存在");
        }
        return Result.ok(train);
    }

    /**
     * 购票
     */
    @PostMapping("/buy")
    public Result<?> buyTicket(@RequestParam Long trainId,
                               @RequestParam(defaultValue = "1") int count,
                               HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return Result.unauthorized();

        String msg = ticketService.buyTicket(userId, trainId, count);
        if (msg.startsWith("购票成功")) {
            return Result.ok(msg);
        }
        return Result.fail(msg);
    }

    /**
     * 退票
     */
    @PostMapping("/refund")
    public Result<?> refundTicket(@RequestParam String orderNo,
                                  HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return Result.unauthorized();

        String msg = ticketService.refundTicket(userId, orderNo);
        if (msg.startsWith("退票成功")) {
            return Result.ok(msg);
        }
        return Result.fail(msg);
    }

    /**
     * 候补
     */
    @PostMapping("/waitlist")
    public Result<?> addWaitlist(@RequestParam Long trainId,
                                 @RequestParam(defaultValue = "1") int count,
                                 HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return Result.unauthorized();

        String msg = ticketService.addWaitlist(userId, trainId, count);
        if (msg.startsWith("候补成功")) {
            return Result.ok(msg);
        }
        return Result.fail(msg);
    }

    /**
     * 取消候补
     */
    @PostMapping("/waitlist/cancel")
    public Result<?> cancelWaitlist(@RequestParam Long waitlistId,
                                    HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return Result.unauthorized();

        String msg = ticketService.cancelWaitlist(userId, waitlistId);
        if (msg.equals("候补已取消")) {
            return Result.ok(msg);
        }
        return Result.fail(msg);
    }

    /**
     * 我的订单
     */
    @GetMapping("/orders")
    public Result<?> myOrders(HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return Result.unauthorized();

        List<TicketOrder> orders = ticketService.myOrders(userId);
        return Result.ok(orders);
    }

    /**
     * 我的候补
     */
    @GetMapping("/waitlist/my")
    public Result<?> myWaitlist(HttpSession session) {
        Long userId = getLoginUserId(session);
        if (userId == null) return Result.unauthorized();

        List<Waitlist> waitlists = ticketService.myWaitlist(userId);
        return Result.ok(waitlists);
    }
}
