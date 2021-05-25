package moviereservation;

import javax.persistence.*;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Reservation_table")
public class Reservation {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String movieName;
    private String customerName;
    private String reservationStatus;



    @HystrixCommand(fallbackMethod = "reservationFallback",
            commandProperties = {
                    @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "500"),
                    @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "10")
            })
    @PostPersist
    public void onPostPersist(){
        Reserved reserved = new Reserved();
        BeanUtils.copyProperties(this, reserved);
        reserved.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        moviereservation.external.Payment payment = new moviereservation.external.Payment();
        // mappings goes here
        payment.setReservationId(this.id);
        payment.setPaymentStatus(this.reservationStatus);

        ReservationApplication.applicationContext.getBean(moviereservation.external.PaymentService.class)
                .pay(payment);

        System.out.println(payment);
    }

    public String reservationFallback(){
        return "접속자가 많아 기다리셔야 합니다";
    }

    @PostUpdate
    public void onPostUpdate(){
        Cancelled cancelled = new Cancelled();
        BeanUtils.copyProperties(this, cancelled);
        cancelled.publishAfterCommit();
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getMovieName() {
        return movieName;
    }

    public void setMovieName(String movieName) {
        this.movieName = movieName;
    }
    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
    public String getReservationStatus() {
        return reservationStatus;
    }

    public void setReservationStatus(String reservationStatus) {
        this.reservationStatus = reservationStatus;
    }




}
