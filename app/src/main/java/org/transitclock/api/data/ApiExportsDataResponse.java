/* (C)2023 */
package org.transitclock.api.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.domain.structs.ExportTable;

/**
 * For when have list of exports. By using this class can control the element name when data is
 * output.
 *
 * @author Hubert goEuropa
 */
@Data
public class ApiExportsDataResponse implements Serializable {

    @JsonProperty("exportsData")
    private List<ApiExportData> data;

    public ApiExportsDataResponse(List<ExportTable> exportData) {
        data = new ArrayList<>();
        for (ExportTable oneExportData : exportData ) {
        	data.add(new ApiExportData(oneExportData));
        }
    }
}
