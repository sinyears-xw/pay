package com.jiuzhe.app.pay.service;

import java.util.*;
import com.pingplusplus.model.Charge;

public interface Ping2PlusService {
	public String createCharge(Map chargeMap) throws Exception;
	public String retrieve(String id) throws Exception;
	public String reverse(String id) throws Exception;
}

