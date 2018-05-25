package com.jiuzhe.app.pay.service.impl;

import java.sql.*;
import java.util.*;
import com.jiuzhe.app.pay.service.AccountService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// import org.codehaus.jackson.map.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.jiuzhe.app.pay.utils.*;
import java.io.*;

@Service
public class AccountServiceImpl implements AccountService{

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private StringRedisTemplate rt;

	private  Log logger = LogFactory.getLog(this.getClass());

	private boolean checkAccount(String id) {
		Map num = jdbcTemplate.queryForMap("select count(1) num from account where user_id = '" + id + "'");
		if (Integer.parseInt(num.get("num").toString()) > 0)
			return true;
		else
			return false;
	}

	private boolean checkSettleAccount(String id, String channel) {
		Map num = jdbcTemplate.queryForMap(String.format("select count(1) num from settle_account where user_id = '%s' and channel = '%s'",id,channel));
		if (Integer.parseInt(num.get("num").toString()) > 0)
			return true;
		else
			return false;
	}

	private boolean checkChannel(String c) {
		String[] channels = new String[]{"alipay","wx","wx_pub","wx_lite","bank_account"};
		for (int i = 0; i < channels.length; i++) {
			if (c.equals(channels[i]))
				return true;
		}
		return false;
	}

	public List<String> checkPasswd(Map param) {
		String userId = param.get("user_id").toString();
		if (StringUtil.isEmpty(userId))
			return Constants.getResult("argsError","user_id");

		Map accountPasswd = null;
		try {
			accountPasswd = jdbcTemplate.queryForMap("select passwd from account where user_id = '" + userId + "' for update");
		} catch (EmptyResultDataAccessException e) {
			return Constants.getResult("accountNotFound", userId);
		}

		String oldPasswd = param.get("old_passwd").toString();
		if (StringUtil.isEmpty(oldPasswd))
			return Constants.getResult("argsError","oldPasswd");

		String storedPasswd = accountPasswd.get("passwd").toString();
		if (!storedPasswd.equals(oldPasswd)) {
			return Constants.getResult("checkPasswdFailed");
		}

		return Constants.getResult("checkPasswdSuccessed");
	}

	@Transactional
	public List<String> delSettleAccount(Map param) {
		String userId = param.get("user_id").toString();
		if (StringUtil.isEmpty(userId))
			return Constants.getResult("argsError","user_id");

		String channel = param.get("channel").toString();
		if (StringUtil.isEmpty(channel) || !checkChannel(channel))
			return Constants.getResult("argsError","channel");


		Object bankCode = param.get("recipient_open_bank_code");
		String openBankCode = "";
		if (bankCode != null) {
			openBankCode = bankCode.toString();
		}

		if (channel.equals("bank_account")) {
			if (openBankCode.equals(""))
				return Constants.getResult("bankcodeError");
			jdbcTemplate.update(String.format("delete from settle_account  where user_id='%s' and channel='bank_account' and recipient_open_bank_code = '%s'",userId,openBankCode));
		} else {
			jdbcTemplate.update(String.format("delete from settle_account  where user_id='%s' and channel='%s'",userId,channel));
		}

		return Constants.getResult("delSettleAccountSucceed");	
	}		

	@Transactional
	public List<String> saveSettleAccount(Map param) {
		String userId = param.get("user_id").toString();
		if (StringUtil.isEmpty(userId))
			return Constants.getResult("argsError","user_id");
		if (!checkAccount(userId))
			return Constants.getResult("accountNotFound",userId);

		String channel = param.get("channel").toString();
		if (StringUtil.isEmpty(channel) || !checkChannel(channel))
			return Constants.getResult("argsError","channel");

		String recipient_account = param.get("recipient_account").toString();
		if (StringUtil.isEmpty(recipient_account))
			return Constants.getResult("argsError","recipient_account");

		String recipient_name = param.get("recipient_name").toString();
		if (StringUtil.isEmpty(recipient_name))
			return Constants.getResult("argsError","recipient_name");

		String recipient_type = param.get("recipient_type").toString();
		if (StringUtil.isEmpty(recipient_type) || !(recipient_type.equals("b2c") || recipient_type.equals("b2b")))
			return Constants.getResult("argsError","recipient_type");

		Object bankCode = param.get("recipient_open_bank_code");
		String openBankCode = "";
		if (bankCode != null)
			openBankCode = bankCode.toString();
	
		if (checkSettleAccount(userId, channel))
			jdbcTemplate.update(String.format("update settle_account set recipient_account='%s',recipient_name='%s',recipient_type='%s',recipient_open_bank_code='%s' where user_id='%s' and channel='%s'", recipient_account,recipient_name,recipient_type,openBankCode,userId,channel));
		else
			jdbcTemplate.update(String.format("insert into settle_account(recipient_account,recipient_name,recipient_type,user_id,channel,recipient_open_bank_code) values('%s','%s','%s','%s','%s','%s')", recipient_account,recipient_name,recipient_type,userId,channel,openBankCode));
		
		return Constants.getResult("settleAccountSaved");	
	}		

	@Transactional
	public List<String> doCreate(Map param) {
		String userId = param.get("user_id").toString();
		if (StringUtil.isEmpty(userId))
			return Constants.getResult("argsError","user_id");

		if (checkAccount(userId))
			return Constants.getResult("accountAlreadyExists", userId);

		String type = param.get("type").toString();
		if ((!type.equals("1")) && (!type.equals("2"))) 
			return Constants.getResult("argsError","type");

		String passwd = param.get("passwd").toString();
		if (StringUtil.isEmpty(passwd))
			return Constants.getResult("argsError","passwd");

		jdbcTemplate.update(String.format("insert into account(user_id,type,passwd) values('%s',%d,'%s')", userId,Integer.parseInt(type),passwd));
		return Constants.getResult("accountCreated");	
	}		

	@Transactional
	public List<String> updatePasswd(Map param) {
		String userId = param.get("user_id").toString();
		if (StringUtil.isEmpty(userId))
			return Constants.getResult("argsError","user_id");

		Map accountPasswd = null;
		try {
			accountPasswd = jdbcTemplate.queryForMap("select passwd from account where user_id = '" + userId + "' for update");
		} catch (EmptyResultDataAccessException e) {
			return Constants.getResult("accountNotFound", userId);
		}

		String oldPasswd = param.get("old_passwd").toString();
		if (StringUtil.isEmpty(oldPasswd))
			return Constants.getResult("argsError","oldPasswd");

		String newPasswd = param.get("new_passwd").toString();
		if (StringUtil.isEmpty(newPasswd))
			return Constants.getResult("argsError","newPasswd");

		String storedPasswd = accountPasswd.get("passwd").toString();
		if (storedPasswd.equals(oldPasswd)) {
			jdbcTemplate.update(String.format("update account set passwd = '%s', updt = now() where user_id = '%s'",newPasswd,userId));
			return Constants.getResult("passwdUpdated");
		}
		
		return Constants.getResult("passwdWrong");
	}

	@Transactional
	public List<String> getBackPasswd(Map param) {
		String userId = param.get("user_id").toString();
		if (StringUtil.isEmpty(userId))
			return Constants.getResult("argsError","user_id");

		String newPasswd = param.get("new_passwd").toString();
		if (StringUtil.isEmpty(newPasswd))
			return Constants.getResult("argsError","newPasswd");

		Map accountPasswd = null;
		try {
			accountPasswd = jdbcTemplate.queryForMap("select passwd from account where user_id = '" + userId + "' for update");
		} catch (EmptyResultDataAccessException e) {
			return Constants.getResult("accountNotFound", userId);
		}

		jdbcTemplate.update(String.format("update account set passwd = '%s', updt = now() where user_id = '%s'",newPasswd,userId));
		return Constants.getResult("passwdUpdated");
	}

	public List<String> getAccountInfo(String id) throws IOException {
		try {
			Map account = jdbcTemplate.queryForMap("select user_id,type,total_balance,disable,withdrawn,available_balance from account where user_id = '" + id + "'");
			ObjectMapper mapper = new ObjectMapper();
			String accountJson =mapper.writeValueAsString(account);
			return Constants.getResult("querySucceed",accountJson);
		} catch (EmptyResultDataAccessException e) {
			return Constants.getResult("accountNotFound",id);
		} 
	}

	public List<String> getBillInfo(String id, int page, int size) throws IOException {
		try {
			String sql = String.format("select * from( select amount,created recordDT, 'withdraw' recordType, status addition, '' transtype from withdrawals where user_id = '%s'\n" +
"union all select amount,finished recordDT,'outflow' recordType,user_to addition, type transtype from transaction where user_from = '%s'\n" +
"union all select amount,finished recordDT,'inflow' recordType,user_from addition, type transtype from transaction where user_to = '%s'\n" +
"union all select amount,time_succeeded recordDT, 'deposit' recordType, promotions_type addition, '' transtype from deposit where user_id = '%s' and succeeded = 1 and promotions_type = 1 \n" +
"union all select amount,return_time recordDT, 'cashback' recordType,deposit_id addition, '' transtype from cashback where user_id = '%s'\n" +
")A order by A.recordDt desc limit %d,%d",id,id,id,id,id,page*size,size);
			List bill = jdbcTemplate.queryForList(sql);
			ObjectMapper mapper = new ObjectMapper();
			String billJson =mapper.writeValueAsString(bill);
			return Constants.getResult("querySucceed",billJson);
		} catch (EmptyResultDataAccessException e) {
			return Constants.getResult("querySucceed");
		}
	}

	public List<String> getpromotion() throws IOException {
		String deposit_rule = rt.opsForValue().get("deposit_rule");
		if (deposit_rule == null) {
			List<Map<String, Object>> rules = jdbcTemplate.queryForList("select * from deposit_rule");
			ObjectMapper mapper = new ObjectMapper();
			String rulesJson = mapper.writeValueAsString(rules);
			rt.opsForValue().set("deposit_rule", rulesJson);
			deposit_rule = rulesJson;
		}
		return Constants.getResult("querySucceed",deposit_rule);
	}

	public List<String> getproductdetail() throws IOException {
		String productDetail = rt.opsForValue().get("productDetail");
		if (productDetail == null) {
			List<Map<String, Object>> details = jdbcTemplate.queryForList("select content from product_detail order by id");
			ObjectMapper mapper = new ObjectMapper();
			String detailsJson = mapper.writeValueAsString(details);
			rt.opsForValue().set("productDetail", detailsJson);
			productDetail = detailsJson;
		}
		return Constants.getResult("querySucceed",productDetail);
	}

	public List<String> signin(String userId, String hotelId) {
		jdbcTemplate.update(String.format("insert into sign_in(user_id,hotel_id,dt) values('%s','%s',now())", userId,hotelId));
		return Constants.getResult("signedIn");
	}

	public List<String> signincheck(String userId) {
		Map rs = jdbcTemplate.queryForMap(String.format("select count(1) num from sign_in where user_id = '%s' and dt > CAST(CAST(SYSDATE()AS DATE)AS DATETIME)", userId));
		if (Integer.parseInt(rs.get("num").toString()) <= 0)
			return Constants.getResult("notSignedIn");

		return Constants.getResult("signedIn");	
	}

	private String getPromotionTitle(List<Map> promotionlist, String type) {
		if (promotionlist == null)
			return "";

		Map promotion = null;
		for (int i = 0; i < promotionlist.size(); i++) {
			promotion = promotionlist.get(i);
			if (promotion.get("type").toString().equals(type))
				return promotion.get("title").toString();
		}

		return "";
	}

	public List<String> getfrozenasset(String id) throws IOException {
		if (!checkAccount(id))
			return Constants.getResult("accountNotFound",id);

		ObjectMapper mapper = new ObjectMapper();
		String sql = String.format("select amount,to_pay_dt,current_installments,installments_num,promotions_type, floor(amount * (100 + discount) /100/installments_num) cashback,  floor(amount * (100 + discount + 10) /100/installments_num) - floor(amount * (100 + discount) /100/installments_num) pluscashback from deposit where user_id = '%s' and promotions_type != 1 and succeeded = 1 order by time_succeeded desc",id);
		List<Map<String, Object>> products = jdbcTemplate.queryForList(sql);
		List<String> rs = getpromotion();
		List<Map> promotionlist = null;
		if (rs.get(0).equals("31")) {
			String promotion = rs.get(2);
			JavaType javaType = mapper.getTypeFactory().constructParametricType(List.class, Map.class); 
			promotionlist = (List<Map>)mapper.readValue(promotion, javaType);
		}

		for (int i = 0; i < products.size(); i++) {
			Map product = products.get(i);
			String type = product.get("promotions_type").toString();
			product.put("title", getPromotionTitle(promotionlist, type));
		}
		
		String productsJson = mapper.writeValueAsString(products);
		return Constants.getResult("querySucceed",productsJson);
	}

	public List<String> getSettleAccount(String id, String type) throws IOException {
		String sql = "";
		if (type.equals("all")) {
			sql = String.format("select * from settle_account where user_id = '%s' ",id);
		} else {
			sql = String.format("select * from settle_account where user_id = '%s' and channel = '%s'",id, type);
		}	
		List<Map<String, Object>> products = jdbcTemplate.queryForList(sql);
		ObjectMapper mapper = new ObjectMapper();
		String productsJson = mapper.writeValueAsString(products);
		return Constants.getResult("querySucceed",productsJson);
	}
}

