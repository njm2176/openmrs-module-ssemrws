package org.openmrs.module.ssemrws.constants;

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
}
