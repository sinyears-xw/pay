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


@Service
public class MysqlTransactionServiceImpl implements MysqlTransactionService{

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private StringRedisTemplate rt;

	@Autowired
    RestTemplate restTemplate;

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
		    	num = jdbcTemplate.queryForMap("select count(1) num from deposit where id = " + id);
		    	break;
		    case 2:
		    	num = jdbcTemplate.queryForMap("select count(1) num from transaction where tx_id = " + id);
		    	break;
		    case 3:
		    	num = jdbcTemplate.queryForMap("select count(1) num from withdrawals where id = " + id);
		    	break;
		    case 4:
		    	num = jdbcTemplate.queryForMap("select count(1) num from charges where order_id = '" + id + "'");
		    	break;
		    case 5:
		    	num = jdbcTemplate.queryForMap("select count(1) num from refund where order_id = '" + id + "'");
		    	break;
		}

		if (Integer.parseInt(num.get("num").toString()) <= 0)
			return false;

		return true;
	}

	private String loadDepositRule(String type) {
		try {
			List<Map<String, Object>> rules = jdbcTemplate.queryForList("select * from deposit_rule where type = '" + type + "'");
			ObjectMapper mapper = new ObjectMapper();
			String rulesJson = mapper.writeValueAsString(rules);
			rt.opsForValue().set("deposit_rule" + type, rulesJson);
			return rulesJson;
		} catch(Exception e) {
			logger.error(e);
			return null;
		}
	}

	private List recordDeposit(String from, long amount, String financeType, String depositId) {
		
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
				if (financeType.equals("A"))
					available_amount = amount;
				else
					available_amount = 0;

				break;
			}		
		}

		String sql = null;
		if (financeType.equals("A")) {
			sql = String.format("update account set total_balance = total_balance + %d, available_balance = available_balance + %d, updt = now() where user_id = '%s'", amount, amount, from);
			jdbcTemplate.update(sql);	
		} else {
			sql = String.format("update account set total_balance = total_balance + %d, updt=now() where user_id = '%s'", amount, from);
			jdbcTemplate.update(sql);
		}

		String dataformat = "%Y-%m-%d %H:%i:%s";
		sql = String.format("insert into deposit(id,user_id,amount,available_amount,succeeded,time_succeeded,created,to_pay_dt,installments_num,period,promotions_type,withdraw_interval_type,withdraw_time_limit,discount) values(%d,'%s', %s, %s, 1, now(), now(), date_add(now(), interval %d " + withdraw_interval_type + "),%d,%d,'%s','%s',str_to_date('%s', '%s'),%d)",Long.parseLong(depositId),from,amount,available_amount,period,installments,period,financeType,withdraw_interval_type,withdraw_time_limit,dataformat,discount);
		jdbcTemplate.update(sql);

		return Constants.getResult("depositSucceed");
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
					return Constants.getResult("accountDisabled");
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
			sql = String.format("update deposit set status = 2, updt = now(), available_amount = 0 where id = %d", id);
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
	public List<String> doChargeBalance(Map param) {
		String orderId = param.get("id").toString();
		if(checkId(orderId, 4))
			return Constants.getResult("duplicatedId");

		long amount = Long.parseLong(param.get("amount").toString());
		if (amount <= 0)
			return Constants.getResult("chargeAmountError");

		long depositAmount = Long.parseLong(param.get("deposit_amount").toString());
		if (depositAmount <= 0)
			return Constants.getResult("chargeDepositError");

		String from = param.get("user_from").toString();
		String to = param.get("user_to").toString();
		if (from.equals(to))
			return Constants.getResult("sameAccount",from,to);

		String[] ids = new String[3];
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
				return Constants.getResult("accountSearchError");
			checkrs = new ArrayList<>(Constants.getResult("accountNotFound")); 
			checkrs.add(missed);
			return checkrs;
		}

		//账户校验-支付密码/是否禁用

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
					checkrs = new ArrayList<>(checkrs);  
					checkrs.add(checkAccount.get("user_id").toString());
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
			checkrs = new ArrayList<>(Constants.getResult("balanceInsufficient"));
			checkrs.add(String.valueOf(left_balance));
			return checkrs;
		}

		deductAmount(from,trans_incomes,amount + depositAmount);
		jdbcTemplate.update(String.format("update account set available_balance = available_balance + %d, total_balance = total_balance + %d, trans_incomes = trans_incomes + %d where user_id = '%s'", amount, amount, amount,to));
		jdbcTemplate.update(String.format("update account set available_balance = available_balance + %d, total_balance = total_balance + %d, trans_incomes = trans_incomes + %d where user_id = '%s'", depositAmount, depositAmount, depositAmount,admin));
		
		jdbcTemplate.update(String.format("insert into charges(user_id,order_id,status,amount) values('%s','%s',%d,%d)",from,orderId,1,amount));
		jdbcTemplate.update(String.format("insert into transaction(tx_id,user_from,user_to,amount,type) values(%d,'%s','%s',%d,1)",idWorker.nextId(),from, to, amount));
		jdbcTemplate.update(String.format("insert into transaction(tx_id,user_from,user_to,amount,type) values(%d,'%s','%s',%d,3)",idWorker.nextId(),from, admin, depositAmount));

		Map postData = new HashMap<String,String>();
		postData.put("id",orderId);
		postData.put("status","2");
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
			checkrs = new ArrayList<>(Constants.getResult("accountNotFound")); 
			checkrs.add(missed);
			return checkrs;
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
					checkrs = new ArrayList<>(checkrs);  
					checkrs.add(checkAccount.get("user_id").toString());
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
	public List<String> createWithdraw(Map param) {
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

		Map<String,Object> settleAccountNum = jdbcTemplate.queryForMap(String.format("select count(1) number from settle_account where user_id = '%s' and channel = '%s' and id = %s for update", from, channel, settle_account_id));
		if (Integer.parseInt(settleAccountNum.get("number").toString()) <= 0)
			return Constants.getResult("settleAccountError");

		deductAmount(from,trans_incomes,amount);

		String sql = String.format("insert into withdrawals(id,user_id,fee,settle_account_id,status,amount,description,channel) values(%d,'%s', %s, %s,'created',%d,'%s','%s')",Long.parseLong(withdrawId), from, fee, settle_account_id, amount, description,channel);
		jdbcTemplate.update(sql);

		return Constants.getResult("withdrawApplied");
	}

	@Transactional
	public List<String> doDeposit(Map param) {
		String depositId = param.get("id").toString();
		if(checkId(depositId,1))
			return Constants.getResult("duplicatedId");

		long amount = Long.parseLong(param.get("amount").toString());
		if (amount <= 0)
			return Constants.getResult("depositAmountError");

		String from = param.get("user_from").toString();
		Map userAccount = getUserAccountInfo(from);
		if (userAccount == null)
			return Constants.getResult("accountSearchError");

		List checkrs = checkAccountAvail(userAccount);
		if (!checkrs.get(0).equals("5"))
			return Constants.getResult("accountDisabled");

		String financeType = param.get("finance_type").toString();
		return recordDeposit(from, amount, financeType,depositId);
	}

	@Transactional
	public List<String> doRefund(String orderId, Long amount, String userId) {
		if(checkId(orderId,5))
			return Constants.getResult("duplicatedId");

		Map userAccount = getUserAccountInfo(userId);
		if (userAccount == null)
			return Constants.getResult("accountSearchError");

		List checkrs = checkAccountAvail(userAccount);
		if (!checkrs.get(0).equals("5"))
			return Constants.getResult("accountDisabled");

		jdbcTemplate.update(String.format("update account set available_balance = available_balance + %d, total_balance = total_balance + %d, trans_incomes = trans_incomes + %d where user_id = '%s'", amount, amount, amount,userId));
		jdbcTemplate.update(String.format("update account set available_balance = available_balance - %d, total_balance = total_balance - %d, trans_incomes = trans_incomes - %d where user_id = '%s'", amount, amount, amount,admin));
		jdbcTemplate.update(String.format("insert into refund(order_id,user_id,amount,admin) values('%s','%s',%d,'%s')",orderId,userId,amount,admin));
		jdbcTemplate.update(String.format("insert into transaction(tx_id,user_from,user_to,amount,type) values(%d,'%s','%s',%d,3)",idWorker.nextId(),admin, userId, amount));

		return Constants.getResult("refundSucceed");
	}
}

