/* (C)2023 */
package org.transitclock.db.structs;

import java.io.Serializable;
import java.util.Date;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;
import org.transitclock.applications.Core;
import org.transitclock.service.dto.IpcPrediction;

/**
 * For persisting a prediction.
 *
 * @author SkiBu Smith
 */
@Entity
@DynamicUpdate
@Data
@Table(
        name = "Predictions",
        indexes = {@Index(name = "PredictionTimeIndex", columnList = "creationTime")})
public class Prediction implements Serializable {

    // Need an ID but using a regular column doesn't really make
    // sense. So use an auto generated one. Not final since
    // autogenerated and therefore not set in constructor.
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    // The revision of the configuration data that was being used
    @Column
    private final int configRev;

    @Column
    @Temporal(TemporalType.TIMESTAMP)
    private final Date predictionTime;

    // Timestamp of the AVL report that caused the prediction to be generated
    @Column
    @Temporal(TemporalType.TIMESTAMP)
    private final Date avlTime;

    // The time the AVL data was processed and the prediction was created.
    @Column
    @Temporal(TemporalType.TIMESTAMP)
    private final Date creationTime;

    @Column(length = 60)
    private final String vehicleId;

    @Column(length = 60)
    private final String stopId;

    @Column(length = 60)
    private final String tripId;

    @Column(length = 60)
    private final String routeId;

    @Column
    private final boolean affectedByWaitStop;

    @Column
    private final boolean isArrival;

    @Column
    private final boolean schedBasedPred;

    @Column
    private final int gtfsStopSeq;

    public Prediction(
            long predictionTime,
            long avlTime,
            long creationTime,
            String vehicleId,
            String stopId,
            String tripId,
            String routeId,
            boolean affectedByWaitStop,
            boolean isArrival,
            boolean schedBasedPred,
            int gtfsStopSeq) {
        this.configRev = Core.getInstance().getDbConfig().getConfigRev();
        this.predictionTime = new Date(predictionTime);
        this.avlTime = new Date(avlTime);
        this.creationTime = new Date(creationTime);
        this.vehicleId = vehicleId;
        this.stopId = stopId;
        this.tripId = tripId;
        this.routeId = routeId;
        this.affectedByWaitStop = affectedByWaitStop;
        this.isArrival = isArrival;
        this.schedBasedPred = schedBasedPred;
        this.gtfsStopSeq = gtfsStopSeq;
    }

    public Prediction(IpcPrediction prediction) {
        this.configRev = Core.getInstance().getDbConfig().getConfigRev();
        this.predictionTime = new Date(prediction.getPredictionTime());
        this.avlTime = new Date(prediction.getAvlTime());
        this.creationTime = new Date(prediction.getCreationTime());
        this.vehicleId = prediction.getVehicleId();
        this.stopId = prediction.getStopId();
        this.tripId = prediction.getTripId();
        this.routeId = prediction.getTrip().getRouteId();
        this.affectedByWaitStop = prediction.isAffectedByWaitStop();
        this.isArrival = prediction.isArrival();
        this.schedBasedPred = prediction.isSchedBasedPred();
        this.gtfsStopSeq = prediction.getGtfsStopSeq();
    }

    /** Hibernate requires a no-arg constructor for reading objects from database. */
    protected Prediction() {
        this.configRev = -1;
        this.predictionTime = null;
        this.avlTime = null;
        this.creationTime = null;
        this.vehicleId = null;
        this.stopId = null;
        this.tripId = null;
        this.routeId = null;
        this.affectedByWaitStop = false;
        this.isArrival = false;
        this.schedBasedPred = false;
        this.gtfsStopSeq = -1;
    }
}
