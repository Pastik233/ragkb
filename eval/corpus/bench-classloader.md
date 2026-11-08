# 类加载与双亲委派

类加载分五个阶段：加载、验证、准备、解析、初始化。准备阶段给静态变量分配内存并设零值，真正的赋值在初始化阶段执行 clinit。

双亲委派指收到加载请求时先委派给父加载器，父加载器无法完成才自己加载。三层结构是 Bootstrap、Extension（JDK 9 后改为 Platform）、Application。

它的价值有两层：一是避免类重复加载，同一个类只被加载一次；二是防止核心类被篡改，用户自己写的 java.lang.String 永远不会被加载，因为请求最终会落到 Bootstrap。

破坏双亲委派的典型场景是 JDBC。DriverManager 在 rt.jar 里由 Bootstrap 加载，但它要加载各厂商的 Driver 实现，而那些实现在应用类路径下，父加载器看不见子加载器的类，于是引入线程上下文类加载器，用 Thread.currentThread().getContextClassLoader() 反向委派。

Tomcat 也打破了这套模型，每个 webapp 一个 WebappClassLoader，优先加载自己 WEB-INF/classes 下的类，实现应用隔离。热部署时整个 ClassLoader 被丢弃，类随之卸载。

判断两个类是否相同，要看类加载器加全限定名，两者都相同才算同一个类。所以同名类被不同加载器加载后，互相赋值会抛 ClassCastException。
