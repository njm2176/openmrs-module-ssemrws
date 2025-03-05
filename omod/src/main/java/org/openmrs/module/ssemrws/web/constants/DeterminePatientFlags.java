package org.openmrs.module.ssemrws.web.constants;

import ca.uhn.hl7v2.model.v23.datatype.ST;
import org.openmrs.Patient;
import org.openmrs.module.ssemrws.constants.SharedConstants;
import org.openmrs.module.ssemrws.queries.GetDueForVL;
import org.openmrs.module.ssemrws.queries.GetInterruptedInTreatment;
import org.openmrs.module.ssemrws.queries.GetMissedAppointments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.*;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;

@Component
public class DeterminePatientFlags {

	private static final Logger logger = LoggerFactory.getLogger(DeterminePatientFlags.class);

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
		
		boolean highVL = determineIfPatientIsHighVl(patient);
		if (highVL) {
			flags.add(SharedConstants.Flags.HIGH_VL);
		}
		
		boolean rtt = determineIfPatientIsRTT(patient);
		if (rtt) {
			flags.add(SharedConstants.Flags.RTT);
		}
		
		String enrollmentDateStr = getEnrolmentDate(patient);
		if (!enrollmentDateStr.isEmpty()) {
			try {
				Date enrollmentDate = dateTimeFormatter.parse(enrollmentDateStr);
				Calendar enrollmentCal = Calendar.getInstance();
				enrollmentCal.setTime(enrollmentDate);
				
				Calendar now = Calendar.getInstance();
				if (enrollmentCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
				        && enrollmentCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)) {
					flags.add(SharedConstants.Flags.NEW_CLIENT);
				}
			}
			catch (ParseException e) {
				logger.error("Error parsing enrollment date for patient id: {}", patient.getId(), e);
			}
		}
		
		return flags;
	}
}
