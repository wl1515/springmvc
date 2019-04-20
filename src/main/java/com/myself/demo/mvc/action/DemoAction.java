package com.myself.demo.mvc.action;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.myself.demo.service.IDemoService;
import com.myself.springmvc.annotation.MyAutowired;
import com.myself.springmvc.annotation.MyController;
import com.myself.springmvc.annotation.MyRequestMapping;
import com.myself.springmvc.annotation.MyRequestParam;


//虽然，用法一样，但是没有功能
@MyController
@MyRequestMapping("/demo")
public class DemoAction {

  	@MyAutowired
	private IDemoService demoService;

	@MyRequestMapping("/query")
	public void query(HttpServletRequest req, HttpServletResponse resp,
					  @MyRequestParam("name") String name){
//		String result = demoService.get(name);
		String result = "My name is " + name;
		try {
			resp.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@MyRequestMapping("/add")
	public void add(HttpServletRequest req, HttpServletResponse resp,
					@MyRequestParam("a") Integer a, @MyRequestParam("b") Integer b){
		try {
			resp.getWriter().write(a + "+" + b + "=" + (a + b));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@MyRequestMapping("/remove")
	public void remove(HttpServletRequest req,HttpServletResponse resp,
					   @MyRequestParam("id") Integer id){
	}

}
