package com.example.myproject.service;

import com.example.myproject.entity.TicketOrder;
import com.example.myproject.entity.Train;
import com.example.myproject.entity.Waitlist;
import com.example.myproject.mapper.TicketOrderMapper;
import com.example.myproject.mapper.TrainMapper;
import com.example.myproject.mapper.WaitlistMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TicketService {

    private static final String SEATS_PREFIX = "train:seats:";
    private static final String LOCK_PREFIX = "train:lock:";

    @Autowired
    private TrainMapper trainMapper;

    @Autowired
    private TicketOrderMapper orderMapper;

    @Autowired
    private WaitlistMapper waitlistMapper;

    @Autowired
    private TrainService trainService;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 购票核心逻辑：
     * Redisson 公平锁保证排队顺序 + RBucket 直接读写
     * 锁内 check-and-set，代码清晰无 Lua 依赖
     *
     * @param userId  用户ID
     * @param trainId 车次ID
     * @param count   购买数量
     * @return 结果消息
     */
    public String buyTicket(Long userId, Long trainId, int count) {
        // 1. Redisson 公平锁：FIFO 先进先出，避免惊群效应
        RLock lock = redissonClient.getFairLock(LOCK_PREFIX + trainId);
        try {
            // tryLock：最多等 5 秒，锁持有 10 秒自动释放（避免死锁）
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                return "当前抢票人数过多，请稍后再试";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "系统繁忙，请稍后再试";
        }

        try {
            // 2. 检查车次信息
            Train train = trainMapper.findById(trainId);
            if (train == null || train.getStatus() != 1) {
                return "车次不存在或已停售";
            }

            // 3. 从 Redis 读取余票并原子性扣减（锁内操作，线程安全）
            RBucket<Integer> bucket = redissonClient.getBucket(SEATS_PREFIX + trainId);
            Integer available = bucket.get();

            if (available == null) {
                // 缓存失效，从 MySQL 回源
                trainService.refreshCache(trainId);
                available = train.getAvailableSeats();
            }

            if (available < count) {
                return "余票不足，当前仅剩 " + available + " 张";
            }

            // 扣减 Redis 余票
            bucket.set(available - count);

            // 4. 同步更新 MySQL
            int updated = trainMapper.decreaseAvailableSeats(trainId, count);
            if (updated == 0) {
                // MySQL 更新失败，回滚 Redis
                bucket.set(available);
                return "余票不足，购票失败";
            }

            // 5. 创建订单
            TicketOrder order = new TicketOrder();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setTrainId(trainId);
            order.setSeatCount(count);
            order.setTotalPrice(train.getPrice().multiply(BigDecimal.valueOf(count)));
            order.setStatus(1);
            orderMapper.insert(order);

            return "购票成功！订单号：" + order.getOrderNo();

        } finally {
            // 确保锁被释放（Redisson 看门狗也会兜底）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
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

        // 处理候补队列
        processWaitlist(order.getTrainId());

        return "退票成功";
    }

    /**
     * 处理候补队列：按提交时间顺序自动转正
     */
    private void processWaitlist(Long trainId) {
        List<Waitlist> waitlists = waitlistMapper.findByTrainIdOrderByTime(trainId);
        for (Waitlist wl : waitlists) {
            String result = buyTicket(wl.getUserId(), trainId, wl.getSeatCount());
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
