package org.openmrs.module.ssemrws.web.constants;

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
		    null, null, 0, null, startDate, endDate, false);
		
		Map<String, List<PatientEnrollmentData>> patientsByMonth = new HashMap<>();
		Set<Integer> uniquePatientIds = new HashSet<>();
		
		for (Obs obs : enrollmentDateObs) {
			Date enrollmentDate = obs.getValueDate();
			
			// Check if the enrollment date falls within the start and end dates
			if (enrollmentDate != null && !enrollmentDate.before(startDate) && !enrollmentDate.after(endDate)) {
				Person person = obs.getPerson();
				if (person.isPatient() && uniquePatientIds.add(person.getId())) {
					Patient patient = Context.getPatientService().getPatient(person.getId());
					PatientEnrollmentData patientData = new PatientEnrollmentData(patient, enrollmentDate);
					
					// Extract the month and year from the enrollment date
					Calendar calendar = Calendar.getInstance();
					calendar.setTime(enrollmentDate);
					String monthYear = String.format("%tB %tY", calendar, calendar);
					
					// Add the patient to the corresponding month-year list
					patientsByMonth.computeIfAbsent(monthYear, k -> new ArrayList<>()).add(patientData);
				}
			}
		}
		
		List<PatientEnrollmentData> filteredClients = new ArrayList<>();
		patientsByMonth.values().forEach(filteredClients::addAll);
		
		return filteredClients;
	}
	
	// Helper class to hold patient and enrollment date information
	public static class PatientEnrollmentData {
		
		private final Patient patient;
		
		private final Date enrollmentDate;
		
		public PatientEnrollmentData(Patient patient, Date enrollmentDate) {
			this.patient = patient;
			this.enrollmentDate = enrollmentDate;
		}
		
		public Patient getPatient() {
			return patient;
		}
		
		public Date getEnrollmentDate() {
			return enrollmentDate;
		}
	}
	
	// Method to get newly enrolled patients
	public static List<PatientEnrollmentData> getNewlyEnrolledPatients(Date startDate, Date endDate) {
		return getFilteredEnrolledPatients(startDate, endDate);
	}
}
