package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.List;

@Component
public class FetchPatientsByIdentifier {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	private static final int BATCH_SIZE = 15;
	
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
	
	public HashSet<Patient> fetchPatientsIds(List<Integer> patientIds) {
		HashSet<Patient> patients = new HashSet<>();
		int total = patientIds.size();
		for (int i = 0; i < total; i += BATCH_SIZE) {
			List<Integer> batchIds = patientIds.subList(i, Math.min(i + BATCH_SIZE, total));
			patients.addAll(
			    entityManager.createQuery("SELECT p FROM Patient p WHERE p.patientId IN :patientIds", Patient.class)
			            .setParameter("patientIds", batchIds).getResultList());
		}
		return patients;
	}
}
