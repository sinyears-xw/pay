package com.jiuzhe.app.pay.utils;

import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.Configuration;
import java.net.InetAddress;
import org.apache.commons.codec.binary.Base64;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.io.*;

@Configuration
public class WebHookInterceptor extends HandlerInterceptorAdapter {

	private  Log logger = LogFactory.getLog(this.getClass());

    private static String publicKeyString = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzs8SiPoFQT9K0lWa6WSx" +
"0d6UnA391KM2aFwijY0AK7r+MiAe07ivenopzFL3dqIRhQjuP7d30V85kWydN5UZ" +
"cm/tZgm4K+8wttb988hOrzSjtPOMghHK+bnDwE8FIB+ZbHAZCEVhNfE6i9kLGbHH" +
"Q617+mxUTJ3yEZG9CIgke475o2Blxy4UMsRYjo2gl5aanzmOmoZcbiC/R5hXSQUH" +
"XV9/VzA7U//DIm8Xn7rerd1n8+KWCg4hrIIu/A0FKm8zyS4QwAwQO2wdzGB0h15t" +
"uFLhjVz1W5ZPXjmCRLzTUoAvH12C6YFStvS5kjPcA66P1nSKk5o3koSxOumOs0iC" +
"EQIDAQAB";

    private  boolean verifyData(String dataString, String signatureString, PublicKey publicKey) {
        try {
            byte[] signatureBytes = Base64.decodeBase64(signatureString);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(dataString.getBytes("UTF-8"));
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            logger.error(e);
            return false;
        }
    }

    public PublicKey getPubKey() throws Exception {
        // String pubKeyString = publicKey.replaceAll("(-+BEGIN PUBLIC KEY-+\\r?\\n|-+END PUBLIC KEY-+\\r?\\n?)", "");
        // logger.info(publicKeyString);
        byte[] keyBytes = Base64.decodeBase64(publicKeyString);

        // generate public key
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(spec);
        return publicKey;
    }

    private String getIp(HttpServletRequest request) {
        System.out.println(request.getHeader("content-type"));
        String ip = "";
        try {
            ip = request.getHeader("x-forwarded-for");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            if (ip.equals("0:0:0:0:0:0:0:1")) {
                ip = InetAddress.getLocalHost().getHostAddress().toString();
            }

        } catch (Exception e) {
            logger.error(e);     
        }
        return ip;
    }

	@Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

    	String signature = request.getHeader("X-Pingplusplus-Signature");
        String ip = getIp(request);
        if (signature == null || signature.equals("")) {
            logger.info("illegal ip: " + ip);
            return false;
        }

        // return verifyData(request.getParameterMap().toString(), signature, getPubKey());
        return true;
    }

}