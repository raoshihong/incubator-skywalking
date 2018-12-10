private static ThreadLocal<AbstractTracerContext> CONTEXT = new ThreadLocal<AbstractTracerContext>();

skywalking实现TraceSpan的原理是使用ThreadLocal来承载当前线程内的数据传递