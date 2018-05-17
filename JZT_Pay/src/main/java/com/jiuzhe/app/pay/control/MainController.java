package com.jiuzhe.app.pay.control;

import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
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
import com.jiuzhe.app.pay.service.AlipayService;
import com.jiuzhe.app.pay.service.WXpayService;
import com.jiuzhe.app.pay.utils.Constants;
import com.jiuzhe.app.pay.utils.*;
import com.alipay.api.internal.util.AlipaySignature;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.DocumentHelper;

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
    AlipayService alipayService;

    @Autowired
    WXpayService wxpayService;

    @Autowired
    Ping2PlusService p2pService;

    @Autowired  
    private AmqpTemplate rabbitTemplate;

    @Autowired
    MysqlTransactionService mysqlTx;

    private String getIpAddress(HttpServletRequest request) throws IOException {  
        // 获取请求主机IP地址,如果通过代理进来，则透过防火墙获取真实IP地址  
  
        String ip = request.getHeader("X-Forwarded-For");  
        if (logger.isInfoEnabled()) {  
            logger.info("getIpAddress(HttpServletRequest) - X-Forwarded-For - String ip=" + ip);  
        }  
  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
                ip = request.getHeader("Proxy-Client-IP");  
                if (logger.isInfoEnabled()) {  
                    logger.info("getIpAddress(HttpServletRequest) - Proxy-Client-IP - String ip=" + ip);  
                }  
            }  
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
                ip = request.getHeader("WL-Proxy-Client-IP");  
                if (logger.isInfoEnabled()) {  
                    logger.info("getIpAddress(HttpServletRequest) - WL-Proxy-Client-IP - String ip=" + ip);  
                }  
            }  
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
                ip = request.getHeader("HTTP_CLIENT_IP");  
                if (logger.isInfoEnabled()) {  
                    logger.info("getIpAddress(HttpServletRequest) - HTTP_CLIENT_IP - String ip=" + ip);  
                }  
            }  
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");  
                if (logger.isInfoEnabled()) {  
                    logger.info("getIpAddress(HttpServletRequest) - HTTP_X_FORWARDED_FOR - String ip=" + ip);  
                }  
            }  
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
                ip = request.getRemoteAddr();  
                if (logger.isInfoEnabled()) {  
                    logger.info("getIpAddress(HttpServletRequest) - getRemoteAddr - String ip=" + ip);  
                }  
            }  
        } else if (ip.length() > 15) {  
            String[] ips = ip.split(",");  
            for (int index = 0; index < ips.length; index++) {  
                String strIp = (String) ips[index];  
                if (!("unknown".equalsIgnoreCase(strIp))) {  
                    ip = strIp;  
                    break;  
                }  
            }  
        }  
        return ip;  
    }  

    private Map<String,String> getRequestParam(HttpServletRequest request) {
    	Map<String,String> params = new HashMap<String,String>();
		Map requestParams = request.getParameterMap();
		for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
		    String name = (String) iter.next();
		    String[] values = (String[]) requestParams.get(name);
		    String valueStr = "";
		    for (int i = 0; i < values.length; i++) {
		        valueStr = (i == values.length - 1) ? valueStr + values[i]
		                    : valueStr + values[i] + ",";
		  	}
		    //乱码解决，这段代码在出现乱码时使用。
			//valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
			params.put(name, valueStr);
		}
		return params;
    }

    private Map<String,String> getWeChatPayReturn(HttpServletRequest request) {
    	try {
    		logger.info(getIpAddress(request));
            InputStream inStream = request.getInputStream();
            int _buffer_size = 4096;
            if (inStream != null) {
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                byte[] tempBytes = new byte[_buffer_size];
                int count = -1;
                while ((count = inStream.read(tempBytes, 0, _buffer_size)) != -1) {
                    outStream.write(tempBytes, 0, count);
                }
                tempBytes = null;
                outStream.flush();
                //将流转换成字符串
                String result = new String(outStream.toByteArray(), "UTF-8");
             
                Document doc = DocumentHelper.parseText(result);
                Element root = doc.getRootElement();
                List<Element> elementList = root.elements(); 
            	
            	Map<String, String> map = new HashMap<String, String>();  
               for (Element e : elementList){  
		            map.put(e.getName(), e.getText());  
		        }  
              
                return map;
            }

           return null;
        } catch (Exception e) {
            logger.error(e);
            return null;
        }
     
	}

    @RequestMapping(value = "/webhook/alipay/charge", method = RequestMethod.POST)
	public String aliwebhookcharge(HttpServletRequest request, HttpServletResponse response) {
		try {
			Map<String,String> params = getRequestParam(request);
			if (alipayService.rsaCheck(params)) {
				if (params.get("trade_status").equals("TRADE_SUCCESS")) {
					long amount = Math.round(Double.parseDouble(params.get("total_amount"))*100);
					String out_trade_no = params.get("out_trade_no");
					List<String> rs = mysqlTx.createDepositInCharge(out_trade_no,amount);
					if (!rs.get(0).equals("13")) {
						logger.error("-----------------------------------");
						logger.error("createDepositInCharge Failed:" + rs.get(1));
						logger.error(params);
						return "failed";
					}
					rs = mysqlTx.updateChargeStatus(out_trade_no);
					if (!rs.get(0).equals("19")) {
						logger.error("-----------------------------------");
						logger.error("updateChargeStatus Failed:" + rs.get(1));
						logger.error(params);
					}
				}
				
			} else {
				logger.error("rsaCheck Failed:" + params.toString());
				return "failed";
			}
			return "success";
		} catch (Exception e) {
			logger.error(e);
			return "failed";
		}	
	}

	@RequestMapping(value = "/webhook/alipay/deposit", method = RequestMethod.POST)
	public String aliwebhookdeposit(HttpServletRequest request, HttpServletResponse response) {
		try {
			Map<String,String> params = getRequestParam(request);
			if (alipayService.rsaCheck(params)) {
				if (params.get("trade_status").equals("TRADE_SUCCESS")) {
					long amount = Math.round(Double.parseDouble(params.get("total_amount"))*100);
					List<String> rs = mysqlTx.updateDepositStatus(params.get("out_trade_no"),amount);
					if (!rs.get(0).equals("13")) {
						logger.error("-----------------------------------");
						logger.error("updateDepositStatus Failed:" + rs.get(1));
						logger.error(params);
					}
				}
				
			} else {
				logger.error("rsaCheck Failed:" + params.toString());
			}
			return "success";
		} catch (Exception e) {
			logger.error(e);
			return "failed";
		}	
	}

	@RequestMapping(value = "/webhook/wx/deposit", method = RequestMethod.POST)
	public String wxwebhookdeposit(HttpServletRequest request, HttpServletResponse response) {
		try {
			Map<String,String> params = getWeChatPayReturn(request);
			if (params != null) {
				logger.info(params);

				if (!params.get("return_code").equals("SUCCESS")) {
					logger.error(params.get("return_msg"));
				}

				if (!params.get("result_code").equals("SUCCESS")) {
					logger.error(params.get("err_code_des"));
				}
			
				long amount = Long.parseLong(params.get("cash_fee"));
				List<String> rs = mysqlTx.updateDepositStatus(params.get("out_trade_no"),amount);
				if (!rs.get(0).equals("13")) {
					logger.error("-----------------------------------");
					logger.error("updateDepositStatus Failed:" + rs.get(1));
					logger.error(params);
				}
				
			} else {
				logger.error("params is null");
				return "failed";
			}
			return "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
		} catch (Exception e) {
			logger.error(e);
			return "failed";
		}	
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

			return  mysqlTx.doWithdraw(paramMap);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}
		
	}

	@RequestMapping(value = "/depositwx/{randomKey}", method = RequestMethod.POST)
	@ResponseBody
	public List<String> depositwx(@PathVariable String randomKey, @RequestBody Map param) {
		try {
			Map paramMap = mysqlTx.decodeRequestBody(randomKey, param);
			if (paramMap == null)
				return Constants.getResult("decodeError");

			String body = paramMap.get("body").toString();
			String ip = paramMap.get("ip").toString();

			List<String> checkrs = mysqlTx.doDeposit(paramMap);
			if (checkrs.get(0).equals("13")) {
				return wxpayService.getOrder(checkrs.get(2),Long.parseLong(checkrs.get(4)),body,WXpayUtil.notify_url_deposit,ip);
			}

			return checkrs;
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}
	}

	@RequestMapping(value = "/depositalipay/{randomKey}", method = RequestMethod.POST)
	@ResponseBody
	public List<String> depositalipay(@PathVariable String randomKey, @RequestBody Map param) {
		try {
			Map paramMap = mysqlTx.decodeRequestBody(randomKey, param);
			if (paramMap == null)
				return Constants.getResult("decodeError");

			String body = paramMap.get("body").toString();
			String subject = paramMap.get("subject").toString();

			List<String> checkrs = mysqlTx.doDeposit(paramMap);
			if (checkrs.get(0).equals("13")) {
				return alipayService.getOrder(checkrs.get(2),Double.parseDouble(checkrs.get(3)),body,subject,AlipayUtil.notify_url_deposit);
			}

			return checkrs;
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}
	}

	@RequestMapping(value = "/chargealipay/{randomKey}", method = RequestMethod.POST)
	@ResponseBody
	public List<String> chargealipay(@PathVariable String randomKey, @RequestBody Map param) {
		try {
			Map paramMap = mysqlTx.decodeRequestBody(randomKey, param);
			if (paramMap == null)
				return Constants.getResult("decodeError");

			String body = paramMap.get("body").toString();
			String subject = paramMap.get("subject").toString();
			List<String> checkrs = mysqlTx.doCharge(paramMap);
			if (checkrs.get(0).equals("50")) {
				return alipayService.getOrder(checkrs.get(2),Double.parseDouble(checkrs.get(3)),body,subject,AlipayUtil.notify_url_charge);
			}

			return checkrs;
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
			return mysqlTx.doChargeBalance(paramMap, true);
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

	@RequestMapping(value = "/forbidden/cancelOrder/{orderId}/{amount}/{depositAmount}/{userId}/{merchantId}", method = RequestMethod.GET)
	@ResponseBody
	public List<String> refund(@PathVariable String orderId, @PathVariable long amount,@PathVariable long depositAmount,@PathVariable String userId,@PathVariable String merchantId) {
		try {
			if (amount < 0 || StringUtil.isEmpty(orderId) || StringUtil.isEmpty(userId) || depositAmount < 0 || StringUtil.isEmpty(merchantId))
				return Constants.getResult("argsError");

			return mysqlTx.doCancelOrder(orderId,amount,depositAmount,userId,merchantId);
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
