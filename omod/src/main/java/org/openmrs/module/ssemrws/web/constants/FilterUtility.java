package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.controller.SSEMRWebServicesController;

import java.util.Date;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;

public class FilterUtility {
	
	private static final int CHILDREN_ADOLESCENT_AGE = 19;
	
	public static boolean applyFilter(Patient patient, SSEMRWebServicesController.filterCategory filterCategory,
	        Date endDate) {
		if (filterCategory == null) {
			return true;
		}
		switch (filterCategory) {
			case CHILDREN_ADOLESCENTS:
				return getPatientAge(patient) <= CHILDREN_ADOLESCENT_AGE;
			case PREGNANT_BREASTFEEDING:
				return determineIfPatientIsPregnantOrBreastfeeding(patient, endDate);
			default:
				return true;
		}
	}
	
}
