package org.openmrs.module.ssemrws.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.service.FacilityDashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.util.List;

import static org.openmrs.module.ssemrws.constants.PrivilegeConstants.VIEW_CHILD_REGIMEN_TREATMENT;
import static org.openmrs.module.ssemrws.constants.SharedConstants.*;

@Service
public class FacilityDashboardServiceImpl implements FacilityDashboardService {
	
	private static final String PRIVILEGES_EXCEPTION_CODE = "error.privilegesRequired";
	
	private final Log log = LogFactory.getLog(this.getClass());
	
	private boolean validateIfUserHasSelfOrAllChildRegimenAccess() {
		return Context.hasPrivilege(VIEW_CHILD_REGIMEN_TREATMENT);
	}
	
	@Transactional
	@Override
	public Object getPatientsOnChildRegimenTreatment(String startDate, String endDate, List<String> regimenConceptUuids,
	        String activeRegimenConceptUuid) {
		if (!validateIfUserHasSelfOrAllChildRegimenAccess()) {
			throw new APIAuthenticationException(Context.getMessageSourceService().getMessage(PRIVILEGES_EXCEPTION_CODE,
			    new Object[] { VIEW_CHILD_REGIMEN_TREATMENT }, null));
		}
		return fetchPatients(startDate, endDate, regimenConceptUuids, activeRegimenConceptUuid);
	}
	
	private Object fetchPatients(String startDate, String endDate, List<String> regimenConceptUuids,
	        String activeRegimenConceptUuid) {
		try {
			// Utilize the getPatientsOnRegimenTreatment method to handle the main data
			// processing
			return getPatientsOnRegimenTreatment(startDate, endDate, regimenConceptUuids, activeRegimenConceptUuid);
		}
		catch (ParseException e) {
			log.error("Error parsing dates for fetching patients on regimen treatment: " + e.getMessage(), e);
			throw new RuntimeException("Failed to fetch patients on regimen treatment due to date parsing error.");
		}
		catch (Exception e) {
			log.error("Error fetching patients on regimen treatment: " + e.getMessage(), e);
			throw new RuntimeException("Failed to fetch patients on regimen treatment due to an unexpected error.");
		}
	}
}
