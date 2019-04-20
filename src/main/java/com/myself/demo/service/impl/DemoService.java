package com.myself.demo.service.impl;


import com.myself.demo.service.IDemoService;
import com.myself.springmvc.annotation.MyService;

/**
 * 核心业务逻辑
 */
@MyService
public class DemoService implements IDemoService {

	public String get(String name) {
		return "My name is " + name;
	}

}
