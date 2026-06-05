package com.example.myproject.mq;

import com.example.myproject.entity.Waitlist;
import com.example.myproject.mapper.WaitlistMapper;
import com.example.myproject.service.TicketPurchaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WaitlistConsumer {

    private static final Logger log = LoggerFactory.getLogger(WaitlistConsumer.class);

    @Autowired
    private WaitlistMapper waitlistMapper;

    @Autowired
    private TicketPurchaseService ticketPurchaseService;

    /**
     * 消费候补转正消息
     * 退票成功后发送 trainId 到此队列，逐条处理候补用户
     * 重试 3 次仍失败则进入死信队列，避免消息丢失
     */
    @RabbitListener(queues = "ticket.waitlist.queue")
    public void handleWaitlist(Long trainId) {
        log.info("候补队列收到消息：车次 {}", trainId);

        List<Waitlist> waitlists = waitlistMapper.findByTrainIdOrderByTime(trainId);
        for (Waitlist wl : waitlists) {
            String result = ticketPurchaseService.buyTicket(wl.getUserId(), trainId, wl.getSeatCount());
            if (result.startsWith("购票成功")) {
                waitlistMapper.updateStatus(wl.getId(), 1);
                log.info("候补转正成功：waitlistId={}, userId={}", wl.getId(), wl.getUserId());
            } else {
                log.info("候补转正失败：waitlistId={}, 原因={}", wl.getId(), result);
                // 余票不足以满足当前候补，后续用户更不可能，直接跳出
                break;
            }
        }
    }
}
