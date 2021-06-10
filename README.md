
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
10. 예매 또는 예매취소가 발생할때 포인트가 적립/취소 된다. (개별 과제 추가)


비기능적 요구사항
1. 트랜잭션
    1. 좌석이 선택되지 않은 예약건은 아예 거래가 성립되지 않아야 한다. Sync 호출
    2. 티켓이 취소되야 결제가 취소된다. Sync 호출
    3. 티켓이 취소되면 포인트도 취소된다. Sync 호출 (개별 과제 추가)
1. 장애격리
    1. 티켓관리 기능이 수행되지 않더라도 예매는 365일 24시간 받을 수 있어야 한다  Async (event-driven), Eventual Consistency
    2. 포인트 기능이 수행되지 않더라도 예매는 365일 24시간 받을 수 있어야 한다  Async (event-driven), Eventual Consistency (개별 과제 추가)
    3. 결제시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도한다  Circuit breaker, fallback
1. 성능
    1. 고객이 자주 예매상태를 마이페이지(프론트엔드)에서 확인할 수 있어야 한다  CQRS
    2. 고객이 자주 확인하는 포인트는  프론트엔드에서 확인할 수 있어야 한다 CQRS (개별 과제 추가)



# 분석/설계

## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과:  http://www.msaez.io/#/storming/8rzhelQnQVQBZpE64M9BimrheFN2/mine/cdbb8701073517aa1bddd0e9ca6f65b5
![image](https://user-images.githubusercontent.com/80744169/121415131-5c93b100-c9a2-11eb-8601-80d490ee7340.PNG)



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

cd point (개별 과제 추가)
mvn spring-boot:run

cd mypoint (개별 과제 추가)
mvn spring-boot:run 

```

## DDD 의 적용

```
# reservation 서비스의 예매처리
http POST http://aa25f6d4a5f3849bdb90146293c1115f-80937499.ap-northeast-2.elb.amazonaws.com/reservations customerName="홍길동" movieName="사탄의인형3" reservationStatus="예매완료"

# payment 서비스의 결제확인
http GET http://aa25f6d4a5f3849bdb90146293c1115f-80937499.ap-northeast-2.elb.amazonaws.com/payments

# myPoint 확인 (개별 과제 추가)
http GET http://aa25f6d4a5f3849bdb90146293c1115f-80937499.ap-northeast-2.elb.amazonaws.com/myPoints

```


## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 좌석이 선택되지 않은 예약건은 아예 거래가 성립되지 않아야 한다. 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 
호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 포인트서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

```
# PointService.java

@FeignClient(name="point", url="http://user02-point:8080")
public interface PointService {

    @RequestMapping(method= RequestMethod.POST, path="/points")
    public void decreasePoint(@RequestBody Point point);

}
```

- 티켓취소 직후(@PostUpdate) 포인트 적립을 요청하도록 처리
```
# Ticket.java (Entity)

    @PostUpdate
    public void onPostUpdate(){

        moviereservation.external.Point point = new moviereservation.external.Point();

        point.setReservationId(this.getReservationId());
        point.setPointStatus("적립취소");

        TicketmanagementApplication.applicationContext.getBean(moviereservation.external.PointService.class)
            .decreasePoint(point);

        TicketCancelled ticketCancelled = new TicketCancelled();
        BeanUtils.copyProperties(this, ticketCancelled);
        ticketCancelled.publishAfterCommit();

    }
```




## 비동기식 호출 / 일관성

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
- 포인트에서는 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:

```
@Service
public class PolicyHandler{
    @Autowired PointRepository pointRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverTicketReserved_IncreasePoint(@Payload TicketReserved ticketReserved){

        if(!ticketReserved.validate()) return;

        System.out.println("\n\n##### listener IncreasePoint : " + ticketReserved.toJson() + "\n\n");

        // Sample Logic //
        Point point = new Point();
        point.setId(ticketReserved.getId());
        point.setReservationId(ticketReserved.getReservationId());
        point.setPointStatus("적립");

        if(pointRepository.findByReservationId(ticketReserved.getReservationId()) != null) {
            Integer n = ticketReserved.getReservationId().intValue();
            point.setPoint(n+1);
            
        } else {
            point.setPoint(1);
        }

        pointRepository.save(point);
            
    }

```


# 운영

## CI/CD 설정


각 구현체들은 각자의 source repository 에 구성되었고, 사용한 CI/CD 플랫폼은 AWS를 사용하였으며, pipeline build script 는 각 프로젝트 폴더 이하에 buildspec.yml 에 포함되었다.

모든 MSA 프로젝트 소스를 아래와 같이 하나의 git에서 관리를 하였다

![github](https://user-images.githubusercontent.com/80744169/121416822-33742000-c9a4-11eb-94f9-1402432b9d08.PNG)


각 프로젝트별 빌드를 하기 위해 파이프라인을 아래와 같이 개별적으로 구성하였다.

![codebuild](https://user-images.githubusercontent.com/80744169/121417164-8e0d7c00-c9a4-11eb-8121-06a658cb5443.PNG)



파이프라인은 aws codepipeline, codebuild를 활용했으며, codebuild의 경우 git의 루트 경로가 home임으로 
source build, dockering에서 필요한 경로를 아래와 같이 개별 프로젝트의 buildspec에서 정하여 CI/CD를 구현하였습니다.

![파이프라인1](https://user-images.githubusercontent.com/80744169/121417478-e93f6e80-c9a4-11eb-82d6-1c3c86154bf6.PNG)
![파이프라인2](https://user-images.githubusercontent.com/80744169/121417484-eba1c880-c9a4-11eb-895d-ef2286713099.PNG)




# 동기식 호출

* 티겟예약이 취소되면 포인트 적립을 동기 호출 한다


```
### Ticket.java


@PostUpdate
    public void onPostUpdate(){

        moviereservation.external.Point point = new moviereservation.external.Point();

        point.setReservationId(this.getReservationId());
        point.setPointStatus("적립취소");

        TicketmanagementApplication.applicationContext.getBean(moviereservation.external.PointService.class)
            .decreasePoint(point);

        TicketCancelled ticketCancelled = new TicketCancelled();
        BeanUtils.copyProperties(this, ticketCancelled);
        ticketCancelled.publishAfterCommit();



    }


```


## 오토스케일 아웃
포인트 서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 30프로를 넘어서면 replica 를 10개까지 늘려준다

![오토스케일](https://user-images.githubusercontent.com/80744169/121447315-646a4a00-c9d0-11eb-94ba-a0baead5a56a.PNG)


## Liveness Probe
컨테이너 상태 체크

* exec-liveness 설정 파일 생성 : exec_leveness.yaml
* 파일 배포 : kubectl create –f exec-liveness.yaml
* 파일 적용 : kubectl apply –f exec-liveness.yaml
* 결과 확인 : watch -n 1 kubectl get all, kubectl describe pod liveness-exec

![probe](https://user-images.githubusercontent.com/80744169/121484942-ab753100-ca0a-11eb-8e65-611b058d9c8c.PNG)


```
apiVersion: v1
kind: Pod
metadata:
  name: liveness-exec
  labels:
    test: liveness
    name: liveness-exec
spec:
  containers:
  - name: liveness
    image: k8s.gcr.io/busybox
    args:
    - /bin/sh
    - -c
    - touch /tmp/healthy; sleep 30; rm -rf /tmp/healthy; sleep 600
    livenessProbe:
      exec:
        command:
        - cat
        - /tmp/healthy
      initialDelaySeconds: 5
      periodSeconds: 5


```
