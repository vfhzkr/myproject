package com.example.myproject.service;

import com.example.myproject.config.RabbitConfig;
import com.example.myproject.entity.TicketOrder;
import com.example.myproject.entity.Train;
import com.example.myproject.entity.Waitlist;
import com.example.myproject.mapper.TicketOrderMapper;
import com.example.myproject.mapper.TrainMapper;
import com.example.myproject.mapper.WaitlistMapper;
import com.example.myproject.service.TicketPurchaseService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

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

    @Autowired
    private RabbitTemplate rabbitTemplate;

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

        // RabbitMQ 异步处理候补队列（事务提交后发送消息，更可靠）
        final Long refundTrainId = order.getTrainId();
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(
                        RabbitConfig.WAITLIST_EXCHANGE,
                        RabbitConfig.WAITLIST_ROUTING_KEY,
                        refundTrainId
                    );
                }
            }
        );

        return "退票成功";
    }

    @Transactional
    public String addWaitlist(Long userId, Long trainId, int count) {
        // 1. 先尝试直接购买（有票就直接兑现，不用排队）
        String buyResult = ticketPurchaseService.buyTicket(userId, trainId, count);
        if (buyResult.startsWith("购票成功")) {
            return buyResult + "（已自动兑现）";
        }

        // 2. 检查是否已在候补队列中（应用层防重复）
        if (waitlistMapper.countPendingByUserAndTrain(userId, trainId) > 0) {
            return "您已在候补队列中，请勿重复提交";
        }

        // 3. 买不到才进入候补排队
        Train train = trainMapper.findById(trainId);
        if (train == null) {
            return "车次不存在";
        }

        Waitlist wl = new Waitlist();
        wl.setUserId(userId);
        wl.setTrainId(trainId);
        wl.setSeatCount(count);
        wl.setStatus(0);
        try {
            waitlistMapper.insert(wl);
        } catch (Exception e) {
            // 数据库唯一约束兜底：极低概率的并发重复插入
            return "您已在候补队列中，请勿重复提交";
        }

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
