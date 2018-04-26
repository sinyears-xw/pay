package com.jiuzhe.app.pay.control;

import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.AmqpTemplate;  
import java.util.*;
import java.io.*;
import com.jiuzhe.app.pay.service.Ping2PlusService;
import org.springframework.transaction.PlatformTransactionManager;
import org.codehaus.jackson.map.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import com.jiuzhe.app.pay.service.MysqlTransactionService;
import com.jiuzhe.app.pay.utils.Constants;
import com.jiuzhe.app.pay.utils.*;

@RestController
public class MainController {
	private Log logger = LogFactory.getLog(this.getClass());
	private static IdWorker idWorker = new IdWorker(0, 0);

	@Value("${ping2x.appid}")
	String appId;

	@Value("${ping2x.appkey}")
	String apiKey;

	@Value("${halfsecret}")
	String halfsecret;

    @Autowired
    Ping2PlusService p2pService;

    @Autowired  
    private AmqpTemplate rabbitTemplate;

    @Autowired
    MysqlTransactionService mysqlTx;

    @RequestMapping(value = "/webhook/", method = RequestMethod.POST)
	@ResponseBody
	public String webhook(@RequestBody Map param) {
		String rs = "";
		try {
			rs = param.toString();
		} catch (Exception e) {
			logger.error(e);
		}
		return rs;			
	}

	@RequestMapping(value = "/trans/{randomKey}", method = RequestMethod.POST)
	@ResponseBody
	public List<String> trans(@PathVariable String randomKey, @RequestBody Map param) {
		try {
			Map paramMap = mysqlTx.decodeRequestBody(randomKey, param);
			if (paramMap == null)
				return Constants.getResult("decodeError");

			return mysqlTx.doTrans(paramMap);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/withdraw/{randomKey}", method = RequestMethod.POST)
	@ResponseBody
	public List<String> withdraw(@PathVariable String randomKey, @RequestBody Map param) {
		try {
			Map paramMap = mysqlTx.decodeRequestBody(randomKey, param);
			if (paramMap == null)
				return Constants.getResult("decodeError");

			return mysqlTx.createWithdraw(paramMap);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}
		
	}

	@RequestMapping(value = "/deposit/{randomKey}", method = RequestMethod.POST)
	@ResponseBody
	public List<String> deposit(@PathVariable String randomKey, @RequestBody Map param) {
		try {
			Map paramMap = mysqlTx.decodeRequestBody(randomKey, param);
			if (paramMap == null)
				return Constants.getResult("decodeError");

			return mysqlTx.doDeposit(paramMap);
		
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}
	}

	@RequestMapping(value = "/chargebalance/{randomKey}", method = RequestMethod.POST)
	@ResponseBody
	public List<String> chargebalance(@PathVariable String randomKey, @RequestBody Map param) {
		try {
			Map paramMap = mysqlTx.decodeRequestBody(randomKey, param);
			if (paramMap == null)
				return Constants.getResult("decodeError");
			// String orderId = paramMap.get("id").toString();
			// if (rs.get(0).equals("19")) {
			// 	// rabbitTemplate.convertAndSend("amq.direct","hotelOrder ",orderId + "|Done");
			// 	restTemplate
			// }
			return mysqlTx.doChargeBalance(paramMap);
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}
	}

	@RequestMapping(value = "/forbidden/refund/{orderId}/{amount}/{userId}", method = RequestMethod.GET)
	@ResponseBody
	public List<String> refund(@PathVariable String orderId, @PathVariable long amount, @PathVariable String userId) {
		try {
			if (amount < 0 || StringUtil.isEmpty(orderId) || StringUtil.isEmpty(userId))
				return Constants.getResult("argsError");

			return mysqlTx.doRefund(orderId,amount,userId);
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}
	}
	
	// @RequestMapping(value = "/test/{arg}", method = RequestMethod.GET)
	// public String test(@PathVariable String arg) {
	// 	try {
	// 		if (DataUtil.addDataToHbase("row1", "test", arg))
	// 			return "hbase";
	// 		else {
	// 			logger.error(arg);
	// 		}
	// 		return "log";
	// 	} catch (Exception e) {
	// 		return "failed";
	// 	}
	// }


	private String getStringRandom(int length) {  

        String val = "";  
        Random random = new Random();        
        //length为几位密码 
        for(int i = 0; i < length; i++) {          
            String charOrNum = random.nextInt(2) % 2 == 0 ? "char" : "num";  
            //输出字母还是数字  
            if( "char".equalsIgnoreCase(charOrNum) ) {  
                //输出是大写字母还是小写字母  
                int temp = random.nextInt(2) % 2 == 0 ? 65 : 97;  
                val += (char)(random.nextInt(26) + temp);  
            } else if( "num".equalsIgnoreCase(charOrNum) ) {  
                val += String.valueOf(random.nextInt(10));  
            }  
        }  
        return val;  
    }  

	@RequestMapping(value = "/encode", method = RequestMethod.POST)
	public Map test1(@RequestBody Map param) {
		try {
			String halfkey = getStringRandom(12);
			String key = halfkey + halfsecret;
			String en = DESUtil.encode(key,param.get("data").toString());
			String de = DESUtil.decode(key, en);
			
			Map rs = new HashMap<String, String>();
			rs.put("key", halfkey);
			rs.put("encode", en);
			rs.put("decode", de);
			rs.put("encode-length", en.length());
			return rs;
		} catch (Exception e) {
			return null;
		}
	}

	@RequestMapping(value = "/generateId", method = RequestMethod.GET)
	public List<String> generateId() {
		try {
			return Constants.getResult("IDgenerated",String.valueOf(idWorker.nextId()));
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("IDgeneratedFailed");
		}
	}
}
