package com.example.myproject.mapper;

import com.example.myproject.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByUsername(@Param("username") String username);

    User findById(@Param("id") Long id);

    User findByPhone(@Param("phone") String phone);

    User findByEmail(@Param("email") String email);

    int insert(User user);

    void updatePassword(@Param("id") Long id, @Param("password") String password);
}
