# Spring 循环依赖与三级缓存

循环依赖指 A 依赖 B、B 又依赖 A。构造器注入无法解决，因为创建 A 时必须先拿到 B，创建 B 又必须先拿到 A，直接死锁。字段注入和 setter 注入可以解决，因为对象可以先实例化再填属性。

Spring 用三级缓存处理这个问题。第一级 singletonObjects 存放完全初始化好的单例；第二级 earlySingletonObjects 存放提前暴露的半成品；第三级 singletonFactories 存放生成早期引用的 ObjectFactory。

流程是：创建 A 时先实例化，把 A 的 ObjectFactory 放进三级缓存，然后填充属性发现需要 B；创建 B 时同样先实例化，填充属性时发现需要 A，于是从三级缓存拿到 ObjectFactory 生成早期引用，放入二级缓存并返回；B 初始化完成后进入一级缓存；回到 A，A 拿到 B 完成初始化，也进入一级缓存。

三级缓存的作用是延迟决定要不要生成代理。如果 A 需要 AOP 代理，ObjectFactory 里的 getEarlyBeanReference 会提前生成代理对象，保证 B 注入到的是代理而不是原始对象。如果没有循环依赖，这个工厂永远不会被调用，代理仍然在初始化后由 BeanPostProcessor 正常生成。

二级缓存的作用是避免同一个 Bean 被反复调用工厂。一旦早期引用被创建，就放进二级缓存，后续再有人要就直接取，不会重复生成代理。

`@Lazy` 是另一种解法：在注入点加延迟代理，B 注入的其实是一个代理占位，真正调用方法时才去容器里取 A，从而打断创建时的循环。

Spring Boot 2.6 之后默认禁止循环依赖，需要显式设置 `spring.main.allow-circular-references=true` 才能恢复旧行为。这个默认值的变化是在提醒：循环依赖通常是设计问题，应该拆分层级而不是靠容器兜底。
