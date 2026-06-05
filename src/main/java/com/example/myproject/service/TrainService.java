package com.example.myproject.service;

import com.example.myproject.entity.Train;
import com.example.myproject.mapper.TrainMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TrainService {

    private static final String SEATS_PREFIX = "train:seats:";

    @Autowired
    private TrainMapper trainMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 启动时将所有车次余票加载到Redis（连接失败不阻塞启动）
     */
    @PostConstruct
    public void initRedisCache() {
        try {
            List<Train> trains = trainMapper.findAll();
            for (Train train : trains) {
                String key = SEATS_PREFIX + train.getId();
                redisTemplate.opsForValue().set(key, train.getAvailableSeats(), 24, TimeUnit.HOURS);
            }
            System.out.println("Redis缓存初始化完成，共加载 " + trains.size() + " 个车次");
        } catch (Exception e) {
            System.err.println("Redis缓存初始化失败（应用将继续运行，降级为直连MySQL）: " + e.getMessage());
        }
    }

    /**
     * 获取所有车次（余票从Redis读取，保证高并发下的实时性）
     */
    public List<Train> findAll() {
        List<Train> trains = trainMapper.findAll();
        for (Train train : trains) {
            String key = SEATS_PREFIX + train.getId();
            Object seats = redisTemplate.opsForValue().get(key);
            if (seats != null) {
                train.setAvailableSeats(((Number) seats).intValue());
            }
        }
        return trains;
    }

    /**
     * 搜索车次
     */
    public List<Train> search(String departure, String destination) {
        List<Train> trains = trainMapper.search(departure, destination);
        for (Train train : trains) {
            String key = SEATS_PREFIX + train.getId();
            Object seats = redisTemplate.opsForValue().get(key);
            if (seats != null) {
                train.setAvailableSeats(((Number) seats).intValue());
            }
        }
        return trains;
    }

    public Train findById(Long id) {
        Train train = trainMapper.findById(id);
        if (train != null) {
            String key = SEATS_PREFIX + train.getId();
            Object seats = redisTemplate.opsForValue().get(key);
            if (seats != null) {
                train.setAvailableSeats(((Number) seats).intValue());
            }
        }
        return train;
    }

    /**
     * 增加余票（退票时调用）
     * 只更新 MySQL，事务提交后：删除旧缓存 → 主动从 MySQL 回源写入 Redis
     * 相比纯 delete 策略，可避免退票后大量查询穿透到 MySQL
     */
    public void increaseSeats(Long trainId, int count) {
        trainMapper.increaseAvailableSeats(trainId, count);
        // 事务提交后：删除旧缓存 + 主动回源写入
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    String key = SEATS_PREFIX + trainId;
                    // 1. 先删除旧缓存（防止并发读拿到旧值）
                    redisTemplate.delete(key);
                    // 2. 主动从 MySQL 读取最新余票写入 Redis（避免缓存穿透）
                    Train train = trainMapper.findById(trainId);
                    if (train != null) {
                        redisTemplate.opsForValue().set(key, train.getAvailableSeats(), 1, TimeUnit.HOURS);
                    }
                }
            }
        );
    }

}
