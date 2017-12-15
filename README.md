# numsg-odata-service

## 1. 简述

numsg-odata-service是基于olingo-4相关API(版本是4.4.0)及spring对OData4（[OData4详见](https://github.com/numsg/numsg-odata/blob/master/docs/OData%20Version%204.0.pdf)）的实现。

## 2. numsg-odata-service调整点

numsg-odata-service针对sg-odata([sg-odata是什么?](https://github.com/numsg/sg-odata))做了一些规范调整及性能优化。

### 2.1 规范调整点如下

1. 工程存放目录移动到 https://github.com/numsg/numsg-odata
2. 包信息调整

```java
<dependency>
  <groupId>com.numsg.odata</groupId>
  <artifactId>numsg-odata-service</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2.2 特性点优化如下

1. numsg-odata-service包装ODdata的服务会将整个数据库暴露出来，但在使用OData开发外部接口时，我们希望有些表及表中字段不在接口里面暴露给调用方，此时我们可以使用numsg-odata-service新特性，使用方法如下，
    1. 将Entity相关工程依赖numsg-odata-service
    ```
    compile  'com.numsg.odata:numsg-odata-service:1.0.0-SNAPSHOT'
    ```
    2. 在不向暴露出来的Entity或字段打标记,将expose属性设置为false
    ```
    @Entity(name = "CAR")
    @ODataEntity(expose = false)
    public class Car {
      //...
    }
    @Column(name = "MODELYEAR")
    @ODataColumn(expose = false)
    private String ModelYear;
    ```
    3. 到此Car这个实体就不会被OData暴露出来
2. 修改集合默认查询，如下URL
```
http://localhost:8080/OdataService.svc/Peoples
```
这个URL原始默认查询会把Peoples实体里面所有记录查询出来，当People表数据量小的时候不会有什么问题，但如果People表的数据达到上千万级别，如果无意中执行了这种查询，对数据库及Java服务性能会有很大的影响。解决方案如下，在numsg-odata-service中对集合的查询做了优化处理，如果一个集合的数据总数>10，则会默认为这个结合查询添加top及skip参数，默认值为
```
if(count > 10){
  typedQuery.setFirstResult(0);
  typedQuery.setMaxResults(10);
}
```
但有人会问，需要读取一个配置表(配置表的数据100条)，该怎么办？是的，同样需要添加$top查询条件，如下
```
http://localhost:8080/OdataService.svc/Peoples?$top=100
```
3. 修改$count=true返回结果，前面对一个集合的查询如果加上$count=true的查询条件，如下URL,
```
http://localhost:8080/OdataService.svc/Peoples?$count=true 
```
返回结果：
```
{
  "@odata.context":"$metadata#PEOPLE/$entity",
  "@odata.count":11,
  "value":[]
}
```
这个返回结果集只会返回这个集合的总数。这样对做前端分页查询是很不合理的，因为这样的返回结果，导致还需要做一次结果集的查询。解决方案如下，添加集合结果集的返回
```
{
  "@odata.context":"$metadata#PEOPLE/$entity",
  "@odata.count":11,
  "value":[
    {
      "PersonId":"p1",
      "PersonName":"zhangsan",
      "gender":"MALE"
    },
    {
      "PersonId":"p2",
      "PersonName":"lisi",
      "gender":"FEMALE"
    },
    {
      "PersonId":"p3",
      "PersonName":"wangwu",
      "gender":"FEMALE"
    }]
}
```
4. 枚举查询，如下实体里面定义了枚举
```
@Entity(name = "PEOPLE")
public class People {
  @Column(name = "P_GENDER", length = 4, nullable = false)
  private Gender  gender;
}
```
以枚举字段Gender作为过滤条件进行查询如下，
```
http://localhost:8080/OdataService.svc/Peoples?$filter=gender eq 'MAIL'&$top=2
```
返回结果
```
{
  "@odata.context":"$metadata#PEOPLE/$entity",
  "value":[
    {
      "PersonId":"p1",
      "PersonName":"zhangsan",
      "gender":"MALE"
    },
    {
      "PersonId":"p4",
      "PersonName":"test1",
      "gender":"MALE"
    }]
}
```

## 3. numsg-odata-service集成

### 3.1 与spring-boot集成

这里主要描述的是numsg-odata-service与spring-boot工程 [odata-springboot](https://github.com/numsg/numsg-odata/samples/odata-springboot)集成

1. 在spring-boot种子工程API子工程及service子工程中添加numsg-odata-service服务jar包引用
```java
compile  'com.numsg.odata:numsg-odata-service:1.0.0-SNAPSHOT'
```

2. 在sg-entity工程中定义不需要暴露的实体或字段
```java
@Entity(name = "CAR")
@ODataEntity(expose = true)
public class Car {
  //...
  @Column(name = "MODELYEAR")
  @ODataColumn(expose = true)
  private String ModelYear;
  //...
}
```

3. 在sg-web工程中添加ODataController,设置好OData对于的RequestMapping
```java
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/OdataService.svc/**")
public class ODataController extends NumsgODataController {
}
```

4. 在sg-web子工程中添加numsg-odata-service jar包的扫描
```java
@SpringBootApplication
@EnableWebMvc
@EnableAutoConfiguration
@EnableConfigurationProperties
@ComponentScan(basePackages = "com.sg.*")
public class OlingoApplication {
    public static void main(String[] args) {
        SpringApplication.run(OlingoApplication.class,args);
    }
}
```
5. OData集成到此结束。

## 4. numsg-odata-service支持

[numsg-odata-service与spring-boot集成Sample](https://github.com/numsg/numsg-odata/samples/odata-springboot)

[numsg-odata-service客户端Sample](https://github.com/numsg/numsg-odata/samples/odata-web-client)

`numsg-odata-service支持:`

```
   http://localhost:8080/OdataService.svc/$metadata
   
   http://localhost:8080/OdataService.svc/Cars
   
   http://localhost:8080/OdataService.svc/Cars?$count=true    //返回记录个数的查询
   
   http://localhost:8080/OdataService.svc/Cars?$expand=Manufacturer,People  //展开导航属性
   
   http://localhost:8080/OdataService.svc/Cars(1)
   
   http://localhost:8080/OdataService.svc/Peoples('002')
   
   http://localhost:8080/OdataService.svc/Cars(1)?$expand=Manufacturer
   
   http://localhost:8080/OdataService.svc/Cars(1)?$expand=Manufacturer($select=Name)
   
   http://localhost:8080/OdataService.svc/Cars(1)/Model    /*单个属性*/
   
   http://localhost:8080/OdataService.svc/Cars(1)/Manufacturer   /*导航属性*/
   
   http://localhost:8080/OdataService.svc/Cars?$expand=Manufacturer&$select=Model,Price  //选择查询某几个字段
   
   http://localhost:8080/OdataService.svc/Cars(1)?$expand=Manufacturer&$select=Model,Price
   
   http://localhost:8080/OdataService.svc/Cars?$count=true&$filter=Model eq 'test'  // string
   
   http://localhost:8080/OdataService.svc/Cars?$filter=Id eq 2  //int
   
   http://localhost:8080/OdataService.svc/Cars?$filter=CreateTime gt '2016-12-05 12:30:12'  // Date  但目前只支持postgresql
   
   http://localhost:8080/OdataService.svc/Cars?$top=1&$skip=1&$orderby=Id desc,Model asc&$count=true  //分页查询
   
   http://localhost:8080/OdataService.svc/Cars?$filter=Model eq 'test'&$top=1&$skip=0&$orderby=Id desc,Model asc&$count=true
   
   http://localhost:8080/OdataService.svc/Peoples  // 支持枚举类型
   
   http://localhost:8080/OdataService.svc/Peoples?$filter=gender eq 0
   
   http://localhost:8080/OdataService.svc/Peoples('002')/Driver
```
