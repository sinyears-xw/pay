package com.jiuzhe.app.pay.utils;

import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.AlipayClient;

public class AlipayUtil {
	
//↓↓↓↓↓↓↓↓↓↓请在这里配置您的基本信息↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

	// 应用ID,您的APPID，收款账号既是您的APPID对应支付宝账号
	public static String app_id = "2018042702598657";
	
	// 商户私钥，您的PKCS8格式RSA2私钥
    public static String merchant_private_key = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCgmmGFprEGhjCNRdmmGMdWeV8W+aSvZU5vbi8hJ7mfdKo/zyCeUOldzqhx/xADpPjXvK1ZIegBlae5QEK8Vtom2PCYScu9WizppPNFYq6ZSzx5U2vN8OXpc0vZlb1Y9XKJ6dMob+c1DK6V9biunxIPGenZ7QlO+Gd99m0iaiSObNYuBLANnDtjQ9YlM63mTV38TPJVN36DVIbZ45tKVPcStL49kiCbLPyz1jEd36xD4dRoAOBsUOI7ll12uitBEiIfgcmKIaloJ3lzMZjZUc4Suf0Jyx1n5tuyOMbIkiQOha/Puo4dgmSJT23fL01GsGlKWDSsLO5gJPMnwAWDlEpFAgMBAAECggEBAJ+AE0CIByICnS+A4qJ502cHTS/lKPBkvVwfYsEb0dcSJ48Np0hz9QCMIHBurznfWp/vq31tFhyUw6lQr7aazzdrlq64A5b/esFkZ5JOajamKN4ZwmQaTkvmjLsAYrd5tJJRXbu4PzhJaw5VpmAObQIAa2Zaajc6rpXD6ikWn02uKHsWehpCF/uBAN3nO+9w+mFKCqVE4KVAV3FtlbhKiD6hmIx9n1sYZ6UnZmnqOCJEoaSHwMli90oDz/95GXtwNn/XHhkQCroDGufTw1g51U/xiF/HCY/iCKtAEIaILzs9xeprUphVgUnQ6PfxlRARDzpYX0NQ6Lt8OibPRC3K4BUCgYEA07J5Mqx05ONJeRAjctvXSBZ3pmNH+6qNfYbhlKCXVmBQjvbIwrNdhna2shS1135f0AD3tpKNYaaGr5ciL4vMtQGeYm+R+q+ElbanHrAT2VtdHCDtKww+RXSUbwS5OUq53QCWqjpREyZcCUA7UBQBorptkyHZHonEaTceON3uABMCgYEAwjaTkVt1Jrybt7Ycl+KgTiAvN7koZ7gRro0Hw5Q0mtXytRFbfFZ3yLg6XZgyECkvEwSB9WWgDbulQDHxUXAYZzZ66uiN1bDui4LeqKLD6yLIcD3ywoXW+Qum7oetrSlS44tMqmOwlvyz3GgxZOPmefYpW/OWQVMHNJ70IAzfR0cCgYEAvhMCBQCZGCYHAx79ZRSEkCI0Hf4IVKuGzM09X7BtEU37JtHn4gEgGSof0XcM6kZ6io1D3PZq4OGfDqtZM22HSIICjol0fav4FGBiXwhjWgZRbH8X003z33LUD3YjRJCWnxr3LDKeNt57RocYekbsTQqsda2vf17dLkch6JdDKWcCgYB3l2vfQlYteE7bHYUV8jOMaD0rLxU2aR5aQfXZ9VU/UyeVYA7ZzdUNAITQRXvuf63BJDFyjxWz3yyvDxHud/xO8jlWlcVhJQZ/WiMJ7NB/5ndOxpbKOehai1ZL47iKvLuR6qFW+vQuxYVr360q1zuBNpfZS3Hm3928BXWGluaW2QKBgDCfc4m7WcWXAp8ao51wr8CJ6YIBb7goINuz1lYoXgT4iaZAKiYH39XfhA6Op4WUUxlQLXkSpjVYZ1KUfvGBmkjw40Dfm378R4Vh+kWwoJEX4OShyMwl6cYKmCHqnZYuDTDy2XwE7UYoXFrblZAUvCliTmLYa09ST5BHqonh+2ug";
	
    // 商户公钥
	public static String merchant_public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoJphhaaxBoYwjUXZphjHVnlfFvmkr2VOb24vISe5n3SqP88gnlDpXc6ocf8QA6T417ytWSHoAZWnuUBCvFbaJtjwmEnLvVos6aTzRWKumUs8eVNrzfDl6XNL2ZW9WPVyienTKG/nNQyulfW4rp8SDxnp2e0JTvhnffZtImokjmzWLgSwDZw7Y0PWJTOt5k1d/EzyVTd+g1SG2eObSlT3ErS+PZIgmyz8s9YxHd+sQ+HUaADgbFDiO5ZddrorQRIiH4HJiiGpaCd5czGY2VHOErn9CcsdZ+bbsjjGyJIkDoWvz7qOHYJkiU9t3y9NRrBpSlg0rCzuYCTzJ8AFg5RKRQIDAQAB";
	
	// 支付宝公钥,查看地址：https://openhome.alipay.com/platform/keyManage.htm 对应APPID下的支付宝公钥。
    public static String alipay_public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoGseQxjYp0sNeb2tVPmoMWKdU/rGMFXgL9rVL6RRvgs6AizHNyiRF1hFGY0utEE7zTv9VjXH3ReYiPC4LwyRFKYaRi6c1QXGB7zcIOak3+N8iUNI5HRHlBTy3kiIZM5ciW7oaMFep+BVecjV7TYrcftQGyNfQCiGn3f4Cfg+Dt8RTK4acV5YeyeYiIWuzyR0s667SxOAP/zN98MRsfRwkRJ3j29Hg9LXTlNXDMCubxt4u5YVmg88qAoSyEwBpAokvOyFbCgJy6sUhY1lp4fnKPtXdr+gyOBX3rIV5VhNmA/uxvVjTF0yDfc9WuWw+EJJ7OdgJ5/OW/sMrjnnYv9oqwIDAQAB";

	// 服务器异步通知页面路径  需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
	public static String notify_url = "http://47.98.160.254:18080/pay/webhook/alipay";

	public static String notify_url_deposit = "http://47.98.160.254:18080/pay/webhook/alipay/deposit";

	public static String notify_url_charge = "http://47.98.160.254:18080/pay/webhook/alipay/charge";


	// 页面跳转同步通知页面路径 需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
	public static String return_url = "http://47.98.160.254:18080/pay/webhook/alipay";

	// 签名方式
	public static String sign_type = "RSA2";
	
	// 字符编码格式
	public static String charset = "utf-8";
	
	// 支付宝网关
	public static String gatewayUrl = "https://openapi.alipay.com/gateway.do";

	public static String payee_type = "ALIPAY_LOGONID";

	public static String payer_show_name = "内蒙古玖折科技有限公司";

	public static AlipayClient alipayClient = null;

	static {
		alipayClient = new DefaultAlipayClient(gatewayUrl, app_id, merchant_private_key, "json", charset, alipay_public_key, sign_type);
	}

}