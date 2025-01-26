package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier.fetchPatientsByIds;

@Component
public class GetInterruptedInTreatmentWithinRange {
	
	private final GetNextAppointmentDate getNextAppointmentDateByUuid;
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public GetInterruptedInTreatmentWithinRange(GetNextAppointmentDate getNextAppointmentDateByUuid) {
		this.getNextAppointmentDateByUuid = getNextAppointmentDateByUuid;
	}
	
	public HashSet<Patient> getIitWithinRange(Date startDate, Date endDate) {
		String query = "SELECT t.patient_id FROM ( " + "SELECT p.patient_id, p.status, p.start_date_time, "
		        + "DATE_ADD(p.start_date_time, INTERVAL 28 DAY) AS iit_date " + "FROM openmrs.patient_appointment p "
		        + "JOIN ( " + "    SELECT patient_id, MAX(start_date_time) AS max_start_date_time "
		        + "    FROM openmrs.patient_appointment " + "    GROUP BY patient_id "
		        + ") AS latest_appt ON p.patient_id = latest_appt.patient_id "
		        + "AND p.start_date_time = latest_appt.max_start_date_time "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up e ON e.client_id = p.patient_id "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_end_of_follow_up f ON f.client_id = p.patient_id "
		        + "WHERE p.status = 'Missed' "
		        + "AND DATE_ADD(p.start_date_time, INTERVAL 28 DAY) BETWEEN :startDate AND :endDate "
		        + "AND DATE(e.encounter_datetime) <= DATE(:endDate) " + "AND DATEDIFF(CURDATE(), p.start_date_time) > 28 "
		        + "AND (f.death IS NULL OR f.death != 'Yes') " + "AND (f.transfer_out IS NULL OR f.transfer_out != 'Yes') "
		        + "AND (f.client_refused_treatment IS NULL OR f.client_refused_treatment != 'Yes') "
		        + "ORDER BY p.patient_id ASC " + ") AS t";
		
		// Execute the query
		List<Integer> iitIds = entityManager.createNativeQuery(query).setParameter("startDate", startDate)
		        .setParameter("endDate", endDate).getResultList();
		
		// Fetch patients by their IDs
		HashSet<Patient> patients = fetchPatientsByIds(iitIds);
		
		// Filter out patients with upcoming appointments
		patients.removeIf(patient -> {
			String nextAppointmentDate = getNextAppointmentDateByUuid.getNextAppointmentDateByUuid(patient.getUuid());
			return !nextAppointmentDate.equals("No Upcoming Appointments");
		});
		
		return patients;
	}
}
