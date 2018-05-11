package com.jiuzhe.app.pay.service;

import java.util.*;

public interface MysqlTransactionService {
	public Map decodeRequestBody(String hsecret, Map param);
	public List<String> doTrans(Map param);
	public List<String> doChargeBalance(Map param, boolean newOrder);
	public List<String> doWithdraw(Map param);
	public List<String> doDeposit(Map param);
	public List<String> doRefund(String orderId, long amount, String userId);
	public List<String> doCancelOrder(String orderId, long amount, long depositAmount, String userId, String merchantId);
	public List<String> updateDepositStatus(String id, long amount);
	public List<String> updateChargeStatus(String id);
	public List<String> doCharge(Map param);
	public List<String> createDepositInCharge(String id, long amount);
}

