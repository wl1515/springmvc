package com.myself.springmvc.v3.servlet;

import com.myself.springmvc.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map.Entry;

public class MYDispatcherServlet extends HttpServlet {

   private Properties contextConfig = new Properties();

   private List<String> classNames = new ArrayList<String>();

   private Map<String,Object> ioc = new HashMap<String,Object>();

   //保存Url和Method的对应关系
   //private Map<String,Object> handleMapping = new HashMap<String,Object>();

    //为什么不适用map
    //handleMapping 本身就包含url与method关系
    //要符合单一原则,最少知道原则
    private List<Handler> handleMapping = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }

    //运行
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        //6.调用
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception{
        
        try {
            Handler handler = getHandler(req);

            if(handler == null){
                //如果没有匹配上，返回404错误
                resp.getWriter().write("404 Not Found");
                return;
            }

            //获取方法的参数列表
            Class<?> [] paramTypes = handler.method.getParameterTypes();

            //保存所有需要自动赋值的参数值
            Object [] paramValues = new Object[paramTypes.length];

            Map<String, String[]> params = req.getParameterMap();
            for (Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
                
                //如果找到匹配对象，则开始填充参数值
                if(!handler.paramIndexMapping.containsKey(param.getKey())){ continue; }
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = covert(paramTypes[index],value);
            }
            //设置方法中的request和response对象
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;

            handler.method.invoke(handler.controller, paramValues);
        }catch (Exception e){
            throw e;
        }
    }

    private Object covert(Class<?> type, String value) {
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }

    private Handler getHandler(HttpServletRequest req) throws Exception{

        if(handleMapping.isEmpty()){ return null; }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");

        for(Handler handler : handleMapping){
            try {
                Matcher matcher = handler.pattern.matcher(url);
                //如果没有匹配上继续下一个匹配
                if(!matcher.matches()){ continue; }
                return handler;
            }catch (Exception e){
                throw  e;
            }
        }
        return null;
    }

    //初始化阶段
    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2.扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        //3.初始化扫描到的类，并且将它们放入到IOC容器之中
        doInstance();

        //4.完成依赖注入
        doAutowired();

        //5.初始化HandlerMapping
        initHandlerMapping();

        System.out.println("My Spring framework is init");
    }

    //加载配置文件
    private void doLoadConfig(String contextConfigLocation) {
        //直接
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
          if(null != inputStream){
              try {
                  inputStream.close();
              } catch (IOException e) {
                  e.printStackTrace();
              }
          }
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.","/"));
        File classPath = new File(url.getFile());
        for(File file : classPath.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            }else{
                if(!file.getName().endsWith(".class")) continue;
                String className = (scanPackage + "." + file.getName().replace(".class", ""));
                classNames.add(className);
            }
        }
    }

    private void doInstance() {
        //初始化，为DI做准备
        if(classNames.isEmpty()) return;

        try{
            for(String classNmae : classNames){
                Class<?> clazz = Class.forName(classNmae);
                //加了注解的类，才初始化
                if(clazz.isAnnotationPresent(MyController.class)) {
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,instance);
                }else if(clazz.isAnnotationPresent(MyService.class)){
                    //1、默认类名首字母小写
                    MyService service = clazz.getAnnotation(MyService.class);
                    String beanName = service.value();
                    //2、自定义的beanName
                    if("".equals(beanName.trim())){
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                    //3、根据类型自动赋值
                    for(Class<?> i : clazz.getInterfaces()){
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("The “" + i.getName() + "” is exists!!!");
                        }
                        ioc.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //暂不考虑类名是小写字母，现方法是自己用
    private String toLowerFirstCase(String simpleName) {
        char [] chars = simpleName.toCharArray();
        /**
         *大小写字母的ASCII码相差32
         * 而且大写字母的ASCII码要小于小写字母的ASCII
         * 在Java中,对char做算学运算，实际上就是对ASCII码做算学运算
         */
        chars[0] += (1<<5);
        return String.valueOf(chars);
    }

    private void doAutowired() {
        if(ioc.isEmpty()) return;

        for (Map.Entry<String, Object> entry : ioc.entrySet()){
            //Declared 所有的，特定的字段，包括private/protected/default
            //正常来说，普通的OOP编程只能拿到public的属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields){
                if(!field.isAnnotationPresent(MyAutowired.class)) continue;
                MyAutowired autowired = field.getAnnotation(MyAutowired.class);

                //如果用户没有自定义beanName, 默认就根据类型注入
                //这个地方省去了对类名首字母小写情况的判断，这个是课后作业
                //自己完善
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }

                //如果是public以外的修饰符，只要加了@Autowired注解，都要强制赋值
                //发射中叫做暴力访问
                field.setAccessible(true);

                try {
                    //用反射机制，动态给字段赋值
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //初始化Url和Method
    private void initHandlerMapping() {
        if(ioc.isEmpty()) return;

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            if(!clazz.isAnnotationPresent(MyController.class)) continue;

            //保存写在类上的URL
            String baseUrl = "";
            if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = requestMapping.value();

            }

            //默认获取所有的public方法
            for (Method method : clazz.getMethods()) {
                if(!method.isAnnotationPresent(MyRequestMapping.class)) continue;

                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                //优化"/"
                String regex = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+","/");
                //handleMapping.put(url,method);
                Pattern pattern = Pattern.compile(regex);
                handleMapping.add(new Handler(pattern,entry.getValue(),method));
                System.out.println("Mapped :" + regex + "," + method);
            }
        }
    }

    /**
     * Handler记录Controller中的RequestMapping和Method的对应关系
     * @author Tom
     * 内部类
     */
    private class Handler{

        protected Object controller; //保存方法对应的实例
        protected Method method;     //保存映射的方法
        protected Pattern pattern;   //采用正则可以扩展url
        protected Map<String, Integer> paramIndexMapping; //参数顺序


        public Handler(Pattern pattern, Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;

            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {

            //提取方法中加了注解的参数
            Annotation [] [] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++){
                for (Annotation a : pa[i]){
                    if(a instanceof MyRequestParam){
                        String paramName = ((MyRequestParam) a).value();
                        if(!"".equals(paramName.trim())){
                            paramIndexMapping.put(paramName,i);
                        }
                    }
                }
            }

            //提取方法中的request和response参数
            Class<?> [] paramsTypes = method.getParameterTypes();
            for(int i = 0; i < paramsTypes.length; i++){
                Class<?> type = paramsTypes[i];
                if(HttpServletRequest.class == type || HttpServletResponse.class == type){
                    paramIndexMapping.put(type.getName(),i);
                }
            }
        }

    }

}
