package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetDatePatientBecameIIT {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public Date getIitDateForPatient(Patient patient) {
		// Query to fetch the IIT date for a specific patient
		String query = "SELECT DATE_ADD(t.max_start_date_time, INTERVAL 28 DAY) AS iit_date "
		        + "FROM (SELECT MAX(p.start_date_time) AS max_start_date_time "
		        + "FROM openmrs.patient_appointment p WHERE p.status = 'Missed' AND p.patient_id = :patientId) AS t "
		        + "WHERE DATEDIFF(CURDATE(), t.max_start_date_time) > 28";
		
		// Execute the query with the patient ID as a parameter
		List<Object> results = entityManager.createNativeQuery(query).setParameter("patientId", patient.getPatientId())
		        .getResultList();
		
		// Return the IIT date if found, otherwise return null
		if (!results.isEmpty() && results.get(0) != null) {
			return (Date) results.get(0);
		}
		
		return null;
	}
}
