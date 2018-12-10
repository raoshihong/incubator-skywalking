自定义skywalking插件的步骤
1.添加目标类增强类的定义,必须继承ClassInstanceMethodsEnhancePluginDefine类,在这个定义类中可以定义一下功能
    (1).指明要代理的目标程序的目标类，实现protected ClassMatch enhanceClass() 
    (2).指明要对目标类的目标方法的拦截的定义,实现getInstanceMethodsInterceptPoints或者getConstructorsInterceptPoints,或者getStaticMethodsInterceptPoints
    (3).指明见证类列表,实现witnessClasses,当目标程序有不同版本,不同类时就需要使用这个见证类列表方法来区分相同框架的不同版本
    
2.添加拦截器类
    添加拦截器必须实现InstanceMethodsAroundInterceptor结果
    
3.添加skywalking插件定义文件
    添加skywalking-plugin.def插件定义文件,格式name=插件类全名称
    如：mydemoplugin=com.rao.study.my.plugin.MyDemoInstrumentation
    
    
当在目标程序调用目标类的目标方法时，就会被我们添加的自定义插件的拦截器给拦截住,从而进行相应的代理处理