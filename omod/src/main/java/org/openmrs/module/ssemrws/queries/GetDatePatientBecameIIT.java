package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
public class GetDatePatientBecameIIT {
	
	private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy");
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public String getIitDateForPatient(Patient patient, Date startDate, Date endDate) {
		if (patient == null || patient.getPatientId() == null) {
			throw new IllegalArgumentException("Invalid patient or patient ID");
		}
		
		if (startDate == null || endDate == null) {
			throw new IllegalArgumentException("Start date and end date cannot be null");
		}
		
		// Query to fetch the IIT date for a specific patient within the date range
		String query = "SELECT DATE_ADD(MAX(fp.start_date_time), INTERVAL 28 DAY) AS followup_date "
		        + "FROM openmrs.patient_appointment fp " + "WHERE fp.status = 'Missed' "
		        + "AND DATE_ADD(fp.start_date_time, INTERVAL 28 DAY) <= CURDATE() "
		        + "AND DATE_ADD(fp.start_date_time, INTERVAL 28 DAY) BETWEEN :startDate AND :endDate "
		        + "AND fp.patient_id = :patientId " + "AND fp.patient_id NOT IN ( " + "    SELECT DISTINCT fp2.patient_id "
		        + "    FROM openmrs.patient_appointment fp2 " + "    WHERE fp2.start_date_time > CURDATE() " + ") "
		        + "GROUP BY fp.patient_id";
		
		// Execute the query with parameters
		List<Date> results = entityManager.createNativeQuery(query).setParameter("patientId", patient.getPatientId())
		        .setParameter("startDate", startDate).setParameter("endDate", endDate).getResultList();
		
		// Check if a result exists and format the date
		if (!results.isEmpty() && results.get(0) != null) {
			return dateFormatter.format(results.get(0));
		}
		
		// Return an empty string if no date is found
		return "";
	}
}
