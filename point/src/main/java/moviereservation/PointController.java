package moviereservation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

 @RestController
 public class PointController {

        @Autowired
        PointRepository pointRepository;

@RequestMapping(value = "/decreasePoint",
        method = RequestMethod.POST,
        produces = "application/json;charset=UTF-8")

        /*
public void decreasePoint(HttpServletRequest request, HttpServletResponse response)
        throws Exception {
        System.out.println("##### /point/decreasePoint  called #####");

        Point point = new Point();
        point = request.
        point = pointRepository.findByReservationId(point.getReservationId());
        if (point.getPoint() > 0) {
                point.setPoint(point.getPoint() - 1);
                pointRepository.save(point);
        }
        }
 }
 */

 public void decreasePoint(@RequestBody Point point)
        throws Exception {
        System.out.println("##### /ticket/cancelTicket  called #####");
        System.out.println("seat : " + point.getReservationId());
        System.out.println("seat : " + point);

        if (point.getPoint() > 0) {
                point.setPoint(point.getPoint() - 1);
                point.setPointStatus("적립취소");
                pointRepository.save(point);
        }

        }
 }
