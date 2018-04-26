package com.jiuzhe.app.pay.service;

import java.util.*;
import java.io.*;

public interface AccountService {
	public List<String> doCreate(Map param);
	public List<String> saveSettleAccount(Map param);
	public List<String> updatePasswd(Map param);
	public List<String> getAccountInfo(String id) throws IOException;
	public List<String> getBillInfo(String id, int page, int size) throws IOException;
	public List<String> checkPasswd(Map param);
	public List<String> getpromotion() throws IOException;
}

