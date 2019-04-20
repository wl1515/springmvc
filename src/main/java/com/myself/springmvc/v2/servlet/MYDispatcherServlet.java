package com.myself.springmvc.v2.servlet;

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

public class MYDispatcherServlet extends HttpServlet {

   private Properties contextConfig = new Properties();

   private List<String> classNames = new ArrayList<String>();

   private Map<String,Object> ioc = new HashMap<String,Object>();

   //保存Url和Method的对应关系
   private Map<String,Object> handleMapping = new HashMap<String,Object>();

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
        //绝对路径
        String url = req.getRequestURI();
        //处理成相对路径
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");

        if(!this.handleMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!!!");
            return;
        }

        Method method = (Method) this.handleMapping.get(url);



        Map<String,String[]> params = req.getParameterMap();
        //method.invoke(ioc.get(beanName),new Object[]{req,resp,params.get("name")[0]});
        //获取方法的形参列表
        Class<?> [] parameterTypes = method.getParameterTypes();

        Object [] paramValues = new Object[parameterTypes.length];

        for(int i = 0; i < parameterTypes.length; i ++){
            Class parameterType = parameterTypes[i];
            //不能用instanceof, parameterType它不是实参，而是形参
            if(parameterType == HttpServletRequest.class){
                paramValues[i] = req;
                continue;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] = resp;
                continue;
            }else if(parameterType == String.class){
                Annotation [] [] pa = method.getParameterAnnotations();
                for (int j = 0; j < pa.length ; j ++) {
                    for (Annotation a : pa[j]) {
                        //我们要解析的只是MyRequestParam
                        if(a instanceof MyRequestParam){
                            //拿到参数名称
                            String paramName = ((MyRequestParam) a).value();

                            //从req拿到参数类表中去找对应的key
                            if(params.containsKey(paramName)){
                                for (Map.Entry<String, String[]> param : params.entrySet()) {
                                    //拿到Key所对应的值，而拿到的这个值，有一对多的关系
                                    //一个key对一个数组
                                    System.out.println(Arrays.toString(param.getValue()));

                                    String value = Arrays.toString(param.getValue())
                                            .replaceAll("\\[\\]","")
                                            .replaceAll("\\s",",");
                                    paramValues[i] = value;
                                }
                            }
                        }
                    }
                }
            }
        }
        //投机取巧的方式
        //通过反射拿到method所在class，拿到class之后再拿到class名称
        //再调用toLowerFirstCase获得beanName
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName), paramValues);
    }

    //url传过来的参数是String类型的,http是基于字符串协议
    //只需要把String转换为任意类型就好
    private Object convert(Class<?> type, String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
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
                String url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+","/");
                handleMapping.put(url,method);
                System.out.println("Mapped :" + url + "," + method);
            }
        }
    }
}
