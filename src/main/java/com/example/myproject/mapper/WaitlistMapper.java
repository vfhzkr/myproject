package com.example.myproject.mapper;

import com.example.myproject.entity.Waitlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WaitlistMapper {

    int insert(Waitlist waitlist);

    List<Waitlist> findByUserId(@Param("userId") Long userId);

    List<Waitlist> findByTrainIdOrderByTime(@Param("trainId") Long trainId);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    Waitlist findById(@Param("id") Long id);

    int cancel(@Param("id") Long id);

    int countPendingByUserAndTrain(@Param("userId") Long userId, @Param("trainId") Long trainId);
}
