package com.example.myproject.mapper;

import com.example.myproject.entity.TicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketOrderMapper {

    int insert(TicketOrder order);

    TicketOrder findByOrderNo(@Param("orderNo") String orderNo);

    TicketOrder findById(@Param("id") Long id);

    List<TicketOrder> findByUserId(@Param("userId") Long userId);

    int refund(@Param("orderNo") String orderNo);

    List<TicketOrder> findByTrainId(@Param("trainId") Long trainId);

    int countByTrainIdAndStatus(@Param("trainId") Long trainId, @Param("status") Integer status);
}
