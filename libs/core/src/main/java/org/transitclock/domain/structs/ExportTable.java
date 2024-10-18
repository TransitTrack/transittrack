/* (C)2023 */
package org.transitclock.domain.structs;

import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

/**
 * For storing static configuration for vehicle in block.
 *
 * @author Hubert GoEuropa
 */
@Entity
@DynamicUpdate
@ToString
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

    @Column(name = "file")
    private byte[] file;

    public ExportTable(Date dataDate, int exportType, String fileName) {
        this.dataDate = dataDate;
        this.exportType = exportType;
        this.fileName = fileName;
        this.exportDate = new Date();
        this.exportStatus = 1;
    }

    public ExportTable(long id, Date dataDate, Date exportDate, int exportType, int exportStatus, String fileName) {
        this.id = id;
        this.dataDate = dataDate;
        this.exportDate = exportDate;
        this.exportType = exportType;
        this.exportStatus = exportStatus;
        this.fileName = fileName;
    }

    /** Needed because Hibernate requires no-arg constructor */
    @SuppressWarnings("unused")
    protected ExportTable() {
        dataDate = null;
        exportDate = null;
        exportType = 0;
        fileName = null;
        file = null;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Date getDataDate() {
        return dataDate;
    }

    public void setDataDate(Date dataDate) {
        this.dataDate = dataDate;
    }

    public Date getExportDate() {
        return exportDate;
    }

    public void setExportDate(Date exportDate) {
        this.exportDate = exportDate;
    }

    public int getExportType() {
        return exportType;
    }

    public void setExportType(int exportType) {
        this.exportType = exportType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getFile() {
        return file;
    }

    public void setFile(byte[] file) {
        this.file = file;
    }

    public int getExportStatus() {
        return exportStatus;
    }

    public void setExportStatus(int exportStatus) {
        this.exportStatus = exportStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExportTable that)) return false;
        return id == that.id && exportType == that.exportType && exportStatus == that.exportStatus && Objects.equals(dataDate, that.dataDate) && Objects.equals(exportDate, that.exportDate) && Objects.equals(fileName, that.fileName) && Objects.deepEquals(file, that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dataDate, exportDate, exportType, exportStatus, fileName, Arrays.hashCode(file));
    }
}
