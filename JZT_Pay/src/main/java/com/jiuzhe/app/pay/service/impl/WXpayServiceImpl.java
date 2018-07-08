package com.jiuzhe.app.pay.service.impl;

import java.util.*;
import java.io.*;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
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

import com.jiuzhe.app.pay.service.WXpayService;
import org.dom4j.Document;  
import org.dom4j.Element;  
import org.dom4j.io.SAXReader;  

@Service
public class WXpayServiceImpl implements WXpayService {
	private  Log logger = LogFactory.getLog(this.getClass());

	private String GetMapToXML(Map<String,String> param) {  
         StringBuffer sb = new StringBuffer();  
         sb.append("<xml>");  
         for (Map.Entry<String,String> entry : param.entrySet()) {   
                sb.append("<"+ entry.getKey() +">");  
                sb.append(entry.getValue());  
                sb.append("</"+ entry.getKey() +">");  
        }    
         sb.append("</xml>");  
         return sb.toString();  
     }

     private String NonceStr() {
     	try {
	     	String random = Math.random()+"::"+new java.util.Date().toString();
	        String res = Base64.getEncoder().encodeToString(random.getBytes("UTF-8")).substring(0, 30);  
	        return res;
	    }  catch (java.io.UnsupportedEncodingException e) {
	    	logger.error(e);
	    	return null;
	    }
    }

     private String formatUrlMap(Map<String, String> paraMap, boolean urlEncode, boolean keyToLower) {
        String buff = "";
        Map<String, String> tmpMap = paraMap;
        try
        {
            List<Map.Entry<String, String>> infoIds = new ArrayList<Map.Entry<String, String>>(tmpMap.entrySet());
            // 对所有传入参数按照字段名的 ASCII 码从小到大排序（字典序）
            Collections.sort(infoIds, new Comparator<Map.Entry<String, String>>()
            {

                @Override
                public int compare(Map.Entry<String, String> o1, Map.Entry<String, String> o2)
                {
                    return (o1.getKey()).toString().compareTo(o2.getKey());
                }
            });
            // 构造URL 键值对的格式
            StringBuilder buf = new StringBuilder();
            for (Map.Entry<String, String> item : infoIds)
            {
                if (!StringUtil.isEmpty(item.getKey()))
                {
                    String key = item.getKey();
                    String val = item.getValue();
                    if (urlEncode)
                    {
                        val = URLEncoder.encode(val, "utf-8");
                    }
                    if (keyToLower)
                    {
                        buf.append(key.toLowerCase() + "=" + val);
                    } else
                    {
                        buf.append(key + "=" + val);
                    }
                    buf.append("&");
                }

            }
            buff = buf.toString();
            if (buff.isEmpty() == false)
            {
                buff = buff.substring(0, buff.length() - 1);
            }
        } catch (Exception e)
        {
           return null;
        }
        return buff;
    }

    private String GetSign(Map<String,String> param) {  
        String StringA =  formatUrlMap(param, false, false);
        String stringSignTemp = Md5Util.MD5(StringA+"&key="+WXpayUtil.app_secret).toUpperCase(); 
        return stringSignTemp;  
     }

    private Map<String, String> parseXml(String strxml) throws Exception {  
        // 将解析结果存储在HashMap中  
        Map<String, String> map = new HashMap<String, String>();  
        // 从request中取得输入流  
        // 读取输入流  
        SAXReader reader = new SAXReader();  
        Document document = reader.read(new ByteArrayInputStream(strxml.getBytes("UTF-8")));  
        // 得到xml根元素  
        Element root = document.getRootElement();  
        // 得到根元素的所有子节点  
        List<Element> elementList = root.elements();  
        // 遍历所有子节点  
        for (Element e : elementList){  
            map.put(e.getName(), e.getText());  
        }  
        return map;  
    }  

	public List<String> getOrder(String outtradeno, long amount, String body, String notify_url, String ip, boolean credit_forbidden) throws Exception{
		    Map<String,String> param = new HashMap<String,String>();  
        param.put("appid", WXpayUtil.app_id);  
        param.put("mch_id", WXpayUtil.merchant_ID);  
        param.put("nonce_str",NonceStr());  
        param.put("body", body);  
        param.put("out_trade_no",outtradeno);  
        param.put("total_fee", amount+"");  
        param.put("spbill_create_ip", ip);
        param.put("notify_url",notify_url);  
        param.put("trade_type", "APP");
        if (credit_forbidden)
          param.put("limit_pay", "no_credit");
          
        String sign = GetSign(param);
        param.put("sign", sign);
        String xml = GetMapToXML(param);
        String res = httpsRequest(WXpayUtil.url_unifiedorder,"POST",xml);
        Map<String, String> maprs = parseXml(res);
        // logger.info(maprs);

        if (!maprs.get("return_code").equals("SUCCESS"))
        	return Constants.getResult("wxpayError", maprs.get("return_msg"));
        if (!maprs.get("result_code").equals("SUCCESS"))
        	return Constants.getResult("wxpayError", maprs.get("err_code_des"));

        Map<String,String> order = new HashMap<String,String>();
        order.put("appid", WXpayUtil.app_id);
        order.put("partnerid", WXpayUtil.merchant_ID);
        order.put("prepayid", maprs.get("prepay_id"));
        order.put("package", "Sign=WXPay");
        order.put("noncestr", NonceStr());
        order.put("timestamp", String.valueOf(new Date().getTime()/1000));

        String ordersign = GetSign(order);
        order.put("sign", ordersign);
        ObjectMapper mapper = new ObjectMapper();
        String orderstring = mapper.writeValueAsString(order);
        // logger.info(orderstring);

        return Constants.getResult("wxpayPending",orderstring);
	}

	private String httpsRequest(String requestUrl, String requestMethod, String outputStr) {    
      try {    
          URL url = new URL(requestUrl);    
          HttpURLConnection conn = (HttpURLConnection) url.openConnection();    
            
          conn.setDoOutput(true);    
          conn.setDoInput(true);    
          conn.setUseCaches(false);    
          // 设置请求方式（GET/POST）    
          conn.setRequestMethod(requestMethod);    
          conn.setRequestProperty("content-type", "application/x-www-form-urlencoded");    
          // 当outputStr不为null时向输出流写数据    
          if (null != outputStr) {    
              OutputStream outputStream = conn.getOutputStream();    
              // 注意编码格式    
              outputStream.write(outputStr.getBytes("UTF-8"));    
              outputStream.close();    
          }    
          // 从输入流读取返回内容    
          InputStream inputStream = conn.getInputStream();    
          InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "utf-8");    
          BufferedReader bufferedReader = new BufferedReader(inputStreamReader);    
          String str = null;  
          StringBuffer buffer = new StringBuffer();    
          while ((str = bufferedReader.readLine()) != null) {    
              buffer.append(str);    
          }    
          // 释放资源    
          bufferedReader.close();    
          inputStreamReader.close();    
          inputStream.close();    
          inputStream = null;    
          conn.disconnect();    
          return buffer.toString();    
      } catch (Exception e) {    
          logger.error(e);
      }    
      return null;    
    }
}

