package com.example.myproject.service;

import com.example.myproject.entity.TicketOrder;
import com.example.myproject.entity.Train;
import com.example.myproject.entity.Waitlist;
import com.example.myproject.mapper.TicketOrderMapper;
import com.example.myproject.mapper.TrainMapper;
import com.example.myproject.mapper.WaitlistMapper;
import com.example.myproject.service.TicketPurchaseService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class TicketService {

    @Autowired
    private TrainMapper trainMapper;

    @Autowired
    private TicketOrderMapper orderMapper;

    @Autowired
    private WaitlistMapper waitlistMapper;

    @Autowired
    private TrainService trainService;

    @Autowired
    private TicketPurchaseService ticketPurchaseService;

    /**
     * 候补处理线程池：异步处理退票后的候补转正，避免阻塞退票接口响应
     * core=2, max=4, 有界队列(500), CallerRunsPolicy 拒绝时由退票线程兜底
     */
    private final ExecutorService waitlistExecutor = new ThreadPoolExecutor(
            2,
            4,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            r -> {
                Thread t = new Thread(r, "waitlist-" + r.hashCode());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @PreDestroy
    public void shutdown() {
        waitlistExecutor.shutdown();
        try {
            if (!waitlistExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                waitlistExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            waitlistExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 购票核心逻辑：
     * Redisson 公平锁 + @Transactional 保证数据一致性
     * MySQL 作为数据权威源，Redis 仅作为缓存，事务提交后才写入
     *
     * @param userId  用户ID
     * @param trainId 车次ID
     * @param count   购买数量
     * @return 结果消息
     */
    @Transactional
    public String buyTicket(Long userId, Long trainId, int count) {
        return ticketPurchaseService.buyTicket(userId, trainId, count);
    }

    /**
     * 退票：恢复余票 + 处理候补队列
     */
    @Transactional
    public String refundTicket(Long userId, String orderNo) {
        TicketOrder order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            return "订单不存在";
        }
        if (!order.getUserId().equals(userId)) {
            return "无权操作此订单";
        }
        if (order.getStatus() != 1) {
            return "订单已退票";
        }

        orderMapper.refund(orderNo);

        // 恢复余票（Redis + MySQL）
        trainService.increaseSeats(order.getTrainId(), order.getSeatCount());

        // 异步处理候补队列（事务提交后执行，避免阻塞退票响应）
        final Long refundTrainId = order.getTrainId();
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    waitlistExecutor.submit(() -> processWaitlist(refundTrainId));
                }
            }
        );

        return "退票成功";
    }

    /**
     * 处理候补队列：按提交时间顺序自动转正
     */
    private void processWaitlist(Long trainId) {
        List<Waitlist> waitlists = waitlistMapper.findByTrainIdOrderByTime(trainId);
        for (Waitlist wl : waitlists) {
            String result = ticketPurchaseService.buyTicket(wl.getUserId(), trainId, wl.getSeatCount());
            if (result.startsWith("购票成功")) {
                waitlistMapper.updateStatus(wl.getId(), 1);
            } else {
                break;
            }
        }
    }

    @Transactional
    public String addWaitlist(Long userId, Long trainId, int count) {
        Train train = trainMapper.findById(trainId);
        if (train == null) {
            return "车次不存在";
        }

        Waitlist wl = new Waitlist();
        wl.setUserId(userId);
        wl.setTrainId(trainId);
        wl.setSeatCount(count);
        wl.setStatus(0);
        waitlistMapper.insert(wl);

        return "候补成功，排队中...";
    }

    public String cancelWaitlist(Long userId, Long waitlistId) {
        Waitlist wl = waitlistMapper.findById(waitlistId);
        if (wl == null || !wl.getUserId().equals(userId)) {
            return "无权操作";
        }
        if (wl.getStatus() != 0) {
            return "候补状态已变更";
        }
        waitlistMapper.cancel(waitlistId);
        return "候补已取消";
    }

    public List<TicketOrder> myOrders(Long userId) {
        return orderMapper.findByUserId(userId);
    }

    public List<Waitlist> myWaitlist(Long userId) {
        return waitlistMapper.findByUserId(userId);
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
