package org.openmrs.module.ssemrws.constants;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;

public class GetDateObservations {
	
	// Get date as String
	public static String getPatientDateByConcept(Patient patient, String conceptUuid) {
		List<Obs> conceptDateObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(Context.getConceptService().getConceptByUuid(conceptUuid)), null, null, null,
		    null, 0, null, null, null, false);
		
		if (!conceptDateObs.isEmpty()) {
			Obs dateObs = conceptDateObs.get(0);
			Date conceptDate = dateObs.getValueDate();
			if (conceptDate != null) {
				return dateTimeFormatter.format(conceptDate);
			}
		}
		
		return "";
	}
	
	// Get unfiltered Date
	public static Date getDateByConcept(Patient patient, String conceptUuid) {
		List<Obs> conceptDateObs = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(Context.getConceptService().getConceptByUuid(conceptUuid)), null, null, null,
		    null, 0, null, null, null, false);
		
		if (!conceptDateObs.isEmpty()) {
			Obs dateObs = conceptDateObs.get(0);
			return dateObs.getValueDate();
		}
		
		return null;
	}
	
	public static Date getLatestDateFromObs(Patient patient, String conceptUuid) {
		Concept dateConcept = Context.getConceptService().getConceptByUuid(conceptUuid);
		if (dateConcept == null) {
			return null;
		}
		
		List<Obs> obsList = Context.getObsService().getObservationsByPersonAndConcept(patient.getPerson(), dateConcept);
		
		if (obsList.isEmpty()) {
			return null;
		}
		
		return obsList.get(0).getValueDate();
	}
}
