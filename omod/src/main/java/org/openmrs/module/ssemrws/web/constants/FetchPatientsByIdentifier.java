package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;

import java.util.HashSet;
import java.util.List;

public class FetchPatientsByIdentifier {
	
	public static HashSet<Patient> fetchPatientsByIds(List<Integer> patientIds) {
		HashSet<Patient> patients = new HashSet<>();
		for (Integer patientId : patientIds) {
			Patient patient = Context.getPatientService().getPatient(patientId);
			if (patient != null) {
				patients.add(patient);
			}
		}
		return patients;
	}
}
