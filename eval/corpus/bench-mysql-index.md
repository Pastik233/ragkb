# MySQL 索引失效的常见场景

联合索引遵循最左前缀原则。索引 (a, b, c) 能支持 a、a+b、a+b+c 三种查询，但单独查 b 用不上。范围查询会截断后面的列：where a=1 and b>2 and c=3 中 c 无法走索引。

在索引列上做函数运算或隐式类型转换会导致失效。比如 where date(created_at) = '2026-01-01'，或者字符串列用数字去比较 where phone = 13800138000，都会退化成全表扫描。正确写法是把函数挪到常量侧，或者用范围条件改写。

以 % 开头的模糊查询 where name like '%abc' 无法使用索引，因为 B+ 树只能做前缀匹配。需要全文检索应该用倒排索引而不是 B+ 树。

or 条件里只要有一侧没有索引，整个查询就可能走全表扫描。可以用 union all 拆开。

不等于、not in、is not null 在区分度低的时候优化器会放弃索引，因为回表成本高于全表扫描。

覆盖索引能避免回表：如果查询的列都在索引里，直接从索引返回。explain 的 Extra 显示 Using index 就是覆盖索引，显示 Using index condition 是索引下推。

判断索引是否生效要看 explain 的 type，从好到差依次是 const、eq_ref、ref、range、index、ALL。出现 ALL 说明全表扫描，需要检查索引设计或统计信息是否过期。
