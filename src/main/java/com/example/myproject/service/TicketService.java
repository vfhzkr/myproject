package com.example.myproject.service;

import com.example.myproject.entity.TicketOrder;
import com.example.myproject.entity.Train;
import com.example.myproject.entity.Waitlist;
import com.example.myproject.mapper.TicketOrderMapper;
import com.example.myproject.mapper.TrainMapper;
import com.example.myproject.mapper.WaitlistMapper;
import jakarta.annotation.PreDestroy;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
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

    // 自注入，使 @Transactional 在 processWaitlist 调用时通过代理生效
    @Autowired
    private TicketService self;

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
        // FairLock：FIFO 排队，避免惊群效应
        RLock lock = redissonClient.getFairLock(LOCK_PREFIX + trainId);
        try {
            if (!lock.tryLock(5, -1, TimeUnit.SECONDS)) {
                return "当前抢票人数过多，请稍后再试";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "系统繁忙，请稍后再试";
        }

        try {
            // 以 MySQL 为准读取余票（锁+事务保护，值始终准确）
            Train train = trainMapper.findById(trainId);
            if (train == null || train.getStatus() != 1) {
                return "车次不存在或已停售";
            }

            int available = train.getAvailableSeats();
            if (available < count) {
                return "余票不足，当前仅剩 " + available + " 张";
            }

            // 扣减 MySQL（带条件 WHERE available_seats >= count）
            int updated = trainMapper.decreaseAvailableSeats(trainId, count);
            if (updated == 0) {
                return "余票不足，购票失败";
            }

            // 创建订单（与扣库存在同一事务，任一失败整体回滚）
            TicketOrder order = new TicketOrder();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setTrainId(trainId);
            order.setSeatCount(count);
            order.setTotalPrice(train.getPrice().multiply(BigDecimal.valueOf(count)));
            order.setStatus(1);
            orderMapper.insert(order);

            // 事务提交后更新 Redis 缓存（避免事务回滚导致 Redis 与 MySQL 不一致）
            final int finalAvailable = available;
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        RBucket<Integer> bucket = redissonClient.getBucket(SEATS_PREFIX + trainId);
                        bucket.set(finalAvailable - count);
                    }
                }
            );

            return "购票成功！订单号：" + order.getOrderNo();

        } finally {
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
            // 通过 self 调用代理方法，@Transactional 生效
            String result = self.buyTicket(wl.getUserId(), trainId, wl.getSeatCount());
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
