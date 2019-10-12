skywalking插件体系定义

1.插件的加载
PluginBootstrap 插件加载器,通过loadPlugins()方法加载所有自定义的plugin,例如dubbo-plugin,spring-plugins
AgentPackagePath 定义资源加载路径
AgentClassLoader自定义类加载器,默认指明加载plugins和activations目录下的插件
PluginResourcesResolver 插件资源加载器与解析期,加载所有的skywalking-plugin.def文件,(自定义skywalking插件的关键部分,表面要自定义skywalking插件的话,首要步骤就是创建一个skywalking-plugin.def文件)
PluginCfg load加载skywalking-plugin.def中的内容
PluginDefine 将skywalking-plugin.def内容中每一行的定义key-value转换为一个PluginDefine对象保存

AbstractClassEnhancePluginDefine核心插件定义类,即skywalking-plugin.def中定义的插件类,通过前面的类加载并反射实例化获得该实例对象
ClassEnhancePluginDefine  AbstractClassEnhancePluginDefine的实现类,添加了对getConstructorsInterceptPoints和getInstanceMethodsInterceptPoints方法的定义,通过enhanceInstance该方法对拦截器的解析，将其添加到AgentBudiler中绑定
ClassInstanceMethodsEnhancePluginDefine ClassEnhancePluginDefine的实现类，添加了getStaticMethodsInterceptPoints的定义

2.插件目标类的匹配
插件的匹配不是对插件进行匹配，而是指插件要代理的哪些类
ProtectiveShieldMatcher
PluginFinder 
ClassMatch 
NameMatch
ElementMatcher
AbstractJunction

3.插件目标类的拦截器定义
ResettableClassFileTransformer子类ExecutingTransformer  通过transform方法对目标类添加代理拦截器

InstanceMethodsInterceptPoint 在这个切入点ElementMatcher和拦截器InstanceMethodsAroundInterceptor

InstanceMethodsAroundInterceptor 定义拦截器方法,插件中定义的拦截器实现这个类即可



