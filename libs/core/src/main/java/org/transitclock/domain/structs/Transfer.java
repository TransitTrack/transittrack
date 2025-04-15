/* (C)2023 */
package org.transitclock.domain.structs;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.transitclock.gtfs.model.GtfsTransfer;

/**
 * Contains data from the transfers.txt GTFS file. This class is for reading/writing that data to
 * the db.
 *
 * @author SkiBu Smith
 */
@Entity
@DynamicUpdate
@Getter @Setter @ToString
@Table(name = "transfers")
public class Transfer implements Serializable {

    @Id
    @Column(name = "config_rev")
    private final int configRev;

    @Id
    @Column(name = "from_stop_id", length = 60)
    private final String fromStopId;

    @Id
    @Column(name = "to_stop_id", length = 60)
    private final String toStopId;

    @Column(name = "transfer_type", length = 1)
    private final String transferType;

    @Column(name = "min_transfer_time")
    private final Integer minTransferTime;

    public Transfer(int configRev, GtfsTransfer gt) {
        this.configRev = configRev;
        this.fromStopId = gt.getFromStopId();
        this.toStopId = gt.getToStopId();
        this.transferType = gt.getTransferType();
        this.minTransferTime = gt.getMinTransferTime();
    }

    /** Needed because no-arg constructor required by Hibernate */
    @SuppressWarnings("unused")
    protected Transfer() {
        configRev = -1;
        fromStopId = null;
        toStopId = null;
        transferType = null;
        minTransferTime = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transfer transfer)) return false;
        return configRev == transfer.configRev && Objects.equals(fromStopId, transfer.fromStopId) && Objects.equals(toStopId, transfer.toStopId) && Objects.equals(transferType, transfer.transferType) && Objects.equals(minTransferTime, transfer.minTransferTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configRev, fromStopId, toStopId, transferType, minTransferTime);
    }
}
