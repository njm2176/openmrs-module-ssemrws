package org.openmrs.module.ssemrws.constants;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;

import java.util.Collections;
import java.util.List;

public class GetObservationValue {
	
	public static Object getObsValue(Patient patient, Obs observation, String conceptUuid) {
		List<Obs> observations = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
		    null, Collections.singletonList(Context.getConceptService().getConceptByUuid(conceptUuid)), null, null, null,
		    null, null, null, null, null, false);
		
		for (Obs obs : observations) {
			if (obs.getObsGroup() != null && obs.getObsGroup().equals(observation)) {
				if (obs.getValueCoded() != null) {
					return obs.getValueCoded().getName().getName();
				} else if (obs.getValueNumeric() != null) {
					return obs.getValueNumeric();
				} else if (obs.getValueText() != null) {
					return obs.getValueText();
				}
			}
		}
		
		return null;
	}
	
	public static Object getLatestObsByConcept(Patient patient, String conceptUuid) {
		Concept concept = Context.getConceptService().getConceptByUuid(conceptUuid);
		
		List<Obs> observations = Context.getObsService().getObservationsByPersonAndConcept(patient.getPerson(), concept);
		if (observations != null && !observations.isEmpty()) {
			Obs latestObs = observations.get(0);
			
			if (latestObs.getValueCoded() != null) {
				return latestObs.getValueCoded().getName().getName();
			} else if (latestObs.getValueText() != null) {
				return latestObs.getValueText();
			} else if (latestObs.getValueNumeric() != null) {
				return latestObs.getValueNumeric();
			} else if (latestObs.getValueDatetime() != null) {
				return latestObs.getValueDatetime().toString();
			}
		}
		return null;
	}

	/**
	 * A reusable method to get the value text from the latest observation
	 * for a specific concept.
	 *
	 * @param patient The patient to get the observation for.
	 * @param conceptUuid The UUID of the concept to search for.
	 * @return The value of the observation as a String, or an empty string if not found.
	 */
	public static String getLatestObsValueText(Patient patient, String conceptUuid) {
		Concept question = Context.getConceptService().getConceptByUuid(conceptUuid);
		if (question == null) {
			return "Concept " + conceptUuid + "not found";
		}

		List<Obs> obsValue = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()),
				null, Collections.singletonList(Context.getConceptService().getConceptByUuid(conceptUuid)), null, null, null,
				null, null, null, null, null, false);

		if (!obsValue.isEmpty()) {
			return obsValue.get(0).getValueText();
		}

		return "";
	}
}
