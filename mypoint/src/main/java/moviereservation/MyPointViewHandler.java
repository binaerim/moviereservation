package moviereservation;

import moviereservation.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class MyPointViewHandler {


    @Autowired
    private MyPointRepository myPointRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenPointIncreased_then_CREATE_1 (@Payload PointIncreased pointIncreased) {
        try {

            if (!pointIncreased.validate()) return;

            // view 객체 생성
            MyPoint myPoint = new MyPoint();
            // view 객체에 이벤트의 Value 를 set 함
            myPoint.setPoint(pointIncreased.getPoint());
            myPoint.setReservationId(pointIncreased.getReservationId());
            myPoint.setId(pointIncreased.getId());
            // view 레파지 토리에 save
            myPointRepository.save(myPoint);
        
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenPointIncreased_then_UPDATE_1(@Payload PointIncreased pointIncreased) {
        try {
            if (!pointIncreased.validate()) return;
                // view 객체 조회
            List<MyPoint> myPointList = myPointRepository.findByReservationId(pointIncreased.getReservationId());
            for(MyPoint myPoint : myPointList){
                myPoint.setReservationId(pointIncreased.getReservationId());;
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                myPoint.setPoint(pointIncreased.getPoint());
                // view 레파지 토리에 save
                myPointRepository.save(myPoint);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenPointDecreased_then_UPDATE_2(@Payload PointDecreased pointDecreased) {
        try {
            if (!pointDecreased.validate()) return;
                // view 객체 조회
            List<MyPoint> myPointList = myPointRepository.findByReservationId(pointDecreased.getReservationId());
            for(MyPoint myPoint : myPointList){
                myPoint.setReservationId(pointDecreased.getReservationId());;
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                myPoint.setPoint(pointDecreased.getPoint());
                // view 레파지 토리에 save
                myPointRepository.save(myPoint);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}