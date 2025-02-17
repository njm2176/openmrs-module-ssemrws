package org.openmrs.module.ssemrws.web.controller;

import org.openmrs.Patient;
import org.openmrs.module.ssemrws.queries.GetInterruptedInTreatment;
import org.openmrs.module.ssemrws.web.constants.GetTxCurr;
import org.openmrs.module.ssemrws.web.constants.GetTxNew;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.constants.SharedConstants.createResultMap;
import static org.openmrs.module.ssemrws.web.constants.GetTxNew.getNewlyEnrolledPatients;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class WaterfallAnalysisController {
	
	private final GetTxCurr getTxCurr;
	
	private final GetInterruptedInTreatment getInterruptedInTreatment;
	
	public WaterfallAnalysisController(GetTxCurr getTxCurr, GetInterruptedInTreatment getInterruptedInTreatment) {
		this.getTxCurr = getTxCurr;
		this.getInterruptedInTreatment = getInterruptedInTreatment;
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/waterfallAnalysis")
	@ResponseBody
	public Object getWaterfallAnalysis(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") SSEMRWebServicesController.filterCategory filterCategory)
	        throws ParseException {
		
		return getWaterfallAnalysisChart(qStartDate, qEndDate);
	}
	
	private Object getWaterfallAnalysisChart(String qStartDate, String qEndDate) throws ParseException {
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		// Get all active clients for the entire period
		List<GetTxNew.PatientEnrollmentData> activeClientsEntirePeriod = getTxCurr.getTxCurrPatients(dates[0], dates[1]);
		int totalActiveClients = activeClientsEntirePeriod.size();
		
		List<GetTxNew.PatientEnrollmentData> activeClientsLast30Days = getTxCurr.getTxCurrPatients(dates[0], null);
		
		// Exclude active clients from the last 30 days
		activeClientsEntirePeriod.removeAll(activeClientsLast30Days);
		int txCurrFirstTwoMonths = activeClientsEntirePeriod.size();
		
		// Use newly enrolled patients for TX_NEW
		List<GetTxNew.PatientEnrollmentData> enrolledPatients = getNewlyEnrolledPatients(dates[0], dates[1]);
		int txNewThirdMonth = enrolledPatients.size();
		
		// Other calculations remain unchanged
		HashSet<Patient> transferredInPatientsCurrentQuarter = getTransferredInPatients(dates[0], dates[1]);
		HashSet<Patient> returnToTreatmentPatientsCurrentQuarter = getReturnToTreatmentPatients(dates[0], dates[1]);
		HashSet<Patient> transferredOutPatientsCurrentQuarter = getTransferredOutClients(dates[0], dates[1]);
		HashSet<Patient> deceasedPatientsCurrentQuarter = new HashSet<>(getDeceasedPatientsByDateRange(dates[0], dates[1]));
		HashSet<Patient> interruptedInTreatmentPatientsCurrentQuarter = getInterruptedInTreatment.getIit(dates[0], dates[1]);
		
		int transferInCurrentQuarter = transferredInPatientsCurrentQuarter.size();
		int txRttCurrentQuarter = returnToTreatmentPatientsCurrentQuarter.size();
		int transferOutCurrentQuarter = transferredOutPatientsCurrentQuarter.size();
		int txDeathCurrentQuarter = deceasedPatientsCurrentQuarter.size();
		
		HashSet<Patient> interruptedInTreatmentLessThan3Months = filterInterruptedInTreatmentPatients(
		    interruptedInTreatmentPatientsCurrentQuarter, 3, false);
		int txMlIitLessThan3MoCurrentQuarter = interruptedInTreatmentLessThan3Months.size();
		
		HashSet<Patient> interruptedInTreatmentMoreThan3Months = filterInterruptedInTreatmentPatients(
		    interruptedInTreatmentPatientsCurrentQuarter, 3, true);
		int txMlIitMoreThan3MoCurrentQuarter = interruptedInTreatmentMoreThan3Months.size();
		
		int txCurrPreviousQuarter = totalActiveClients - txNewThirdMonth - transferInCurrentQuarter - txRttCurrentQuarter;
		
		int potentialTxCurr = txNewThirdMonth + txCurrPreviousQuarter + transferInCurrentQuarter + txRttCurrentQuarter;
		
		List<Map<String, Object>> waterfallAnalysisList = new ArrayList<>();
		waterfallAnalysisList.add(createResultMap("TX_CURR", txCurrPreviousQuarter));
		waterfallAnalysisList.add(createResultMap("TX_NEW", txNewThirdMonth));
		waterfallAnalysisList.add(createResultMap("Transfer In", transferInCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("TX_RTT", txRttCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("Potential TX_CURR", potentialTxCurr));
		waterfallAnalysisList.add(createResultMap("Transfer Out", transferOutCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("TX_DEATH", txDeathCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("TX_ML_Self Transfer", 0));
		waterfallAnalysisList.add(createResultMap("TX_ML_Refusal/Stopped", 0));
		waterfallAnalysisList.add(createResultMap("TX_ML_IIT (<3 mo)", txMlIitLessThan3MoCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("TX_ML_IIT (3+ mo)", txMlIitMoreThan3MoCurrentQuarter));
		waterfallAnalysisList.add(createResultMap("CALCULATED TX_CURR", potentialTxCurr));
		
		// ✅ Combine the results
		Map<String, Object> results = new HashMap<>();
		results.put("results", waterfallAnalysisList);
		return results;
	}
	
	private HashSet<Patient> filterInterruptedInTreatmentPatients(HashSet<Patient> patients, int months, boolean moreThan) {
		HashSet<Patient> filteredPatients = new HashSet<>();
		LocalDate currentDate = LocalDate.now();
		SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy");
		
		for (Patient patient : patients) {
			String enrollmentDate = getInitiationDate(patient);
			
			if (enrollmentDate != null) {
				try {
					Date parsedDate = dateFormatter.parse(enrollmentDate);
					LocalDate enrollmentLocalDate = parsedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
					long monthsOnTreatment = ChronoUnit.MONTHS.between(enrollmentLocalDate, currentDate);
					
					if ((moreThan && monthsOnTreatment >= months) || (!moreThan && monthsOnTreatment < months)) {
						filteredPatients.add(patient);
					}
				}
				catch (ParseException e) {
					e.printStackTrace();
				}
			}
		}
		
		return filteredPatients;
	}
}
