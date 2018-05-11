package com.jiuzhe.app.pay.service;

import java.util.*;
import javax.servlet.http.HttpServletRequest;

public interface AlipayService {
	public List<String> getOrder(String outtradeno, double amount, String body, String subject, String notify_url);
	public List<String> doTrans(String params);
	public boolean rsaCheck(Map<String,String> params);
}

