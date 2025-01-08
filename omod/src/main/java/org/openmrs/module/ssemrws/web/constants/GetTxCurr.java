package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.queries.*;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.web.constants.AllConcepts.*;

@Component
public class GetTxCurr {

	private final GetDatePatientBecameIIT getDatePatientBecameIIT;
	
	public GetTxCurr(GetDatePatientBecameIIT getDatePatientBecameIIT) {
		this.getDatePatientBecameIIT = getDatePatientBecameIIT;
	}
	
	public List<GetTxNew.PatientEnrollmentData> getTxCurrPatients(Date startDate, Date endDate) {
		// Fetch enrollment dates and patient data
		List<Obs> enrollmentDateObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(DATE_OF_ENROLLMENT_UUID)), null, null,
		    null, null, null, null, null, null, false);

		Set<Integer> uniquePatientIds = new HashSet<>();
		List<GetTxNew.PatientEnrollmentData> filteredClients = new ArrayList<>();

		for (Obs obs : enrollmentDateObs) {
			Date enrollmentDate = obs.getValueDate();
			Person person = obs.getPerson();

			// Check if the person is a patient and add if they haven't been added before
			if (enrollmentDate != null && person.isPatient() && uniquePatientIds.add(person.getId())) {
				Patient patient = Context.getPatientService().getPatient(person.getId());

				// Retrieve death, transfer out, IIT, and RTT dates
				Date deathDate = getDeathDate(patient);
				Date transferOutDate = getTransferredOutDate(patient);
				Date iitDate = getDatePatientBecameIIT.getIitDateForPatient(patient);
				Date rttDate = getReturnedToTreatmentDate(patient);

				// Determine the earliest exclusion date
				Date exclusionDate = null;
				if (deathDate != null)
					exclusionDate = deathDate;
				if (transferOutDate != null)
					exclusionDate = (exclusionDate == null || transferOutDate.before(exclusionDate)) ? transferOutDate
					        : exclusionDate;
				if (iitDate != null)
					exclusionDate = (exclusionDate == null || iitDate.before(exclusionDate)) ? iitDate : exclusionDate;

				// Adjust exclusion logic to consider RTT date
				if (exclusionDate != null) {
					if (exclusionDate.before(endDate) && exclusionDate.after(enrollmentDate)) {
						// If RTT date exists and is after exclusion, re-include patient from RTT onward
						if (rttDate != null && !rttDate.after(endDate)) {
							GetTxNew.PatientEnrollmentData patientData = new GetTxNew.PatientEnrollmentData(patient,
							        rttDate);
							filteredClients.add(patientData);
							System.out.println("Including Patient: " + patient.getPersonName().getFullName()
							        + " due to return to treatment date: " + rttDate);
							continue;
						}
						System.out.println("Excluding Patient: " + patient.getPersonName().getFullName()
						        + " due to exclusion date: " + exclusionDate);
						continue;
					}
				}

				// Add patient if the enrollment date is within the query range
				if (!enrollmentDate.after(endDate)) {
					GetTxNew.PatientEnrollmentData patientData = new GetTxNew.PatientEnrollmentData(patient, enrollmentDate);
					filteredClients.add(patientData);
				}
			}
		}

		return filteredClients;
	}
	
	private Obs getEnrollmentObsForPatient(Patient patient) {
		Concept enrollmentConcept = Context.getConceptService().getConceptByUuid(DATE_OF_ART_INITIATION_CONCEPT_UUID);
		List<Obs> enrollmentObs = Context.getObsService().getObservationsByPersonAndConcept(patient, enrollmentConcept);
		return enrollmentObs.stream().findFirst().orElse(null);
	}
}
