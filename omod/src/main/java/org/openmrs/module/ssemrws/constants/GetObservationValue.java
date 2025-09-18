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
	 * A reusable method to get the value text from the latest observation for a specific concept.
	 * 
	 * @param patient The patient to get the observation for.
	 * @param conceptUuid The UUID of the concept to search for.
	 * @return The value of the observation as a String, or an empty string if not found.
	 */
	public static String getLatestObsValueText(Patient patient, String conceptUuid) {
		List<Obs> obsValue = Context.getObsService().getObservations(Collections.singletonList(patient.getPerson()), null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(conceptUuid)), null, null, null, null,
		    null, null, null, null, false);
		
		if (!obsValue.isEmpty()) {
			return obsValue.get(0).getValueText();
		}
		
		return "";
	}
	
	/**
	 * Compares the latest observations from two concepts and returns the value of the most recent one.
	 * 
	 * @param patient The patient to query.
	 * @param newConceptUuid The UUID of the primary/new concept.
	 * @param oldConceptUuid The UUID of the secondary/old concept.
	 * @return The value of the most recent observation, or an empty string if none are found.
	 */
	public static String getLatestValueFromConcepts(Patient patient, String newConceptUuid, String oldConceptUuid) {
		Obs newObs = getLatestObsForConcept(patient, newConceptUuid);
		Obs oldObs = getLatestObsForConcept(patient, oldConceptUuid);
		
		if (newObs != null && oldObs != null) {
			return newObs.getObsDatetime().after(oldObs.getObsDatetime()) ? newObs.getValueText() : oldObs.getValueText();
		} else if (newObs != null) {
			return newObs.getValueText();
		} else if (oldObs != null) {
			return oldObs.getValueText();
		} else {
			return "";
		}
	}
	
	/**
	 * Fetches the single most recent observation for a given patient and concept.
	 * 
	 * @param patient The patient.
	 * @param conceptUuid The concept UUID.
	 * @return The latest Obs object, or null if not found.
	 */
	private static Obs getLatestObsForConcept(Patient patient, String conceptUuid) {
		Concept question = Context.getConceptService().getConceptByUuid(conceptUuid);
		if (question == null) {
			System.err.println("Concept not found with uuid: " + conceptUuid);
			return null;
		}
		
		List<Obs> obsList = Context.getObsService().getObservationsByPersonAndConcept(patient.getPerson(), question);
		
		if (!obsList.isEmpty()) {
			return obsList.get(0);
		}
		
		return null;
	}
}
