package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.*;
import org.openmrs.module.ssemrws.queries.*;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class GetTxCurr {
	
	private final GetTxCurrQueries getTxCurrQueries;
	
	private final GetInterruptedInTreatment getInterruptedInTreatment;
	
	private final GetEnrollmentDate getEnrollmentDate;
	
	public GetTxCurr(GetTxCurrQueries getTxCurrQueries, GetInterruptedInTreatment getInterruptedInTreatment,
	    GetEnrollmentDate getEnrollmentDate) {
		this.getTxCurrQueries = getTxCurrQueries;
		this.getInterruptedInTreatment = getInterruptedInTreatment;
		this.getEnrollmentDate = getEnrollmentDate;
	}
	
	public List<GetTxNew.PatientEnrollmentData> getTxCurrPatients(Date startDate, Date endDate) {
		HashSet<Patient> txCurrPatients = getTxCurrQueries.getTxCurr(endDate);
		
		HashSet<Patient> interruptedInTreatmentPatients = getInterruptedInTreatment.getIit(startDate, endDate);
		
		txCurrPatients.removeAll(interruptedInTreatmentPatients);
		
		// Transform patients into PatientEnrollmentData objects
		List<GetTxNew.PatientEnrollmentData> filteredClients = new ArrayList<>();
		for (Patient patient : txCurrPatients) {
			String artStartDateString = getEnrollmentDate.getARTStartDate(patient, endDate);
			if (artStartDateString != null && !artStartDateString.isEmpty()) {
				try {
					Date artStartDate = new SimpleDateFormat("dd-MM-yyyy").parse(artStartDateString);
					if (!artStartDate.after(endDate)) {
						GetTxNew.PatientEnrollmentData patientData = new GetTxNew.PatientEnrollmentData(patient,
						        artStartDate);
						filteredClients.add(patientData);
					}
				}
				catch (ParseException e) {
					System.err.println("Failed to parse ART start date for patient " + patient.getPatientId());
				}
			}
		}
		
		return filteredClients;
	}
}
