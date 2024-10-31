package org.openmrs.module.ssemrws.web.constants;

import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.controller.SSEMRWebServicesController;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.List;

@Component
public class PaginateTxCurrAndTxNewPages {
	
	private final GeneratePatientListObject getPatientListObjectList;
	
	public PaginateTxCurrAndTxNewPages(GeneratePatientListObject getPatientListObjectList) {
		this.getPatientListObjectList = getPatientListObjectList;
	}
	
	public Object fetchAndPaginatePatientsForNewlyEnrolledPatients(List<Patient> patientList, int page, int size,
	        String totalKey, int totalCount, Date startDate, Date endDate,
	        SSEMRWebServicesController.filterCategory filterCategory) {
		
		if (page < 0 || size <= 0) {
			return "Invalid page or size value. Page must be >= 0 and size must be > 0.";
		}
		
		int fromIndex = page * size;

		int toIndex = Math.min((page + 1) * size, patientList.size());
		
		List<Patient> paginatedPatients = patientList.subList(fromIndex, toIndex);
		
		ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		allPatientsObj.put(totalKey, totalCount);
		
		return getPatientListObjectList.generatePatientListObj(new HashSet<>(paginatedPatients), startDate, endDate,
		    filterCategory, allPatientsObj);
	}
}
