package com.jiuzhe.app.pay.utils;

public class WXpayUtil {
	public static String app_id = "wxda96cfc4f82c9ce1";
    public static String merchant_ID = "1503730621";
    public static String app_secret = "7d00b513f0284ea1ec0aabbb18a7c7b7";
	public static String notify_url_deposit = "http://47.98.160.254:18080/pay/webhook/wx/deposit";
	public static String notify_url_charge = "http://47.98.160.254:18080/pay/webhook/wx/charge";
	public static String url_unifiedorder = "https://api.mch.weixin.qq.com/pay/unifiedorder";

}