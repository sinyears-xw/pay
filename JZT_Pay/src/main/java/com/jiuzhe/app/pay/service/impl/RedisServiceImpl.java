package com.jiuzhe.app.pay.service.impl;

import java.util.*;
import java.io.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import org.codehaus.jackson.map.ObjectMapper;
import com.jiuzhe.app.pay.service.RedisService;
import com.jiuzhe.app.pay.utils.*;

@Service
public class RedisServiceImpl implements RedisService {

  @Autowired
  private StringRedisTemplate rt;

  @Autowired
  private JdbcTemplate jdbcTemplate;

	public List<String> getResult(String redisKey, String sql) {
    String result = null;
    String resultJson = null;
    boolean redisUp = true;
    try {result = rt.opsForValue().get(redisKey);} catch (Exception e) {redisUp = false;}
    if (result == null) {
      List<Map<String, Object>> records = jdbcTemplate.queryForList(sql);
      ObjectMapper mapper = new ObjectMapper();
      try {resultJson = mapper.writeValueAsString(records);} catch (Exception e) {return Constants.getResult("toJsonError");}
      if (redisUp)
        rt.opsForValue().set(redisKey, resultJson);
    } else {
      resultJson = result;
    }
    return Constants.getResult("querySucceed",resultJson);
  }
}
      
           
      

