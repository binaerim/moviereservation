package moviereservation;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Seat_table")
public class Seat {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long reservationId;
    private Long seatQty;

    @PrePersist void onPrePersist() {
        SeatAssigned seatAssigned = new SeatAssigned();
        BeanUtils.copyProperties(this, seatAssigned);
        seatAssigned.publishAfterCommit();
    }

    @PostUpdate
    public void onPostUpdate(){
        //SeatAssigned seatAssigned = new SeatAssigned();
        //BeanUtils.copyProperties(this, seatAssigned);
        //seatAssigned.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        moviereservation.external.Ticket ticket = new moviereservation.external.Ticket();
        // mappings goes here
        // modified by jungilkim (Added Code)
        ticket.setReservationId(this.getReservationId());
        ticket.setTicketStatus("CancellSeat");

        // modified by jungilkim (Application -> SeatmanagementApplication)
        SeatmanagementApplication.applicationContext.getBean(moviereservation.external.TicketService.class)
            .cancelTicket(this.getReservationId(), "CancellSeat");

        SeatCancelled seatCancelled = new SeatCancelled();
        BeanUtils.copyProperties(this, seatCancelled);
        seatCancelled.publishAfterCommit();

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
    public Long getSeatQty() {
        return seatQty;
    }

    public void setSeatQty(Long seatQty) {
        this.seatQty = seatQty;
    }
}
