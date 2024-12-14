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
	
	private final GetInterruptedInTreatment getInterruptedInTreatment;
	
	private final GetTxCurrQueries getTxCurrQueries;
	
	public GetTxCurr(GetInterruptedInTreatment getInterruptedInTreatment, GetTxCurrQueries getTxCurrQueries) {
		this.getInterruptedInTreatment = getInterruptedInTreatment;
		this.getTxCurrQueries = getTxCurrQueries;
	}
	
	public List<GetTxNew.PatientEnrollmentData> getTxCurrPatients(Date startDate, Date endDate) {
		HashSet<Patient> allPatientsSet = getTxCurrQueries.getTxCurr(startDate, endDate);
		
		// Remove all deceased, IIT, and transferred-out patients
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(startDate, endDate);
		HashSet<Patient> transferredOutPatients = getTransferredOutClients(startDate, endDate);
		HashSet<Patient> iitPatients = getInterruptedInTreatment.getIit(startDate, endDate);
		
		allPatientsSet.removeAll(deceasedPatients);
		allPatientsSet.removeAll(transferredOutPatients);
		allPatientsSet.removeAll(iitPatients);
		
		// Convert patients to PatientEnrollmentData format
		List<GetTxNew.PatientEnrollmentData> result = new ArrayList<>();
		for (Patient patient : allPatientsSet) {
			Obs enrollmentObs = getEnrollmentObsForPatient(patient);
			if (enrollmentObs != null) {
				result.add(new GetTxNew.PatientEnrollmentData(patient, enrollmentObs.getValueDate()));
			}
		}
		
		// Remove duplicates and return the final list
		return new ArrayList<>(new LinkedHashSet<>(result));
		
	}
	
	private Obs getEnrollmentObsForPatient(Patient patient) {
		Concept enrollmentConcept = Context.getConceptService().getConceptByUuid(DATE_OF_ART_INITIATION_CONCEPT_UUID);
		List<Obs> enrollmentObs = Context.getObsService().getObservationsByPersonAndConcept(patient, enrollmentConcept);
		return enrollmentObs.stream().findFirst().orElse(null);
	}
}
