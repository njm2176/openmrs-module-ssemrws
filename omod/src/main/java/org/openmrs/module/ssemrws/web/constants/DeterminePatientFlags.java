package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.Patient;
import org.openmrs.module.ssemrws.constants.SharedConstants;
import org.openmrs.module.ssemrws.queries.GetDueForVL;
import org.openmrs.module.ssemrws.queries.GetInterruptedInTreatment;
import org.openmrs.module.ssemrws.queries.GetMissedAppointments;
import org.openmrs.module.ssemrws.queries.GetTxCurrQueries;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.openmrs.module.ssemrws.constants.SharedConstants.getDeceasedPatientsByDateRange;
import static org.openmrs.module.ssemrws.constants.SharedConstants.getTransferredOutClients;

@Component
public class DeterminePatientFlags {
	
	private final GetInterruptedInTreatment getInterruptedInTreatment;
	
	private final GetMissedAppointments getMissedAppointments;
	
	private final GetDueForVL getDueForVl;
	
	private final GetTxCurr getTxCurr;
	
	public DeterminePatientFlags(GetInterruptedInTreatment getInterruptedInTreatment,
	    GetMissedAppointments getMissedAppointments, GetDueForVL getDueForVl, GetTxCurr getTxCurr) {
		this.getInterruptedInTreatment = getInterruptedInTreatment;
		this.getMissedAppointments = getMissedAppointments;
		this.getDueForVl = getDueForVl;
		this.getTxCurr = getTxCurr;
	}
	
	public List<SharedConstants.Flags> determinePatientFlags(Patient patient, Date startDate, Date endDate) {
		List<SharedConstants.Flags> flags = new ArrayList<>();
		
		List<GetTxNew.PatientEnrollmentData> activeClients = getTxCurr.getTxCurrPatients(startDate, endDate);
		for (GetTxNew.PatientEnrollmentData activeClient : activeClients) {
			if (activeClient.getPatient().getId().equals(patient.getId())) {
				flags.add(SharedConstants.Flags.ACTIVE);
				break;
			}
		}
		
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(startDate, endDate);
		if (deceasedPatients.contains(patient)) {
			flags.add(SharedConstants.Flags.DIED);
		}
		
		HashSet<Patient> transferredOutPatients = getTransferredOutClients(startDate, endDate);
		if (transferredOutPatients.contains(patient)) {
			flags.add(SharedConstants.Flags.TRANSFERRED_OUT);
		}
		
		HashSet<Patient> interruptedInTreatment = getInterruptedInTreatment.getIit(startDate, endDate);
		if (interruptedInTreatment.contains(patient)) {
			flags.add(SharedConstants.Flags.IIT);
		}
		
		HashSet<Patient> missedAppointment = getMissedAppointments.getMissedAppointment(startDate, endDate);
		if (missedAppointment.contains(patient)) {
			flags.add(SharedConstants.Flags.MISSED_APPOINTMENT);
		}
		
		HashSet<Patient> dueForVlClients = getDueForVl.getDueForVl(startDate, endDate);
		if (dueForVlClients.contains(patient)) {
			flags.add(SharedConstants.Flags.DUE_FOR_VL);
		}
		
		return flags;
	}
}
