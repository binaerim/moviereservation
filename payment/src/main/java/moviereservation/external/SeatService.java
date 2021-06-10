
package moviereservation.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name = "seatmanagement", url = "http://user02-seatmanagement:8080")
public interface SeatService {
    /// reserveSeat
    @RequestMapping(method = RequestMethod.POST, path = "/reserveSeat")
    public void reserveSeat(@RequestBody Seat seat);

    /// cancelReserveSeat
    @RequestMapping(method = RequestMethod.POST, path = "/cancelReserveSeat")
    public void cancelReserveSeat(@RequestBody Seat seat);
}
