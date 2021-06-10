package moviereservation;

import moviereservation.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

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
        point.setPoint(point.getPoint() + 1);


        //Point pldpoint = new Point();
        //long lpoint = pointRepository.findByReservationId(ticketReserved.getReservationId()); //Integer.parseInt(request.getParameter("seatno"));

        if(pointRepository.findByReservationId(ticketReserved.getReservationId()) != null) {
            Integer n = ticketReserved.getReservationId().intValue();
            point.setPoint(n+1);
            
        } else {
            point.setPoint(1);
        }

        pointRepository.save(point);
            
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}
