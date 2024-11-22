package org.openmrs.module.ssemrws.constants;

import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;

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
}
