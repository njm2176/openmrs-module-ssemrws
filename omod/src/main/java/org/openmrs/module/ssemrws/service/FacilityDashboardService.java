package org.openmrs.module.ssemrws.service;

import org.openmrs.annotation.Authorized;
import org.openmrs.module.ssemrws.constants.PrivilegeConstants;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FacilityDashboardService {
	
	@Transactional
	@Authorized({ PrivilegeConstants.VIEW_CHILD_REGIMEN_TREATMENT })
	Object getPatientsOnChildRegimenTreatment(String startDate, String endDate, List<String> regimens,
	        String activeRegimenConceptUuid);
}
