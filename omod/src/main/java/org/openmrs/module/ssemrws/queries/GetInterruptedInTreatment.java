package org.openmrs.module.ssemrws.queries;

import groovy.util.logging.Commons;
import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier.fetchPatientsByIds;

@Component
public class GetInterruptedInTreatment {
	
	private final GetNextAppointmentDate getNextAppointmentDateByUuid;
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public GetInterruptedInTreatment(GetNextAppointmentDate getNextAppointmentDateByUuid) {
		this.getNextAppointmentDateByUuid = getNextAppointmentDateByUuid;
	}
	
	public HashSet<Patient> getIit(Date startDate, Date endDate) {
		// Execute the query to fetch the list of IIT patient IDs (Missed appointments
		// more than 28 days ago)
		String query = "select distinct fp.patient_id from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where p.uuid is not null "
		        + "and fp.status = 'Missed' " + "and DATEDIFF(CURDATE(), fp.start_date_time) > 28 "
		        + "and fp.start_date_time between :startDate and :endDate";
		
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
