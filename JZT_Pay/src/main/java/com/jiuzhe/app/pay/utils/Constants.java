package com.jiuzhe.app.pay.utils;

import java.util.*;

public class Constants {

    public final static Map<String, List<String>> TCONSTANT = new HashMap<String, List<String>>() {
        {
            put("transSucceed", Arrays.asList("1","转账成功"));
            put("accountDisabled", Arrays.asList("2","账户被禁用"));
            put("transOverlimited", Arrays.asList("3","转账金额超过账户可用"));
            put("passwdWrong", Arrays.asList("4","密码错误"));
            put("accountChecked", Arrays.asList("5","账户校验成功"));
            put("serverException", Arrays.asList("6","服务器异常"));
            put("transFailed", Arrays.asList("7","转账失败"));
            put("depositAmountError", Arrays.asList("8","充值金额错误"));
            put("withdrawOverlimited", Arrays.asList("9","金额超过可提现余额"));
            put("withdrawOverlimitedDay", Arrays.asList("10","金额超过当日提现额度"));
            put("withdrawApplied", Arrays.asList("11","提现申请成功"));
            put("settleAccountError", Arrays.asList("12","提现账户错误"));
            put("depositSucceed", Arrays.asList("13","充值成功"));
            put("decodeError", Arrays.asList("14","密文解析出错"));
            put("argsError", Arrays.asList("15","参数异常"));
            put("accountNotFound", Arrays.asList("16","账户不存在"));
            put("accountSearchError", Arrays.asList("17","账户查询异常"));
            put("transAmountError", Arrays.asList("18","转账金额错误"));
            put("chargeSucceed", Arrays.asList("19","支付成功"));
            put("balanceInsufficient", Arrays.asList("20","账户余额不够"));
            put("chargePromotionTypeError", Arrays.asList("21","充值参数错误"));
            put("calcInstallmentsError", Arrays.asList("22","计算分期出错"));
            put("deductAmountSucceed", Arrays.asList("23","扣除余额成功"));
            put("dataErrorInDB", Arrays.asList("24","数据库数据错误"));
            put("duplicatedId", Arrays.asList("25","记录已存在"));
            put("loadRulesError", Arrays.asList("26","载入充值活动错误"));
            put("decodeJsonError", Arrays.asList("27","json解析出错"));
            put("accountAlreadyExists", Arrays.asList("28","账户已存在"));
            put("accountCreated", Arrays.asList("29","账户创建成功"));
            put("passwdUpdated", Arrays.asList("30","修改密码成功"));
            put("querySucceed", Arrays.asList("31","查询成功"));
            put("settleAccountSaved", Arrays.asList("32","结算账户保存成功"));
            put("sameAccount", Arrays.asList("33","转账账户错误"));
            put("chargeAmountError", Arrays.asList("34","支付金额出错"));
            put("chargeDepositError", Arrays.asList("35","压金金额出错"));
            put("refundSucceed", Arrays.asList("36","退压金成功"));
            put("checkPasswdFailed", Arrays.asList("37","支付密码输入错误"));
            put("checkPasswdSuccessed", Arrays.asList("38","支付密码输入正确"));
            put("IDgenerated", Arrays.asList("39","生成ID成功"));
            put("IDgeneratedFailed", Arrays.asList("40","生成ID失败"));
        }
    };

    public static List<String> getResult(String key, String... values) {
        List checkrs = new ArrayList<>(TCONSTANT.get(key));
        for (int i = 0; i < values.length; i++) {
            checkrs.add(values[i]);
        }
        return checkrs;
    }
}  