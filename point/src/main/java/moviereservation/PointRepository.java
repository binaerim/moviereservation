package moviereservation;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel="points", path="points")
public interface PointRepository extends PagingAndSortingRepository<Point, Long>{

    Point findByReservationId(long reservationId);
}
