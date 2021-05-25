package moviereservation;

import moviereservation.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler {
    @Autowired
    PaymentRepository paymentRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCancelled_PayCancel(@Payload Cancelled cancelled) {

        if (!cancelled.validate())
            return;

        System.out.println("\n\n##### listener PayCancel : " + cancelled.toJson() + "\n\n");

        Payment payment = new Payment();
        payment.setReservationId(cancelled.getId());
        payment.setPaymentStatus("cancelled");
        paymentRepository.save(payment);

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString) {
    }

}
