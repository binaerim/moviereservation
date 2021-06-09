package moviereservation;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Point_table")
public class Point {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long reservationId;
    private String pointStatus;
    private Integer point;

    @PostPersist
    public void onPostPersist(){
        PointIncreased pointIncreased = new PointIncreased();
        BeanUtils.copyProperties(this, pointIncreased);
        pointIncreased.publishAfterCommit();


    }

    @PostUpdate
    public void onPostUpdate(){
        PointDecreased pointDecreased = new PointDecreased();
        BeanUtils.copyProperties(this, pointDecreased);
        pointDecreased.publishAfterCommit();


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
    public String getPointStatus() {
        return pointStatus;
    }

    public void setPointStatus(String pointStatus) {
        this.pointStatus = pointStatus;
    }
    public Integer getPoint() {
        return point;
    }

    public void setPoint(Integer point) {
        this.point = point;
    }




}
