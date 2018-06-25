package com.jiuzhe.app.pay.service;

import java.util.*;

public interface RedisService {
	public List<String> getResult(String redisKey, String sql);
}

