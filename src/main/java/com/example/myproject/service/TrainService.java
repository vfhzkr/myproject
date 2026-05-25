package com.example.myproject.service;

import com.example.myproject.entity.Train;
import com.example.myproject.mapper.TrainMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TrainService {

    private static final String SEATS_PREFIX = "train:seats:";

    @Autowired
    private TrainMapper trainMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 启动时将所有车次余票加载到Redis
     */
    @PostConstruct
    public void initRedisCache() {
        List<Train> trains = trainMapper.findAll();
        for (Train train : trains) {
            String key = SEATS_PREFIX + train.getId();
            redisTemplate.opsForValue().set(key, train.getAvailableSeats(), 24, TimeUnit.HOURS);
        }
        System.out.println("Redis缓存初始化完成，共加载 " + trains.size() + " 个车次");
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
     */
    public void increaseSeats(Long trainId, int count) {
        trainMapper.increaseAvailableSeats(trainId, count);
        redisTemplate.opsForValue().increment(SEATS_PREFIX + trainId, count);
    }

    /**
     * 刷新Redis缓存
     */
    public void refreshCache(Long trainId) {
        Train train = trainMapper.findById(trainId);
        if (train != null) {
            redisTemplate.opsForValue().set(SEATS_PREFIX + trainId, train.getAvailableSeats(), 24, TimeUnit.HOURS);
        }
    }
}
