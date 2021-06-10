
package moviereservation.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="point", url="http://user02-point:8080")
public interface PointService {

    @RequestMapping(method= RequestMethod.POST, path="/points")
    public void decreasePoint(@RequestBody Point point);

}
