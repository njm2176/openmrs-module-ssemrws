package org.openmrs.module.ssemrws.web.controller;

import org.openmrs.module.ssemrws.web.constants.*;
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

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class TxCurrController {
	
	private final GenerateSummaryResponseForTxCurrAndTxNew getGenerateSummaryResponseForTxCurrAndTxNew;
	
	private final GetTxCurr getTxCurr;
	
	public TxCurrController(GenerateSummaryResponseForTxCurrAndTxNew getGenerateSummaryResponseForTxCurrAndTxNew,
	    GetTxCurr getTxCurr) {
		this.getGenerateSummaryResponseForTxCurrAndTxNew = getGenerateSummaryResponseForTxCurrAndTxNew;
		this.getTxCurr = getTxCurr;
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/activeClients")
	@ResponseBody
	public Object getActiveClientsEndpoint(HttpServletRequest request,
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
		
		List<GetTxNew.PatientEnrollmentData> txCurrPatients = getTxCurr.getPatientsCurrentlyOnTreatment(dates[0], dates[1]);
		
		int totalPatients = txCurrPatients.size();
		
		ArrayList<GetTxNew.PatientEnrollmentData> txCurrList = new ArrayList<>(txCurrPatients);
		
		return paginateAndGenerateSummaryForTxCurr(txCurrList, page, size, "totalPatients", totalPatients, dates[0],
		    dates[1], filterCategory);
	}
	
	private Object paginateAndGenerateSummaryForTxCurr(ArrayList<GetTxNew.PatientEnrollmentData> patientList, int page,
	        int size, String totalKey, int totalCount, Date startDate, Date endDate,
	        SSEMRWebServicesController.filterCategory filterCategory) {
		return getGenerateSummaryResponseForTxCurrAndTxNew.generateSummaryResponseForActiveAndNewlyEnrolledClients(
		    patientList, page, size, totalKey, totalCount, startDate, endDate, filterCategory,
		    GenerateCumulativeSummary::generateCumulativeSummary);
	}
}
