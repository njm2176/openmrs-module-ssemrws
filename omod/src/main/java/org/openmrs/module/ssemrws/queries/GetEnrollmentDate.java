package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

@Component
public class GetEnrollmentDate {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	// Query to fetch the ART start date for a specific patient
	public String getARTStartDate(Patient patient, Date endDate) {
		if (patient == null || patient.getPatientId() == null) {
			throw new IllegalArgumentException("Invalid patient or patient ID");
		}
		
		// Query to fetch the ART start date for the given patient and within the
		// specified date range
		String qry = "SELECT DATE_FORMAT(tx.art_start_date, '%d-%m-%Y') AS art_start_date "
		        + "FROM ssemr_etl.ssemr_flat_encounter_personal_family_tx_history tx " + "WHERE tx.client_id = :patientId "
		        + "AND DATE(tx.encounter_datetime) <= DATE(:endDate) " + "AND tx.art_start_date IS NOT NULL "
		        + "ORDER BY tx.encounter_datetime DESC " + "LIMIT 1";
		
		// Execute the query with the patient ID and endDate as parameters
		List<String> results = entityManager.createNativeQuery(qry).setParameter("patientId", patient.getPatientId())
		        .setParameter("endDate", endDate).getResultList();
		
		// Return the ART start date if found, otherwise return an empty string
		return results.isEmpty() ? "" : results.get(0);
	}
}
