package com.example.myproject.mapper;

import com.example.myproject.entity.Train;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TrainMapper {

    List<Train> findAll();

    Train findById(@Param("id") Long id);

    List<Train> search(@Param("departure") String departure, @Param("destination") String destination);

    int updateAvailableSeats(@Param("id") Long id, @Param("count") Integer count);

    int increaseAvailableSeats(@Param("id") Long id, @Param("count") Integer count);

    int decreaseAvailableSeats(@Param("id") Long id, @Param("count") Integer count);
}
