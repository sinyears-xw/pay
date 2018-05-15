package com.jiuzhe.app.pay.service;

import java.util.*;

public interface WXpayService {
	public List<String> getOrder(String outtradeno, long amount, String body, String notify_url, String ip) throws Exception;
}

