/* (C)2023 */
package org.transitclock.domain.structs;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

/**
 * For storing static configuration for vehicle in block.
 *
 * @author Hubert GoEuropa
 */
@Entity
@Getter @Setter @ToString
@DynamicUpdate
@Table(name = "vehicle_to_block_configs")
public class VehicleToBlockConfig implements Serializable {

    // ID of vehicle
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Id
    @Column(name = "vehicle_id", length = 60)
    private final String vehicleId;

    @Column(name = "assignment_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private final Date assignmentDate;

    @Column(name = "block_id", length = 60)
    private String blockId;

    @Column(name = "trip_id", length = 60)
    private String tripId;

    @Column(name = "valid_from")
    @Temporal(TemporalType.TIMESTAMP)
    private Date validFrom;

    @Column(name = "valid_to")
    @Temporal(TemporalType.TIMESTAMP)
    private Date validTo;

    public VehicleToBlockConfig(
            String vehicleId, String blockId, String tripId, Date assignmentDate, Date validFrom, Date validTo) {
        this.vehicleId = vehicleId;
        this.blockId = blockId;
        this.tripId = tripId;
        this.assignmentDate = assignmentDate;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    /** Needed because Hibernate requires no-arg constructor */
    @SuppressWarnings("unused")
    protected VehicleToBlockConfig() {
        vehicleId = null;
        blockId = null;
        tripId = null;
        assignmentDate = null;
        validFrom = null;
        validTo = null;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VehicleToBlockConfig that)) return false;
        return id == that.id && Objects.equals(vehicleId, that.vehicleId) && Objects.equals(assignmentDate, that.assignmentDate) && Objects.equals(blockId, that.blockId) && Objects.equals(tripId, that.tripId) && Objects.equals(validFrom, that.validFrom) && Objects.equals(validTo, that.validTo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, vehicleId, assignmentDate, blockId, tripId, validFrom, validTo);
    }
}
