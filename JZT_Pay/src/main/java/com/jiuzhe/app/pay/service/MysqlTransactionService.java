package com.jiuzhe.app.pay.service;

import java.util.*;

public interface MysqlTransactionService {
	public Map decodeRequestBody(String hsecret, Map param);
	public List<String> doTrans(Map param);
	public List<String> doChargeBalance(Map param);
	public List<String> createWithdraw(Map param);
	public List<String> doDeposit(Map param);
	public List<String> doRefund(String orderId, Long amount, String userId, String admin);
}

