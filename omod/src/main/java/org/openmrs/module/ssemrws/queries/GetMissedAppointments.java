package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier.fetchPatientsByIds;

@Component
public class GetMissedAppointments {
	
	private final GetNextAppointmentDate getNextAppointmentDate;
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public GetMissedAppointments(GetNextAppointmentDate getNextAppointmentDate) {
		this.getNextAppointmentDate = getNextAppointmentDate;
	}
	
	public HashSet<Patient> getMissedAppointment(Date startDate, Date endDate) {
		// Calculate the cutoff date for 28 days ago from today
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_YEAR, -28);
		Date cutoffDate = calendar.getTime();
		
		// Execute the query to fetch the list of missed appointment patient IDs within
		// the last 28 days
		String query = "select distinct fp.patient_id from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where p.uuid is not null "
		        + "and fp.status = 'Missed' " + "and fp.start_date_time >= :cutoffDate "
		        + "and fp.start_date_time between :startDate and :endDate";
		
		List<Integer> missedAppointmentIds = entityManager.createNativeQuery(query).setParameter("cutoffDate", cutoffDate)
		        .setParameter("startDate", startDate).setParameter("endDate", endDate).getResultList();
		
		// Fetch patients by their IDs
		HashSet<Patient> patients = fetchPatientsByIds(missedAppointmentIds);
		
		// Filter out patients with upcoming appointments
		patients.removeIf(patient -> {
			String nextAppointmentDate = getNextAppointmentDate.getNextAppointmentDateByUuid(patient.getUuid());
			return !nextAppointmentDate.equals("No Upcoming Appointments");
		});
		
		return patients;
	}
}
