package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.web.controller.SSEMRWebServicesController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;

public class FilterUtility {
	
	public static boolean applyFilter(Patient patient, SSEMRWebServicesController.filterCategory filterCategory,
	        Date endDate) {
		if (filterCategory == null) {
			return true;
		}
		switch (filterCategory) {
			case CHILDREN_ADOLESCENTS:
				return getPatientAge(patient) <= 19;
			case PREGNANT_BREASTFEEDING:
				return determineIfPatientIsPregnantOrBreastfeeding(patient, endDate);
			default:
				return true;
		}
	}
	
}
