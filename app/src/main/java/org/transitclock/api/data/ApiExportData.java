/* (C)2023 */
package org.transitclock.api.data;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.transitclock.domain.structs.ExportTable;

/**
 * For storing static configuration for vehicle in block.
 *
 * @author Hubert GoEuropa
 */
@XmlRootElement
public class ApiExportData {

    @XmlAttribute
    private long id;

    @XmlAttribute
    private Date dataDate;

    @XmlAttribute
    private Date exportDate;

    @XmlAttribute
    private int exportType;

    @XmlAttribute
    private int exportStatus;

    @XmlAttribute
    private String fileName;

    @XmlAttribute()
    private String file;

    public ApiExportData() {}

    public ApiExportData(ExportTable exportTable) {
        this.id = exportTable.getId();
        this.exportType = exportTable.getExportType();
        this.exportStatus = exportTable.getExportStatus();
        this.fileName = exportTable.getFileName();
        this.dataDate = exportTable.getDataDate();
        this.exportDate = exportTable.getExportDate();
        this.file = exportTable.getExportType() == 2 ? new String(exportTable.getFile(), StandardCharsets.UTF_8) : "";
    }
}
