package org.openmrs.module.ssemrws.web.constants;

import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.controller.SSEMRWebServicesController;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.openmrs.module.ssemrws.web.constants.GeneratePatientListObject.*;

@Component
public class PaginatedPages {
	
	private final GeneratePatientListObject generatePatientListObject;
	
	public PaginatedPages(GeneratePatientListObject generatePatientListObject) {
		this.generatePatientListObject = generatePatientListObject;
	}
	
	public Object fetchAndPaginatePatients(List<Patient> patientList, int page, int size, String totalKey, int totalCount,
	        Date startDate, Date endDate, SSEMRWebServicesController.filterCategory filterCategory) {
		
		if (page < 0 || size <= 0) {
			return "Invalid page or size value. Page must be >= 0 and size must be > 0.";
		}
		
		int fromIndex = page * size;
		int toIndex = Math.min((page + 1) * size, patientList.size());
		
		List<Patient> paginatedPatients = patientList.subList(fromIndex, toIndex);
		
		ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		allPatientsObj.put(totalKey, totalCount);
		
		return generatePatientListObject.generatePatientListObj(new HashSet<>(paginatedPatients), startDate, endDate,
		    filterCategory, allPatientsObj);
	}
}
