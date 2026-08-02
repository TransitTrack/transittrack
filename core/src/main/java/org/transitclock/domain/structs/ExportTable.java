/* (C)2023 */
package org.transitclock.domain.structs;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;

import java.io.Serializable;
import java.sql.Types;
import java.util.Date;

/**
 *
 * @author Hubert GoEuropa
 */
@Getter
@Entity
@Slf4j
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

    public ExportTable(long id, Date dataDate, Date exportDate, int exportType, int exportStatus, String fileName) {
        this.id = id;
        this.dataDate = dataDate;
        this.exportDate = exportDate;
        this.exportType = exportType;
        this.exportStatus = exportStatus;
        this.fileName = fileName;
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

    public void setId(long id) {
        this.id = id;
    }

    public void setExportStatus(int id) {
        this.exportStatus = id;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFile(byte[] file) {
        this.file = file;
    }
}
