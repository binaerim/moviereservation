
package moviereservation.external;

import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name="ticketmanagement", url="http://ticketmanagement:8080")
public interface TicketService {

    @RequestMapping(method = RequestMethod.POST, path = "/cancelTicket")
    public void cancelTicket(@RequestBody Ticket ticket);
}
