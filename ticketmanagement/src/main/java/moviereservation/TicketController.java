package moviereservation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

 @RestController
 public class TicketController {

        @Autowired
        TicketRepository ticketRepository;

@RequestMapping(value = "/cancelTicket",
        method = RequestMethod.POST,
        produces = "application/json;charset=UTF-8")

public void cancelTicket(@RequestBody Ticket ticket)
        throws Exception {
        System.out.println("##### /ticket/cancelTicket  called #####");
        System.out.println("seat : " + ticket.getReservationId());
        System.out.println("seat : " + ticket);

        ticket.setTicketStatus("발급취소");
        ticketRepository.save(ticket);

        }
 }
