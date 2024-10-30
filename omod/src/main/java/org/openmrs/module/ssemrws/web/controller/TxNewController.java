package org.openmrs.module.ssemrws.web.controller;

import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.constants.FilterUtility;
import org.openmrs.module.ssemrws.web.constants.GenerateSummary;
import org.openmrs.module.ssemrws.web.constants.GenerateSummaryResponseForTxCurrAndTxNew;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.openmrs.module.ssemrws.constants.SharedConstants.getStartAndEndDate;
import static org.openmrs.module.ssemrws.web.constants.GetTxNew.*;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class TxNewController {
	
	private final GenerateSummaryResponseForTxCurrAndTxNew getGenerateSummaryResponseForTxCurrAndTxNew;
	
	public TxNewController(GenerateSummaryResponseForTxCurrAndTxNew getGenerateSummaryResponseForTxCurrAndTxNew) {
		this.getGenerateSummaryResponseForTxCurrAndTxNew = getGenerateSummaryResponseForTxCurrAndTxNew;
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/newClients")
	@ResponseBody
	public Object getNewPatients(HttpServletRequest request,
	        @RequestParam(required = false, value = "startDate") String qStartDate,
	        @RequestParam(required = false, value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") SSEMRWebServicesController.filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		List<PatientEnrollmentData> enrolledPatients = getNewlyEnrolledPatients(dates[0], dates[1]);
		
		enrolledPatients = enrolledPatients.stream()
		        .filter(data -> FilterUtility.applyFilter(data.getPatient(), filterCategory, dates[1]))
		        .collect(Collectors.toList());
		
		int totalPatients = enrolledPatients.size();
		
		ArrayList<PatientEnrollmentData> txNewList = new ArrayList<>(enrolledPatients);
		
		// Use the reusable method
		return paginateAndGenerateSummaryForNewlyEnrolledClients(txNewList, page, size, "totalPatients", totalPatients,
		    dates[0], dates[1], filterCategory);
	}
	
	private Object paginateAndGenerateSummaryForNewlyEnrolledClients(ArrayList<PatientEnrollmentData> patientList, int page,
	        int size, String totalKey, int totalCount, Date startDate, Date endDate,
	        SSEMRWebServicesController.filterCategory filterCategory) {
		return getGenerateSummaryResponseForTxCurrAndTxNew.generateSummaryResponseForActiveAndNewlyEnrolledClients(
		    patientList, page, size, totalKey, totalCount, startDate, endDate, filterCategory,
		    GenerateSummary::generateSummary);
	}
}
