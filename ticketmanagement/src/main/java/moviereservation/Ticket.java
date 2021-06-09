package moviereservation;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Ticket_table")
public class Ticket {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long reservationId;
    private String ticketStatus;

    @PostPersist
    public void onPostPersist(){
        TicketReserved ticketReserved = new TicketReserved();
        BeanUtils.copyProperties(this, ticketReserved);
        ticketReserved.publishAfterCommit();


    }

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


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }
    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
    }




}
