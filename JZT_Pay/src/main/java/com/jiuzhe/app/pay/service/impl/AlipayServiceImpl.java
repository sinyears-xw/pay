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

import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayFundTransToaccountTransferRequest ;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayFundTransToaccountTransferResponse;

import com.jiuzhe.app.pay.service.AlipayService;
import com.alipay.api.internal.util.AlipaySignature;
import javax.servlet.http.HttpServletRequest;


@Service
public class AlipayServiceImpl implements AlipayService {
	private  Log logger = LogFactory.getLog(this.getClass());

	public List<String> getOrder(String outtradeno, double amount, String body, String subject, String notify_url, boolean credit_forbidden) {
		AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
		AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();

		model.setBody(body);
		model.setSubject(subject);
		model.setOutTradeNo(outtradeno);
		model.setTimeoutExpress("30m");
		model.setTotalAmount(String.valueOf(amount));
		model.setProductCode("QUICK_MSECURITY_PAY");
		if (credit_forbidden)
			model.setDisablePayChannels("creditCard,creditCardExpress,creditCardCartoon,credit_group");
		request.setBizModel(model);
		request.setNotifyUrl(notify_url);
		try {
			AlipayTradeAppPayResponse response = AlipayUtil.alipayClient.sdkExecute(request);
			return Constants.getResult("alipayPending",response.getBody()); 
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("alipayError"); 
		}
	}

	public List<String> doTrans(String params) {
		// logger.info(params);
		AlipayFundTransToaccountTransferRequest request = new AlipayFundTransToaccountTransferRequest();
		request.setBizContent(params);
		try {
			AlipayFundTransToaccountTransferResponse response = AlipayUtil.alipayClient.execute(request);
			if(response.isSuccess()){
				return Constants.getResult("alipayTransSucceed"); 
			} else {
				return Constants.getResult("alipayTransFailed",response.getSubMsg()); 
			}
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("alipayError"); 
		}
	}

	public boolean rsaCheck(Map<String,String> params) {
		try {
			boolean flag = AlipaySignature.rsaCheckV1(params, AlipayUtil.alipay_public_key, AlipayUtil.charset,AlipayUtil.sign_type);
			return flag;
		} catch (Exception e) {
			logger.error(e);
			return false;
		}
	}
}

