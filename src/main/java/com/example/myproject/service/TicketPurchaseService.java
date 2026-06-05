package com.example.myproject.service;

import com.example.myproject.entity.TicketOrder;
import com.example.myproject.entity.Train;
import com.example.myproject.mapper.TicketOrderMapper;
import com.example.myproject.mapper.TrainMapper;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TicketPurchaseService {

    private static final String SEATS_PREFIX = "train:seats:";
    private static final String LOCK_PREFIX = "train:lock:";

    @Autowired
    private TrainMapper trainMapper;

    @Autowired
    private TicketOrderMapper orderMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Transactional
    public String buyTicket(Long userId, Long trainId, int count) {
        // ========== Redis 预检（无锁，快速拒绝） ==========
        // 利用缓存余票提前拦截，减少不必要的锁竞争 + MySQL 读压力
        RBucket<Integer> bucket = redissonClient.getBucket(SEATS_PREFIX + trainId);
        Integer cachedSeats = bucket.get();
        if (cachedSeats != null && cachedSeats < count) {
            return "余票不足，当前仅剩 " + cachedSeats + " 张";
        }

        // ========== FairLock：FIFO 排队，避免惊群效应 ==========
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
                        bucket.set(finalAvailable - count, 1, TimeUnit.HOURS);
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

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
