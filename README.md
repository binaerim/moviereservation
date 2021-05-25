
# 영화표 예매 시스템 (리포트)

# 서비스 시나리오


기능적 요구사항
1. 고객이 예매를 한다.
2. 예매가 되면 고객이 결제를 한다.
3. 결제가 되면 고객이 좌석을 선택한다.
4. 좌석이 선택되면 티켓이 예약된다.
5. 고객이 예매를 취소한다.
6. 예매가 취소되면 결제도 취소된다.
7. 결제가 취소되면 좌석도 취소된다.
8. 좌석이 취소되면 티켓도 취소된다.
9. 고객은 나의페이지를 통해 예매진행상황을 확인할 수 있다.


비기능적 요구사항
1. 트랜잭션
    1. 좌석이 선택되지 않은 예약건은 아예 거래가 성립되지 않아야 한다. Sync 호출
    2. 티켓이 취소되야 결제가 취소된다. Sync 호출
1. 장애격리
    1. 티켓관리 기능이 수행되지 않더라도 예매는 365일 24시간 받을 수 있어야 한다  Async (event-driven), Eventual Consistency
    1. 결제시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도한다  Circuit breaker, fallback
1. 성능
    1. 고객이 자주 예매상태를 마이페이지(프론트엔드)에서 확인할 수 있어야 한다  CQRS
    1. 예매상태가 바뀔때마다 카톡 등으로 알림을 줄 수 있어야 한다  Event driven



# 분석/설계

## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과:  http://www.msaez.io/#/storming/WpY4xioLpHPrZpRPBibQCIH6sv53/share/3949b62d564a5706174fe3b45a7ba4b4
![image](https://user-images.githubusercontent.com/30439935/119307210-4c8f8800-bca6-11eb-920c-9e78ad57ba27.png)




# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n 이다)

```
cd reservation
mvn spring-boot:run

cd payment
mvn spring-boot:run 

cd seatmanagement
mvn spring-boot:run  

cd ticketmanagement
mvn spring-boot:run  

cd mypage
python policy-handler.py

```

## DDD 의 적용

```
# reservation 서비스의 예매처리
http POST http://acde84ae9f71a41a5962df4b3fbe9e34-1349237753.ap-southeast-1.elb.amazonaws.com/reservations customerName="홍길동" movieName="사탄의인형3" reservationStatus="예매완료"

# payment 서비스의 결제확인
http GET http://acde84ae9f71a41a5962df4b3fbe9e34-1349237753.ap-southeast-1.elb.amazonaws.com/payments

```


## 폴리글랏 프로그래밍, 퍼시스턴스

```
from flask import Flask
from redis import Redis, RedisError
from kafka import KafkaConsumer
import os
import socket


# To consume latest messages and auto-commit offsets
consumer = KafkaConsumer('reservation',
                         group_id='',
                         bootstrap_servers=['localhost:9092'])
for message in consumer:
    print ("%s:%d:%d: key=%s value=%s" % (message.topic, message.partition,
                                          message.offset, message.key,
                                          message.value))

```

파이선 애플리케이션을 컴파일하고 실행하기 위한 도커파일은 아래와 같다 (운영단계에서 할일인가? 아니다 여기 까지가 개발자가 할일이다. Immutable Image):
```
FROM python:2.7-slim
WORKDIR /app
ADD . /app
RUN pip install --trusted-host pypi.python.org -r requirements.txt
ENV NAME World
EXPOSE 8090
CMD ["python", "policy-handler.py"]
```


## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 좌석이 선택되지 않은 예약건은 아예 거래가 성립되지 않아야 한다. 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 
호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

```
# SeatService.java

@FeignClient(name = "seatmanagement", url = "http://seatmanagement:8080")
public interface SeatService {
    /// reserveSeat
    @RequestMapping(method = RequestMethod.POST, path = "/reserveSeat")
    public void reserveSeat(@RequestBody Seat seat);
    
    
}
```

- 좌석예약을 받은 직후(@PostPersist) 결제를 요청하도록 처리
```
# Payment.java (Entity)

    @PostPersist
    public void onPostPersist() {
        Approved approved = new Approved();
        BeanUtils.copyProperties(this, approved);
        approved.setStatus("approved");
        approved.publishAfterCommit();

        // Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        moviereservation.external.Seat seat = new moviereservation.external.Seat();
        // mappings goes here
        seat.setReservationId(this.reservationId);
        seat.setSeatQty(1L);
        PaymentApplication.applicationContext.getBean(moviereservation.external.SeatService.class).reserveSeat(seat);

    }
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인:


```
# 좌석관리 (seatmanagement) 서비스를 잠시 내려놓음 (ctrl+c)

#주문처리
http POST http://acde84ae9f71a41a5962df4b3fbe9e34-1349237753.ap-southeast-1.elb.amazonaws.com/reservations movieName=겨울왕국 customerName=문상우   #Fail
http POST http://acde84ae9f71a41a5962df4b3fbe9e34-1349237753.ap-southeast-1.elb.amazonaws.com/reservations movieName=어벤져스 customerName=로다주   #Fail

#결제서비스 재기동
seatmanagement deploy 재배포

#주문처리
http POST http://acde84ae9f71a41a5962df4b3fbe9e34-1349237753.ap-southeast-1.elb.amazonaws.com/reservations movieName=겨울왕국 customerName=문상우   #Success
http POST http://acde84ae9f71a41a5962df4b3fbe9e34-1349237753.ap-southeast-1.elb.amazonaws.com/reservations movieName=어벤져스 customerName=로다주   #Success
```

- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, 폴백 처리는 운영단계에서 설명한다.)




## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

티켓관리 기능이 수행되지 않더라도 예매는 365일 24시간 받을 수 있어야 한다
이를 위해 기능이 블로킹 되지 않기 위하여

- 이를 위하여 티켓관리에 이벤트를 카프카로 송출한다(Publish)
 
```

@Entity
@Table(name="Seat_table")
public class Seat {

    ...
    @PrePersist
    public void onPrePersist() {
        SeatAssigned seatAssigned = new SeatAssigned();
        BeanUtils.copyProperties(this, seatAssigned);
        seatAssigned.publishAfterCommit();
    }

}
```
- 티켓관리에서는 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:

```
package fooddelivery;

...

@Service
public class PolicyHandler{

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverSeatAssigned_Ticket(@Payload SeatAssigned seatAssigned){

        if(!seatAssigned.validate()) return;

        System.out.println("\n\n##### listener Ticket : " + seatAssigned.toJson() + "\n\n");

        // Sample Logic //
        Ticket ticket = new Ticket();
        ticket.setReservationId(seatAssigned.getReservationId());
        ticket.setTicketStatus("발급");
        ticketRepository.save(ticket);
            
    }

}

```
실제 구현을 하자면, 카톡 등으로 점주는 노티를 받고, 요리를 마친후, 주문 상태를 UI에 입력할테니, 우선 주문정보를 DB에 받아놓은 후, 이후 처리는 해당 Aggregate 내에서 하면 되겠다.:


티켓 시스템은 예매,결제,좌석관리와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 예매 시스템이 유지보수로 인해 잠시 내려간 상태라도 주문을 받는데 문제가 없다:

# 티켓시스템을 잠시 내려놓음 

#예매
http POST http://acde84ae9f71a41a5962df4b3fbe9e34-1349237753.ap-southeast-1.elb.amazonaws.com/reservations movieName=겨울왕국 customerName=문상우   #Success
http POST http://acde84ae9f71a41a5962df4b3fbe9e34-1349237753.ap-southeast-1.elb.amazonaws.com/reservations movieName=어벤져스 customerName=로다주   #Success

#티켓관리 확인
http http://acde84ae9f71a41a5962df4b3fbe9e34-1349237753.ap-southeast-1.elb.amazonaws.com/tickets     # 서버가 죽어있음

#티켓관리 서비스 기동
k8s에 티켓관리 deploy

#티켓관리 확인
http http://acde84ae9f71a41a5962df4b3fbe9e34-1349237753.ap-southeast-1.elb.amazonaws.com/tickets     # 기예매된 서비스가 저장되는지 
```

```
# 운영

## CI/CD 설정


각 구현체들은 각자의 source repository 에 구성되었고, 사용한 CI/CD 플랫폼은 AWS를 사용하였으며, pipeline build script 는 각 프로젝트 폴더 이하에 buildspec.yml 에 포함되었다.

특이사항으로는 모든 프로젝트 소스를 아래와 같이 하나의 git에서 관리를 하여 편의성을 도모했으며,

![4](https://user-images.githubusercontent.com/54625960/119451488-48c83800-bd70-11eb-96c2-81c8c54ca7b3.PNG)

각 프로젝트별 빌드를 하기 위해 파이프라인을 아래와 같이 개별적으로 구성했습니다.

![7](https://user-images.githubusercontent.com/54625960/119451492-4a91fb80-bd70-11eb-9166-aea28cb8213e.PNG)


파이프라인은 aws codepipeline, codebuild를 활용했으며, codebuild의 경우 git의 루트 경로가 home임으로 
source build, dockering에서 필요한 경로를 아래와 같이 개별 프로젝트의 buildspec에서 정하여 CI/CD를 구현하였습니다.

![5](https://user-images.githubusercontent.com/54625960/119451490-49f96500-bd70-11eb-8dd3-1e0e84a931c4.PNG)
![6](https://user-images.githubusercontent.com/54625960/119451491-49f96500-bd70-11eb-99eb-855fcf4d846b.PNG)



# 동기식 호출 / 서킷 브레이킹 / 장애격리

* 서킷 브레이킹 프레임워크의 선택: Spring Hystrix 옵션을 사용하여 구현함

시나리오는 예약(Reservation)-->결제(pay) 시의 연결을 RESTful Request/Response 로 연동하여 구현이 되어있고, 결제 요청이 과도할 경우 CB 를 통하여 장애격리.

- Hystrix 를 설정:  요청처리 쓰레드에서 1초 미만, 요청 실패율이 10ㅃ% 를 넘어갈 경우 CB 작동하여 접속자가 많다는 메세지 발송

```
### (Reservation)Reservation.java


   @HystrixCommand(fallbackMethod = "reservationFallback",
   
            commandProperties = {
                    @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "500"),
                    
                    @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "10")
                    
            })



```

- 결제 서비스에 부하를 주지 않도록, 결제 서비스의 응답이 정상적이지 않을 경우 아래 Fallback 함수 작동

- application.yml 에 설정하는 방법도 있지만,  property 가 많지 않아 설정

- Command Key 의 경우 Default 함수인 onPostPersist 에서 Count


```
    
    public String reservationFallback(){
        return "접속자가 많아 기다리셔야 합니다";
    }



```


### 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 


- 결제서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 30프로를 넘어서면 replica 를 10개까지 늘려준다

![1](https://user-images.githubusercontent.com/54625960/119450045-5ed4f900-bd6e-11eb-9f0d-19cf62471fe5.PNG)

- CB 에서 했던 방식대로 워크로드를 100초 동안 걸어준다.
- siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다. 

![3](https://user-images.githubusercontent.com/54625960/119450050-60062600-bd6e-11eb-89ce-e07373449ed1.PNG)


- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다:
- 어느정도 시간이 흐른 후 (약 30초) 스케일 아웃이 벌어지는 것을 확인할 수 있다

![2](https://user-images.githubusercontent.com/54625960/119450048-60062600-bd6e-11eb-8a19-cbd1ca10d010.PNG)


## 무정지 재배포

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함

- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```
siege -c100 -t120S -r10 --content-type "application/json" 'http://localhost:8081/orders POST {"item": "chicken"}'

** SIEGE 4.0.5
** Preparing 100 concurrent users for battle.
The server is now under siege...

HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://localhost:8081/orders
:

```

- 새버전으로의 배포 시작
```
kubectl set image ...
```

- seige 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인
```
Transactions:		        3078 hits
Availability:		       70.45 %
Elapsed time:		       120 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02

```
배포기간중 Availability 가 평소 100%에서 70% 대로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 이를 막기위해 Readiness Probe 를 설정함:

```
# deployment.yaml 의 readiness probe 의 설정:


kubectl apply -f kubernetes/deployment.yaml
```

- 동일한 시나리오로 재배포 한 후 Availability 확인:
```
Transactions:		        3078 hits
Availability:		       100 %
Elapsed time:		       120 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02

```

배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.


# 신규 개발 조직의 추가

  ![image](https://user-images.githubusercontent.com/487999/79684133-1d6c4300-826a-11ea-94a2-602e61814ebf.png)


## 마케팅팀의 추가
    - KPI: 신규 고객의 유입률 증대와 기존 고객의 충성도 향상
    - 구현계획 마이크로 서비스: 기존 customer 마이크로 서비스를 인수하며, 고객에 음식 및 맛집 추천 서비스 등을 제공할 예정

## 이벤트 스토밍 
    ![image](https://user-images.githubusercontent.com/487999/79685356-2b729180-8273-11ea-9361-a434065f2249.png)


## 헥사고날 아키텍처 변화 

![image](https://user-images.githubusercontent.com/487999/79685243-1d704100-8272-11ea-8ef6-f4869c509996.png)

## 구현  

기존의 마이크로 서비스에 수정을 발생시키지 않도록 Inbund 요청을 REST 가 아닌 Event 를 Subscribe 하는 방식으로 구현. 기존 마이크로 서비스에 대하여 아키텍처나 기존 마이크로 서비스들의 데이터베이스 구조와 관계없이 추가됨. 

## 운영과 Retirement

Request/Response 방식으로 구현하지 않았기 때문에 서비스가 더이상 불필요해져도 Deployment 에서 제거되면 기존 마이크로 서비스에 어떤 영향도 주지 않음.

* [비교] 결제 (pay) 마이크로서비스의 경우 API 변화나 Retire 시에 app(주문) 마이크로 서비스의 변경을 초래함:

예) API 변화시
```
# Order.java (Entity)

    @PostPersist
    public void onPostPersist(){

        fooddelivery.external.결제이력 pay = new fooddelivery.external.결제이력();
        pay.setOrderId(getOrderId());
        
        Application.applicationContext.getBean(fooddelivery.external.결제이력Service.class)
                .결제(pay);

                --> 

        Application.applicationContext.getBean(fooddelivery.external.결제이력Service.class)
                .결제2(pay);

    }
```

예) Retire 시
```
# Order.java (Entity)

    @PostPersist
    public void onPostPersist(){

        /**
        fooddelivery.external.결제이력 pay = new fooddelivery.external.결제이력();
        pay.setOrderId(getOrderId());
        
        Application.applicationContext.getBean(fooddelivery.external.결제이력Service.class)
                .결제(pay);

        **/
    }
```
