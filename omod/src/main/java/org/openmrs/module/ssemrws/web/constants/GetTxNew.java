package org.openmrs.module.ssemrws.web.constants;

import lombok.Getter;
import lombok.Setter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.api.context.Context;

import java.util.*;

import static org.openmrs.module.ssemrws.web.constants.AllConcepts.DATE_OF_ENROLLMENT_UUID;

public class GetTxNew {
	
	public static List<PatientEnrollmentData> getFilteredEnrolledPatients(Date startDate, Date endDate) {
		List<Obs> enrollmentDateObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(DATE_OF_ENROLLMENT_UUID)), null, null,
		    null, null, 0, null, null, null, false);
		
		Set<Integer> uniquePatientIds = new HashSet<>();
		List<PatientEnrollmentData> filteredClients = new ArrayList<>();
		
		for (Obs obs : enrollmentDateObs) {
			Date enrollmentDate = obs.getValueDate();
			Person person = obs.getPerson();
			
			// Check if the person is a patient and add if they haven't been added before
			if (enrollmentDate != null && person.isPatient() && uniquePatientIds.add(person.getId())) {
				Patient patient = Context.getPatientService().getPatient(person.getId());
				PatientEnrollmentData patientData = new PatientEnrollmentData(patient, enrollmentDate);
				
				// Include patient if the enrollment date falls within the provided date range
				if (!enrollmentDate.before(startDate) && !enrollmentDate.after(endDate)) {
					filteredClients.add(patientData);
				}
			}
		}
		
		return filteredClients;
	}
	
	// Helper class to hold patient and enrollment date information
	public static class PatientEnrollmentData {
		
		@Getter
		private final Patient patient;
		
		@Getter
		private final Date enrollmentDate;
		
		@Setter
		private boolean hasUpcomingOrMissedAppointments;
		
		public PatientEnrollmentData(Patient patient, Date enrollmentDate) {
			this.patient = patient;
			this.enrollmentDate = enrollmentDate;
			this.hasUpcomingOrMissedAppointments = false;
		}
		
		public boolean hasUpcomingOrMissedAppointments() {
			return hasUpcomingOrMissedAppointments;
		}
		
	}
	
	// Method to get newly enrolled patients
	public static List<PatientEnrollmentData> getNewlyEnrolledPatients(Date startDate, Date endDate) {
		return getFilteredEnrolledPatients(startDate, endDate);
	}
}
