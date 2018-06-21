package com.jiuzhe.app.pay.utils;

import java.text.*;
import java.util.*;

public class DatePriceUtil {
	public static Date getPreDay(Date date) {  
        Calendar calendar = Calendar.getInstance();  
        calendar.setTime(date);  
        calendar.add(Calendar.DAY_OF_MONTH, -1);  
        date = calendar.getTime();  
        return date;  
    } 

	public static List getHolidayPrice(List<Map> prices) {
		try {
			if (prices.size() == 0) 
				return null;

			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
			List<Map> rs = new ArrayList<Map>();
			Map firstDatePrice = prices.get(0);
			String startDate = firstDatePrice.get("date").toString();
			String prePrice = firstDatePrice.get("price").toString();
			Date preDate = formatter.parse(startDate);
			String endDate = "";
			int inSearch = 1;
			
			for (int i = 1; i < prices.size(); i++) {
				Map priceMap = prices.get(i);
				String dateString = priceMap.get("date").toString();
				String price = priceMap.get("price").toString();
				Date date = formatter.parse(dateString);

				if (inSearch == 1) {
					if (preDate.equals(getPreDay(date)) && prePrice.equals(price)) {
						endDate = dateString;
						preDate = date;
					} else {
						inSearch = 0;
					}
				}

				if (inSearch == 0) {
					Map record = new HashMap<String, String>();
					record.put("startDate", startDate);
					record.put("endDate", endDate);
					record.put("price", prePrice);
					rs.add(record);

					startDate = dateString;
					prePrice = price;
					endDate = "";
					preDate = date;
					inSearch = 1;
				}
			}

			if (inSearch == 1) {
				Map record = new HashMap<String, String>();
				record.put("startDate", startDate);
				record.put("endDate", endDate);
				record.put("price", prePrice);
				rs.add(record);
			}

			return rs;
		} catch (ParseException e) {
			System.out.println(e);
			return null;
		}
	}
}
