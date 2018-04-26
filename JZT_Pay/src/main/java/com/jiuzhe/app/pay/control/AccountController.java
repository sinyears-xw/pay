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
}
