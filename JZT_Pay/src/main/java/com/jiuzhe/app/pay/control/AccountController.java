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
import com.jiuzhe.app.pay.service.AccountService;
import com.jiuzhe.app.pay.utils.Constants;
import com.jiuzhe.app.pay.utils.*;

@RestController
@RequestMapping(value = "/faccount")
public class AccountController {
	private Log logger = LogFactory.getLog(this.getClass());

    @Autowired
    AccountService accountService;

    @RequestMapping(value = "/delsettleaccount", method = RequestMethod.POST)
	@ResponseBody
	public List<String> delsettleaccount(@RequestBody Map param) {
		try {
			if (param == null)
				return Constants.getResult("argsError","param");

			return accountService.delSettleAccount(param);
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/savesettleaccount", method = RequestMethod.POST)
	@ResponseBody
	public List<String> savesettleaccount(@RequestBody Map param) {
		try {
			if (param == null)
				return Constants.getResult("argsError","param");

			return accountService.saveSettleAccount(param);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

    @RequestMapping(value = "/create", method = RequestMethod.POST)
	@ResponseBody
	public List<String> create(@RequestBody Map param) {
		try {
			if (param == null)
				return Constants.getResult("argsError","param");

			return accountService.doCreate(param);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/updatepasswd", method = RequestMethod.POST)
	@ResponseBody
	public List<String> updatePasswd(@RequestBody Map param) {
		try {
			if (param == null)
				return Constants.getResult("argsError","param");

			return accountService.updatePasswd(param);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/getbackpasswd", method = RequestMethod.POST)
	@ResponseBody
	public List<String> getbackpasswd(@RequestBody Map param) {
		try {
			if (param == null)
				return Constants.getResult("argsError","param");

			return accountService.getBackPasswd(param);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/checkpasswd", method = RequestMethod.POST)
	@ResponseBody
	public List<String> checkpasswd(@RequestBody Map param) {
		try {
			if (param == null)
				return Constants.getResult("argsError","param");

			return accountService.checkPasswd(param);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/info/{id}", method = RequestMethod.GET)
	@ResponseBody
	public List<String> getAccountInfo(@PathVariable String id) {
		try {
			if (StringUtil.isEmpty(id))
				return Constants.getResult("argsError","id");
			
			return accountService.getAccountInfo(id);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/getbillinfo/{id}/{page}/{size}", method = RequestMethod.GET)
	@ResponseBody
	public List<String> getBillInfo(@PathVariable String id, @PathVariable int page, @PathVariable int size) {
		try {
			if (StringUtil.isEmpty(id))
				return Constants.getResult("argsError","id");
			
			return accountService.getBillInfo(id, page, size);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/getmwithdraw/{id}/{page}/{size}", method = RequestMethod.GET)
	@ResponseBody
	public List<String> getMerchantWithdraw(@PathVariable String id, @PathVariable int page, @PathVariable int size) {
		try {
			if (StringUtil.isEmpty(id))
				return Constants.getResult("argsError","id");
			
			return accountService.getMerchantWithdraw(id, page, size);

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/getpromotion", method = RequestMethod.GET)
	@ResponseBody
	public List<String> getpromotion() {
		try {
			return accountService.getpromotion();
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/getproductad", method = RequestMethod.GET)
	@ResponseBody
	public List<String> getproductad() {
		try {
			return accountService.getproductad();
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/getproductdetail", method = RequestMethod.GET)
	@ResponseBody
	public List<String> getproductdetail() {
		try {
			return accountService.getproductdetail();
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/getfrozenasset/{id}", method = RequestMethod.GET)
	@ResponseBody
	public List<String> getfrozenasset(@PathVariable String id) {
		try {
			if (StringUtil.isEmpty(id))
				return Constants.getResult("argsError","id");
			
			return accountService.getfrozenasset(id);
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/signin/{userId}", method = RequestMethod.GET)
	@ResponseBody
	public List<String> signin(@PathVariable String userId) {
		try {
			if (StringUtil.isEmpty(userId))
				return Constants.getResult("argsError","userId");
			
			return accountService.signin(userId);
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/signincheck/{userId}", method = RequestMethod.GET)
	@ResponseBody
	public List<String> signincheck(@PathVariable String userId) {
		try {
			if (StringUtil.isEmpty(userId))
				return Constants.getResult("argsError","userId");
			
			return accountService.signincheck(userId);
		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}

	@RequestMapping(value = "/getsettleaccount/{id}/{type}", method = RequestMethod.GET)
	@ResponseBody
	public List<String> getsettleaccount(@PathVariable String id, @PathVariable String type) {
		try {
			if (StringUtil.isEmpty(id))
				return Constants.getResult("argsError","id");

			if (StringUtil.isEmpty(type))
				return Constants.getResult("argsError","type");

			switch (type) {
				case "all":
				case "alipay":
					return accountService.getSettleAccount(id, type);
			}

			return Constants.getResult("getSettleAccountFailed");

		} catch (Exception e) {
			logger.error(e);
			return Constants.getResult("serverException");
		}	
	}
}
