package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.queries.GetInterruptedInTreatment;
import org.openmrs.module.ssemrws.queries.GetMissedAppointments;
import org.openmrs.module.ssemrws.queries.GetNextAppointmentDate;
import org.openmrs.module.ssemrws.queries.GetOnAppointment;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.web.constants.AllConcepts.DATE_OF_ENROLLMENT_UUID;

@Component
public class GetTxCurr {
	
	private final GetMissedAppointments getMissedAppointments;
	
	private final GetOnAppointment getOnAppointment;
	
	public GetTxCurr(GetMissedAppointments getMissedAppointments, GetOnAppointment getOnAppointment) {
		this.getMissedAppointments = getMissedAppointments;
		this.getOnAppointment = getOnAppointment;
	}
	
	public List<GetTxNew.PatientEnrollmentData> getTxCurrPatients(Date startDate, Date endDate) {
		Concept enrollmentConcept = Context.getConceptService().getConceptByUuid(DATE_OF_ENROLLMENT_UUID);
		List<Obs> enrollmentObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(enrollmentConcept), null, null, null, null, 0, null, startDate, endDate, false);
		
		List<GetTxNew.PatientEnrollmentData> enrolledClients = new ArrayList<>();
		
		// Loop through all observations for enrollment and filter based on date range
		for (Obs obs : enrollmentObs) {
			Date enrollmentDate = obs.getValueDate();
			
			// Check if the enrollment date falls within the start and end dates
			if (enrollmentDate != null && !enrollmentDate.before(startDate) && !enrollmentDate.after(endDate)) {
				Person person = obs.getPerson();
				if (person.isPatient()) {
					Patient patient = Context.getPatientService().getPatient(person.getId());
					
					// Store the patient along with their enrollment date
					enrolledClients.add(new GetTxNew.PatientEnrollmentData(patient, enrollmentDate));
				}
			}
		}
		
		// Sort patients by enrollment date in chronological order
		enrolledClients.sort(Comparator.comparing(GetTxNew.PatientEnrollmentData::getEnrollmentDate));
		
		// Include patients from transferred-in, on appointment, and missed appointment
		// sets
		HashSet<Patient> transferredInPatients = getTransferredInPatients(startDate, endDate);
		HashSet<Patient> onAppointmentPatients = getOnAppointment.getOnAppoinment(startDate, endDate);
		HashSet<Patient> missedAppointmentPatients = getMissedAppointments.getMissedAppointment(startDate, endDate);
		
		// Combine all patient sets into a single set to avoid duplicates
		HashSet<Patient> allPatientsSet = new HashSet<>();
		allPatientsSet.addAll(transferredInPatients);
		allPatientsSet.addAll(onAppointmentPatients);
		allPatientsSet.addAll(missedAppointmentPatients);
		
		// Remove all died, IIT and Transferred out patients
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(startDate, endDate);
		HashSet<Patient> transferredOutPatients = getTransferredOutClients(startDate, endDate);
		
		allPatientsSet.removeAll(deceasedPatients);
		allPatientsSet.removeAll(transferredOutPatients);
		
		// Filter all patients that have enrollment dates within the date range
		List<GetTxNew.PatientEnrollmentData> allPatientsWithEnrollment = new ArrayList<>();
		
		for (Patient patient : allPatientsSet) {
			// Find enrollment observation for the patient
			Obs enrollmentObsForPatient = enrollmentObs.stream()
			        .filter(obs -> obs.getPerson().getId().equals(patient.getId())).filter(obs -> {
				        Date enrollmentDate = obs.getValueDate();
				        return enrollmentDate != null && !enrollmentDate.before(startDate) && !enrollmentDate.after(endDate);
			        }).findFirst().orElse(null);
			
			if (enrollmentObsForPatient != null) {
				Date enrollmentDate = enrollmentObsForPatient.getValueDate();
				allPatientsWithEnrollment.add(new GetTxNew.PatientEnrollmentData(patient, enrollmentDate));
			}
		}
		
		// Combine enrolled clients with those from the combined set of transferred-in,
		// on appointment, and missed appointments
		allPatientsWithEnrollment.addAll(enrolledClients);
		
		// Remove duplicates if any (using a set to handle unique patients)
		Set<GetTxNew.PatientEnrollmentData> uniquePatients = new LinkedHashSet<>(allPatientsWithEnrollment);
		
		return new ArrayList<>(uniquePatients);
	}
	
	public List<GetTxNew.PatientEnrollmentData> getPatientsCurrentlyOnTreatment(Date startDate, Date endDate) {
		return getTxCurrPatients(startDate, endDate);
	}
}
