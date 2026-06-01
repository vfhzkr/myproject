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
     * 只更新 MySQL，然后删除 Redis 缓存，事务回滚时不会造成不一致
     */
    public void increaseSeats(Long trainId, int count) {
        trainMapper.increaseAvailableSeats(trainId, count);
        // 事务提交后删除缓存，下次读取时从 MySQL 回源
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisTemplate.delete(SEATS_PREFIX + trainId);
                }
            }
        );
    }

}
