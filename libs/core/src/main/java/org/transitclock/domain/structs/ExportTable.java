/* (C)2023 */
package org.transitclock.domain.structs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.io.Serializable;
import java.sql.Types;
import java.util.Date;

import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;

/**
 * For storing static configuration for vehicle in block.
 *
 * @author Hubert GoEuropa
 */
@Data
@Entity
@DynamicUpdate
@Table(name = "export_table")
public class ExportTable implements Serializable {

    // ID of vehicle
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "data_date")
    @Temporal(TemporalType.DATE)
    private Date dataDate;

    @Column(name = "export_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date exportDate;

    @Column(name = "export_type")
    private int exportType;

    @Column(name = "export_status")
    private int exportStatus;

    @Column(name = "file_name")
    private String fileName;

    @JdbcTypeCode(Types.BINARY)
    @Column(name = "file")
    private byte[] file;

    public ExportTable(Date dataDate, int exportType, String fileName) {
        this.dataDate = dataDate;
        this.exportType = exportType;
        this.fileName = fileName;
        this.exportDate = new Date();
        this.exportStatus = 1;
    }

    public ExportTable(Date dataDate, int exportType, int exportStatus, String fileName) {
        this.dataDate = dataDate;
        this.exportType = exportType;
        this.fileName = fileName;
        this.exportDate = new Date();
        this.exportStatus = exportStatus;
    }

    /**
     * Needed because Hibernate requires no-arg constructor
     */
    @SuppressWarnings("unused")
    protected ExportTable() {
        dataDate = null;
        exportDate = null;
        exportType = 0;
        fileName = null;
        file = null;
    }
}
