package com.jiuzhe.app.pay.service.impl;

import java.sql.*;
import java.util.*;
import com.jiuzhe.app.pay.service.MysqlTransactionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.codehaus.jackson.map.ObjectMapper;
import com.jiuzhe.app.pay.utils.*;
import com.jiuzhe.app.pay.service.AlipayService;


@Service
public class MysqlTransactionServiceImpl implements MysqlTransactionService {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private StringRedisTemplate rt;

	@Autowired
    RestTemplate restTemplate;

    @Autowired
    AlipayService alipayService;

	@Value("${halfsecret}")
	String halfsecret;

	@Value("${adminAccount}")
	String admin;

	private  Log logger = LogFactory.getLog(this.getClass());
	private static IdWorker idWorker = new IdWorker(0, 1);

	/*
	*type
	*1,deposit
	*2,transaction
	*3,withdraw
	*4,charge
	*/
	private boolean checkId(String id, int type) {
		Map num = null;
		switch (type) {
		    case 1:
		    	num = jdbcTemplate.queryForMap(String.format("select count(1) num from deposit where id = %s",id));
		    	break;
		    case 2:
		    	num = jdbcTemplate.queryForMap("select count(1) num from transaction where tx_id = " + id);
		    	break;
		    case 3:
		    	num = jdbcTemplate.queryForMap("select count(1) num from withdrawals where id = " + id);
		    	break;
		    case 4:
		    	num = jdbcTemplate.queryForMap(String.format("select count(1) num from charges where order_id = '%s' and status = 1",id));
		    	break;
		    case 5:
		    	num = jdbcTemplate.queryForMap("select count(1) num from refund where order_id = '" + id + "'");
		    	break;
		     case 6:
		    	num = jdbcTemplate.queryForMap("select count(1) num from cancel_order where order_id = '" + id + "'");
		    	break;
		}

		if (Integer.parseInt(num.get("num").toString()) <= 0)
			return false;

		return true;
	}

	private String loadDepositRule(String type) {
		try {
			List<Map<String, Object>> rules = jdbcTemplate.queryForList("select * from deposit_rule where type = '" + type + "'");
			if (rules == null || rules.size() == 0)
				return null;
			ObjectMapper mapper = new ObjectMapper();
			String rulesJson = mapper.writeValueAsString(rules);
			rt.opsForValue().set("deposit_rule" + type, rulesJson);
			return rulesJson;
		} catch(Exception e) {
			logger.error(e);
			return null;
		}
	}

	private boolean checkNo(String no) {
		no = no.toUpperCase();
		int subNum = 10;
		boolean check = false;
		char areaNum[] = new char[]{'A','B','C','D'};
		int noLen = no.length();
		if (noLen < 2)
			return check;

		char level = no.charAt(noLen - 1);
		if (level < areaNum[0] || level > areaNum[areaNum.length - 1])
			return check;

		if (noLen < ((int)level - (int)areaNum[0] + 2))
			return check;

		try{int levelNo = (int)(no.charAt(noLen - 2));}
		catch(Exception e) {return check;}
		
		return true;
	}

	private List recordDeposit(String from, long amount, String financeType, String depositId, String referee, String referee_phone) {
		String deposit_rule = rt.opsForValue().get("deposit_rule" + financeType);
		List<Map<String, String>> rules = null;
		if (deposit_rule == null) 
			deposit_rule = loadDepositRule(financeType);
		if (deposit_rule == null)
			return Constants.getResult("loadRulesError");

		try{rules = new ObjectMapper().readValue(deposit_rule, List.class);}
		catch(Exception e) {return Constants.getResult("decodeJsonError");}

		long money_min = 0;
		long money_max = 0;
		long discount = 0;
		int period = 0;
		long available_amount = 0;
		int installments = 0;
		String withdraw_time_limit = "";
		String withdraw_interval_type = "";
		Map rule = null;
		int hit = 0;
	
		for (int i = 0; i < rules.size(); i++) {
			rule = rules.get(i);
			money_min = Long.parseLong(rule.get("money_min").toString());
			money_max = Long.parseLong(rule.get("money_max").toString());
			discount = Long.parseLong(rule.get("discount").toString());
			period = Integer.parseInt(rule.get("cashback_period").toString());
			installments = Integer.parseInt(rule.get("installments_num").toString());
			if (rule.get("withdraw_time_limit") != null)
				withdraw_time_limit = rule.get("withdraw_time_limit").toString();
			withdraw_interval_type = rule.get("withdraw_interval_type").toString();

			if (amount >= money_min && amount <= money_max) {
				hit = 1;
				if (financeType.equals("A"))
					available_amount = amount;
				else
					available_amount = 0;

				break;
			}
		}
		if (hit == 0)
			return Constants.getResult("depositAmountError");

		if (financeType.equals("B")) {
			Map fb = null;
			fb = jdbcTemplate.queryForMap(String.format("select count(1) num,sum(amount) amount from deposit where user_id = '%s' and promotions_type = 'B' and status = 'normal'",from));
			if (Integer.parseInt(fb.get("num").toString()) > 0) {
				long fbamount = Long.parseLong(fb.get("amount").toString());
				long left = fbamount + amount - money_max;
				if (left > 0)
					return Constants.getResult("depositOverlimit", String.valueOf(left));
			}
		}

		if (referee.equals(""))
			referee = " ";

		if (referee_phone.equals(""))
			referee_phone = " ";
		else {
			if (!checkNo(referee_phone))
				return Constants.getResult("invitationCodeWrong");
		}
	
		if (!withdraw_time_limit.equals("")) {
			String dataformat = "%Y-%m-%d %H:%i:%s";
			String sql = String.format("insert into deposit(id,user_id,amount,available_amount,created,installments_num,period,promotions_type,withdraw_interval_type,withdraw_time_limit,discount,referee,referee_phone) values(%d,'%s', %d, %d, now(),%d,%d,'%s','%s',str_to_date('%s', '%s'),%d,'%s','%s')",Long.parseLong(depositId),from,amount,available_amount,installments,period,financeType,withdraw_interval_type,withdraw_time_limit,dataformat,discount,referee,referee_phone);
			jdbcTemplate.update(sql);
		} else {
			// logger.info(Long.parseLong(depositId));
			// logger.info(from);
			// logger.info(amount);
			// logger.info(available_amount);
			// logger.info(installments);
			// logger.info(depositId);
			// logger.info(period);
			// logger.info(discount);
			// logger.info(withdraw_interval_type);
			// logger.info(financeType);
			String sql = String.format("insert into deposit(id,user_id,amount,available_amount,created,installments_num,period,promotions_type,withdraw_interval_type,discount,withdraw_time_limit,referee,referee_phone) values(%d,'%s', %d, %d, now(),%d,%d,'%s','%s',%d,date_add(now(), interval 10 YEAR),'%s','%s')",Long.parseLong(depositId),from,amount,available_amount,installments,period,financeType,withdraw_interval_type,discount,referee,referee_phone);

			jdbcTemplate.update(sql);
		}
		double aliamount = ((double)amount) / 100;
		return Constants.getResult("depositSucceed",depositId,String.valueOf(aliamount),String.valueOf(amount));
	}

	private Map getUserAccountInfo(String id) {
		try {
			String sql = "select * from account where user_id = " + "'" + id + "' for update";
			return jdbcTemplate.queryForMap(sql);
		} catch(Exception e) {
			logger.error(e);
			return null;
		}
	}

	private List<Map<String,Object>> getUserAccountInfos(String[] ids) {
		try {
			String sql = "select * from account where user_id in (";
			int i = 0;
			for (; i < ids.length - 1; i++) {
				sql += "'" + ids[i] + "',";
			}
			sql += "'" + ids[i] + "') for update";
			// logger.info(sql);
			return jdbcTemplate.queryForList(sql);
		} catch(Exception e) {
			logger.error(e);
			return null;
		}
	}

	private List<String> checkAccountAll(Map userAccount, Map paramMap) {
		try {
			List rs = checkAccountAvail(userAccount);
			if (!rs.get(0).equals("5"))
				return rs;

			rs = checkAccountPasswd(userAccount, paramMap);
			if (!rs.get(0).equals("5"))
				return rs;
			
			return Constants.getResult("accountChecked");
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("argsError");
		}
	}

	private List<String> checkAccountAvail(Map userAccount) {
		try {
			if (userAccount.get("disable").toString().equals("true"))
					return Constants.getResult("accountDisabled", userAccount.get("user_id").toString());
			return Constants.getResult("accountChecked");
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("argsError");
		}
	}

	private List<String> checkAccountPasswd(Map userAccount, Map paramMap) {
		try {
			if (!userAccount.get("passwd").toString().equals(paramMap.get("passwd").toString()))
					return Constants.getResult("passwdWrong");
			return Constants.getResult("accountChecked");
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("argsError");
		}
	}

	private void deductDepositAmount(String userId, long totalamount) {
		String sql = String.format("select id, available_amount from deposit where user_id = '%s' and available_amount > 0 and succeeded = 1 order by time_succeeded for update",userId);
		List<Map<String,Object>> rs = jdbcTemplate.queryForList(sql);
		Map current = null;
		long amount = 0;
		long id = 0;
		for (int i = 0; i < rs.size(); i++) {
			current = rs.get(i);
			id = Long.parseLong(current.get("id").toString());
			amount = Long.parseLong(current.get("available_amount").toString());
			if (amount - totalamount > 0)
				break;
			sql = String.format("update deposit set updt = now(), available_amount = 0 where id = %d", id);
			jdbcTemplate.update(sql);
			totalamount -= amount;
		}
		sql = String.format("update deposit set updt = now(), available_amount = available_amount - %d where id = %d", totalamount,id);
		jdbcTemplate.update(sql);
	}

	private void deductAmount(String from, long trans_incomes, long amount) {
		long amountAfterTransIncomes = amount - trans_incomes;
		if (amountAfterTransIncomes <= 0) {
			jdbcTemplate.update(String.format("update account set available_balance = available_balance - %d, total_balance = total_balance - %d, trans_incomes = trans_incomes - %d where user_id = '%s'", amount,amount,amount,from));
		} else {
			deductDepositAmount(from, amountAfterTransIncomes);
			jdbcTemplate.update(String.format("update account set available_balance = available_balance - %d,total_balance = total_balance - %d,trans_incomes = 0 where user_id = '%s'", amount,amount,from));
		}
	}

	public Map decodeRequestBody(String randomKey, Map param) {
		try {
			String key = randomKey + halfsecret;
			String encodedData = param.get("data").toString();
			String decodedData = DESUtil.decode(key, encodedData);
			return new ObjectMapper().readValue(decodedData, Map.class);
		} catch (Exception e) {
			logger.error(e);
			return null;
		}
	}

	
	private String getFirstNotExist(String[] ids, List<Map<String,Object>> accounts) {
		Set<String> sets = new HashSet<String>();
		for (int i = 0; i < accounts.size(); i++) {
			sets.add(accounts.get(i).get("user_id").toString());
		}
		// logger.info(sets);
		for (int i = 0; i < ids.length; i++) {
			// logger.info(ids[i]);
			if (!sets.contains(ids[i]))
				return ids[i];
		}
		return null;
	}

	@Transactional
	public List<String> doCharge(Map param) {
		String orderId = param.get("id").toString();
		Map chargeOrder = jdbcTemplate.queryForMap(String.format("select count(1) num, status from charges where order_id = '%s'",orderId));
		Object status = chargeOrder.get("status");
		String statusStr = "";
		int num = Integer.parseInt(chargeOrder.get("num").toString());
		if (status != null)
			statusStr = status.toString();
		if ( num > 0 && statusStr.equals("successed"))
			return Constants.getResult("duplicatedId");

		long amount = Long.parseLong(param.get("amount").toString());
		if (amount <= 0)
			return Constants.getResult("chargeAmountError");

		long depositAmount = Long.parseLong(param.get("deposit_amount").toString());
		if (depositAmount < 0)
			return Constants.getResult("chargeDepositError");

		String from = param.get("user_from").toString();
		String to = param.get("user_to").toString();
		
		if (from.equals(to))
			return Constants.getResult("sameAccount",from,to);

		String[] ids = new String[2];
		ids[0] = from;
		ids[1] = to;
		Map checkAccount = null;
		List<String> checkrs = null;
		Map userAccount = null;

		//获取多个账户信息
		List<Map<String, Object>> userAccounts = getUserAccountInfos(ids);
		if (userAccounts == null)
			return Constants.getResult("accountSearchError");
		//比对获取账户信息个数
		if (userAccounts.size() != 2) {
			String missed = getFirstNotExist(ids, userAccounts);
			if (missed == null)
				return Constants.getResult("accountSearchError", missed);

			return Constants.getResult("accountNotFound", missed); 
		}

		//账户校验-是否禁用

		for (int i = 0; i < userAccounts.size(); i++) {
			checkAccount = userAccounts.get(i);
			checkrs = checkAccountAvail(checkAccount);
			if (!checkrs.get(0).equals("5")) {
				return checkrs;
			}
		}
		if (num == 0)
			jdbcTemplate.update(String.format("insert into charges(user_id,order_id,status,amount,deposit_amount,merchant_id) values('%s','%s',%d,%d,%d,'%s')",from,orderId,3,amount,depositAmount,to));
		
		double aliamount = ((double)(amount + depositAmount)) / 100;
		return Constants.getResult("createPayOrderSucceed",orderId,String.valueOf(aliamount),String.valueOf(amount + depositAmount));
	}

	@Transactional
	public List<String> doChargeBalance(Map param,boolean newOrder) {
		String orderId = param.get("id").toString();
		if(checkId(orderId, 4))
			return Constants.getResult("duplicatedId");

		// long amount = Long.parseLong(param.get("amount").toString());
		// if (amount <= 0)
		// 	return Constants.getResult("chargeAmountError");

		// long depositAmount = Long.parseLong(param.get("deposit_amount").toString());
		// if (depositAmount < 0)
		// 	return Constants.getResult("chargeDepositError");

		Map postDataOrder = new HashMap<String,String>();
		postDataOrder.put("id",orderId);
		Map orderResult = restTemplate.postForObject("http://JZT-HOTEL-CORE/hotelorders/id", postDataOrder,Map.class);

		if (!(orderResult.get("status").toString().equals("0"))) {
			logger.error("orderId:" + orderId);
			logger.error(orderResult);
			return Constants.getResult("getOrderFailed",orderId);
		}

		Map orderData = (Map)orderResult.get("data");
		long amount = Long.parseLong(orderData.get("skuPrice").toString()) * 100;
		long depositAmount = Long.parseLong(orderData.get("skuBond").toString()) * 100;
		long fee = Long.parseLong(orderData.get("platformFee").toString()) * 100;
	
		String from = param.get("user_from").toString();
		String to = param.get("user_to").toString();
		if (from.equals(to))
			return Constants.getResult("sameAccount",from,to);

		String[] ids = new String[2];
		ids[0] = from;
		ids[1] = to;
		Map checkAccount = null;
		List<String> checkrs = null;
		Map userAccount = null;

		//获取多个账户信息
		List<Map<String, Object>> userAccounts = getUserAccountInfos(ids);
		if (userAccounts == null)
			return Constants.getResult("accountSearchError");

		//比对获取账户信息个数
		if (userAccounts.size() != 2) {
			String missed = getFirstNotExist(ids, userAccounts);
			if (missed == null)
				return Constants.getResult("accountSearchError", missed);

			return Constants.getResult("accountNotFound", missed);
		}

		//账户校验-支付密码/是否禁用

		for (int i = 0; i < userAccounts.size(); i++) {
			checkAccount = userAccounts.get(i);
			if (checkAccount.get("user_id").toString().equals(from)) {
				userAccount = userAccounts.get(i);
				if (newOrder) {
					checkrs = checkAccountAll(checkAccount, param);
					if (!checkrs.get(0).equals("5")) 
						return checkrs;
				} else {
					checkrs = checkAccountAvail(checkAccount);
					if (!checkrs.get(0).equals("5")) {
						return checkrs;
					}
				}
			} else {
				checkrs = checkAccountAvail(checkAccount);
				if (!checkrs.get(0).equals("5")) {
					return checkrs;
				}
			}	
		}
		

		long available_balance = Long.parseLong(userAccount.get("available_balance").toString());
		long trans_incomes = Long.parseLong(userAccount.get("trans_incomes").toString());
		if (available_balance < 0 || trans_incomes < 0)
			return Constants.getResult("dataErrorInDB");

		long left_balance = available_balance - amount - depositAmount;
		if (left_balance < 0) {
			return Constants.getResult("balanceInsufficient", String.valueOf(left_balance));
		}

		deductAmount(from,trans_incomes,amount + depositAmount);
		jdbcTemplate.update(String.format("update account set available_balance = available_balance + %d, total_balance = total_balance + %d, trans_incomes = trans_incomes + %d where user_id = '%s'", amount, amount, amount,admin));
		jdbcTemplate.update(String.format("insert into transaction(tx_id,user_from,user_to,amount,type) values(%d,'%s','%s',%d,1)",idWorker.nextId(),from, admin, amount));

		if (newOrder)
			jdbcTemplate.update(String.format("insert into charges(user_id,order_id,status,amount,deposit_amount,merchant_id,fee) values('%s','%s',%d,%d,%d,'%s',%d)",from,orderId,1,amount,depositAmount,to,fee));
		else
			jdbcTemplate.update(String.format("update charges set status = 1, updt = now(), fee = %d where order_id = '%s'",fee,orderId));
		
		if (depositAmount > 0) {
			jdbcTemplate.update(String.format("update account set available_balance = available_balance + %d, total_balance = total_balance + %d, trans_incomes = trans_incomes + %d where user_id = '%s'", depositAmount, depositAmount, depositAmount,admin));
			jdbcTemplate.update(String.format("insert into transaction(tx_id,user_from,user_to,amount,type) values(%d,'%s','%s',%d,3)",idWorker.nextId(),from, admin, depositAmount));
		}

		Map postData = new HashMap<String,String>();
		postData.put("id",orderId);
		postData.put("status","3");
		Map result = restTemplate.postForObject("http://JZT-HOTEL-CORE/hotelorders/orderchange", postData,Map.class);

		if (!(result.get("status").toString().equals("0"))) {
			logger.info("orderId:" + orderId);
			logger.info(result);
			int except = 0;
			except = 1 / 0;
		}
		return Constants.getResult("chargeSucceed");
	}		

	@Transactional
	public List<String> doTrans(Map param) {
		String transId = param.get("id").toString();
		if(checkId(transId,2))
			return Constants.getResult("duplicatedId");

		String from = param.get("user_from").toString();
		String to = param.get("user_to").toString();
		if (from.equals(to))
			return Constants.getResult("sameAccount");
		String[] ids = new String[2];
		ids[0] = from;
		ids[1] = to;
		Map userAccount = null;
		List<String> checkrs = null;
		long amount = Long.parseLong(param.get("amount").toString());
		if (amount <= 0)
			return Constants.getResult("transAmountError");

		//获取多个账户信息
		List<Map<String, Object>> userAccounts = getUserAccountInfos(ids);
		if (userAccounts == null)
			return Constants.getResult("accountSearchError");
		//比对获取账户信息个数
		if (userAccounts.size() != 2) {
			String missed = getFirstNotExist(ids, userAccounts);
			if (missed == null)
				return Constants.getResult("accountSearchError");
			return Constants.getResult("accountNotFound", missed);
		}

		//账户校验-支付密码/是否禁用
		Map checkAccount = null;
		for (int i = 0; i < userAccounts.size(); i++) {
			checkAccount = userAccounts.get(i);
			if (checkAccount.get("user_id").toString().equals(from)) {
				userAccount = userAccounts.get(i);
				checkrs = checkAccountAll(checkAccount, param);
				if (!checkrs.get(0).equals("5")) 
					return checkrs;
			} else {
				checkrs = checkAccountAvail(checkAccount);
				if (!checkrs.get(0).equals("5")) {
					return checkrs;
				}
			}	
		}

		long available_balance = Long.parseLong(userAccount.get("available_balance").toString());
		long trans_incomes = Long.parseLong(userAccount.get("trans_incomes").toString());
		if (available_balance < 0 || trans_incomes < 0)
			return Constants.getResult("dataErrorInDB");

		long left_balance = available_balance - amount;
		if (left_balance < 0)
			return Constants.getResult("transOverlimited");

		deductAmount(from,trans_incomes,amount);

		jdbcTemplate.update(String.format("update account set available_balance = available_balance + %d, total_balance = total_balance + %d, trans_incomes = trans_incomes + %d where user_id = '%s'", amount, amount, amount,to));
		jdbcTemplate.update(String.format("insert into transaction(tx_id,user_from,user_to,amount,type) values(%d,'%s','%s',%d, 4)",Long.parseLong(transId),from, to, amount));
		return Constants.getResult("transSucceed");	
	}

	@Transactional
	public List<String> doWithdraw(Map param) {
		// logger.info(param);
		String withdrawId = param.get("id").toString();
		if(checkId(withdrawId,3))
			return Constants.getResult("duplicatedId");

		long amount = Long.parseLong(param.get("amount").toString());
		if (amount <= 0)
			return Constants.getResult("argsError");

		String from = param.get("user_from").toString();
		String channel = param.get("channel").toString();
		String description = param.get("description").toString();
		String fee = param.get("fee").toString();
		String settle_account_id = param.get("settle_account_id").toString();
		if (fee.equals(""))
			fee = "0";

		Map userAccount = getUserAccountInfo(from);
		if (userAccount == null)
			return Constants.getResult("accountSearchError");

		List<String> checkrs = checkAccountAll(userAccount, param);
		if (!checkrs.get(0).equals("5"))
			return checkrs;

		long available_balance = Long.parseLong(userAccount.get("available_balance").toString());
		long trans_incomes = Long.parseLong(userAccount.get("trans_incomes").toString());
		if (available_balance < 0 || trans_incomes < 0)
			return Constants.getResult("dataErrorInDB");
		if (available_balance < amount)
			return Constants.getResult("withdrawOverlimited");

		Map<String,Object> settleAccountNum = jdbcTemplate.queryForMap(String.format("select count(1) number, recipient_account,recipient_name from settle_account where user_id = '%s' and channel = '%s' and id = %s for update", from, channel, settle_account_id));
		if (Integer.parseInt(settleAccountNum.get("number").toString()) <= 0)
			return Constants.getResult("settleAccountError");

		Object payeeAccountObj = settleAccountNum.get("recipient_account");
		if (payeeAccountObj == null || StringUtil.isEmpty(payeeAccountObj.toString())) 
			return Constants.getResult("settleAccountError");

		Object payeeNameObj = settleAccountNum.get("recipient_name");
		if (payeeNameObj == null || StringUtil.isEmpty(payeeNameObj.toString()))
			return Constants.getResult("settleAccountError");

		int paid = 0;
		List<String> rs = null;
		String aliparamString = "";
		deductAmount(from,trans_incomes,amount);
		switch(channel) {
			case "alipay":
				Map aliparam = new HashMap<String,String>();
				aliparam.put("out_biz_no",withdrawId);
				aliparam.put("payee_type",AlipayUtil.payee_type);
				aliparam.put("payer_show_name",AlipayUtil.payer_show_name);
				aliparam.put("payee_account",payeeAccountObj.toString());
				aliparam.put("payee_real_name",payeeNameObj.toString());
				aliparam.put("amount",String.valueOf(((double)amount/100)));
				aliparam.put("remark",description);

				try {
					ObjectMapper mapper = new ObjectMapper();
					aliparamString = mapper.writeValueAsString(aliparam);
				}  catch (Exception e) {
					logger.error(e);
					return Constants.getResult("argsError");
				}

				rs = alipayService.doTrans(aliparamString);
				if (rs.get(0).equals("43")) {
					paid = 1;
				}
		}

		if (paid == 1) {
			String sql = String.format("insert into withdrawals(id,user_id,fee,settle_account_id,status,amount,description,channel) values(%d,'%s', %s, %s,'succeeded',%d,'%s','%s')",Long.parseLong(withdrawId), from, fee, settle_account_id, amount, description,channel);
			jdbcTemplate.update(sql);
			return Constants.getResult("withdrawSucceed");
		}
		
		String sql = String.format("update account set available_balance = available_balance + %d, updt = now(), total_balance = total_balance + %d, trans_incomes = trans_incomes + %d, withdrawn = withdrawn + %d where user_id='%s'",amount,amount,amount,amount,from);
		jdbcTemplate.update(sql);
		return rs;
	}

	@Transactional
	public List<String> createDepositInCharge(String id, long amount) {
		List<Map<String,Object>> rslist = jdbcTemplate.queryForList(String.format("select status,user_id,deposit_created from charges where order_id = '%s' for update",id));
		if (rslist == null || rslist.size() == 0) {
			return Constants.getResult("chargeOrderNotFound");
		}

		Map<String,Object> rs = rslist.get(0);
		String status = rs.get("status").toString();
		String deposit_created = rs.get("deposit_created").toString();
		if (status.equals("successed") || deposit_created.equals("1"))
			return Constants.getResult("depositSucceed");

		
		String from = rs.get("user_id").toString();
		String depositId = String.valueOf(idWorker.nextId());
		List<String> checkrs =  recordDeposit(from, amount, "A",depositId, "", "");
		if (checkrs.get(0).equals("13")) {
			 checkrs = updateDepositStatus(depositId, amount);
			 if (checkrs.get(0).equals("13")) {
			 	jdbcTemplate.update(String.format("update charges set deposit_created = 1 where order_id = '%s'",id));
			 }
		}
		return checkrs;
	}

	public List<String> updateChargeStatus(String id) {
		List<Map<String,Object>> rslist = jdbcTemplate.queryForList(String.format("select * from charges where order_id = '%s' for update",id));
		if (rslist == null || rslist.size() == 0) {
			return Constants.getResult("chargeOrderNotFound");
		}

		Map<String,Object> rs = rslist.get(0);
		String status = rs.get("status").toString();
		if (status.equals("successed"))
			return Constants.getResult("chargeOrderAlreadyFinished");

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("id", rs.get("order_id"));
		params.put("amount", rs.get("amount"));
		params.put("deposit_amount", rs.get("deposit_amount"));
		params.put("user_from", rs.get("user_id"));
		params.put("user_to", rs.get("merchant_id"));

		return doChargeBalance(params, false);
	}

	@Transactional
	public List<String> updateDepositStatus(String id, long amount) {
		List<Map<String,Object>> rslist = jdbcTemplate.queryForList(String.format("select withdraw_interval_type,period,promotions_type,succeeded,user_id from deposit where id = '%s' for update",id));
		if (rslist == null || rslist.size() == 0) {
			return Constants.getResult("depositOrderNotFound");
		}

		Map<String,Object> rs = rslist.get(0);
		String financeType = rs.get("promotions_type").toString();
		String succeeded = rs.get("succeeded").toString();
		if (succeeded.equals("1"))
			return Constants.getResult("depositOrderAlreadyFinished");
		
		String from = rs.get("user_id").toString();
		String period = rs.get("period").toString();
		String withdraw_interval_type = rs.get("withdraw_interval_type").toString();
		long available_amount = amount;
		String sql = null;
		
		if (financeType.equals("A")) {
			
			sql = String.format("update account set total_balance = total_balance + %d, available_balance = available_balance + %d, updt = now() where user_id = '%s'", amount, amount, from);
			jdbcTemplate.update(sql);	
		} else {
			available_amount = 0;
			sql = String.format("update account set total_balance = total_balance + %d, updt=now() where user_id = '%s'", amount, from);
			jdbcTemplate.update(sql);
		}
		sql = String.format("update deposit set amount = %d,available_amount = %d,succeeded = 1,time_succeeded = date_add(now(), interval -1 second), updt = now(),status = 1, to_pay_dt = date_add(now(), interval %s %s), left_amount = %d where id = %s",amount,available_amount,period,withdraw_interval_type,amount,id);
		
		jdbcTemplate.update(sql);
		return Constants.getResult("depositSucceed");
	}

	@Transactional
	public List<String> doDeposit(Map param) {
		String depositId = param.get("id").toString();
		String financeType = param.get("finance_type").toString();
		String referee = "";
		String referee_phone = "";

		if (param.containsKey("referee")) 
			referee = param.get("referee").toString();

		if (param.containsKey("referee_phone")) 
			referee_phone = param.get("referee_phone").toString();
		
		if(checkId(depositId,1))
			return Constants.getResult("duplicatedId");
	
		long amount = Long.parseLong(param.get("amount").toString());
		if (amount <= 0)
			return Constants.getResult("depositAmountError", String.valueOf(amount));
	
		String from = param.get("user_from").toString();
		Map userAccount = getUserAccountInfo(from);
		if (userAccount == null)
			return Constants.getResult("accountSearchError", from);
	
		List checkrs = checkAccountAvail(userAccount);
		if (!checkrs.get(0).equals("5"))
			return checkrs;

		return recordDeposit(from, amount, financeType, depositId, referee, referee_phone);
	}

	@Transactional
	public List<String> doRefund(String orderId, long amount, long depositAmount, String userId, String merchantId, long fee) {
		if(checkId(orderId,5))
			return Constants.getResult("duplicatedId");

		Map userAccount = getUserAccountInfo(userId);
		if (userAccount == null)
			return Constants.getResult("accountSearchError");

		List checkrs = checkAccountAvail(userAccount);
		if (!checkrs.get(0).equals("5"))
			return checkrs;

		jdbcTemplate.update(String.format("update account set available_balance = available_balance + %d, total_balance = total_balance + %d, trans_incomes = trans_incomes + %d where user_id = '%s'", depositAmount, depositAmount, depositAmount,userId));
		jdbcTemplate.update(String.format("update account set available_balance = available_balance + %d, total_balance = total_balance + %d, trans_incomes = trans_incomes + %d where user_id = '%s'", amount - fee, amount - fee, amount - fee,merchantId));
		jdbcTemplate.update(String.format("update account set available_balance = available_balance - %d, total_balance = total_balance - %d, trans_incomes = trans_incomes - %d where user_id = '%s'", depositAmount + amount - fee, depositAmount + amount - fee, depositAmount + amount - fee,admin));
		jdbcTemplate.update(String.format("insert into refund(order_id,user_id,amount,admin) values('%s','%s',%d,'%s')",orderId,userId,amount,admin));
		jdbcTemplate.update(String.format("insert into transaction(tx_id,user_from,user_to,amount,type) values(%d,'%s','%s',%d,3)",idWorker.nextId(),admin, userId, depositAmount));
		jdbcTemplate.update(String.format("insert into transaction(tx_id,user_from,user_to,amount,type) values(%d,'%s','%s',%d,1)",idWorker.nextId(),admin, merchantId, amount - fee));
		return Constants.getResult("refundSucceed");
	}

	@Transactional
	public List<String> doCancelOrder(String orderId, long amount, long depositAmount, String userId, String merchantId) {
		if(checkId(orderId,6))
			return Constants.getResult("duplicatedId");

		Map userAccount = getUserAccountInfo(userId);
		if (userAccount == null)
			return Constants.getResult("accountSearchError");

		List checkrs = checkAccountAvail(userAccount);
		if (!checkrs.get(0).equals("5"))
			return checkrs;

		jdbcTemplate.update(String.format("update account set available_balance = available_balance + %d, total_balance = total_balance + %d, trans_incomes = trans_incomes + %d where user_id = '%s'", amount+depositAmount, amount+depositAmount, amount+depositAmount,userId));
		jdbcTemplate.update(String.format("update account set available_balance = available_balance - %d, total_balance = total_balance - %d, trans_incomes = trans_incomes - %d where user_id = '%s'", amount+depositAmount, amount+depositAmount, amount+depositAmount,admin));
		jdbcTemplate.update(String.format("insert into cancel_order(order_id,user_id,amount,admin,deposit_amount,merchant_id) values('%s','%s',%d,'%s',%d,'%s')",orderId,userId,amount,admin,depositAmount,merchantId));
		jdbcTemplate.update(String.format("insert into transaction(tx_id,user_from,user_to,amount,type) values(%d,'%s','%s',%d,5)",idWorker.nextId(),admin, userId, amount + depositAmount));
		return Constants.getResult("orderCancelled");
	}
}

