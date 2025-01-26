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
import java.util.stream.Collectors;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class TxCurrController {
	
	private final GenerateTxCurrSummaryResponse generateTxCurrSummaryResponse;
	
	private final GetTxCurr getTxCurr;
	
	public TxCurrController(GenerateTxCurrSummaryResponse generateTxCurrSummaryResponse, GetTxCurr getTxCurr) {
		this.generateTxCurrSummaryResponse = generateTxCurrSummaryResponse;
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
		
		List<GetTxNew.PatientEnrollmentData> txCurrPatients = getTxCurr.getTxCurrPatients(dates[0], dates[1]);
		
		txCurrPatients = txCurrPatients.stream()
		        .filter(data -> FilterUtility.applyFilter(data.getPatient(), filterCategory, dates[1]))
		        .collect(Collectors.toList());
		
		int totalPatients = txCurrPatients.size();
		
		ArrayList<GetTxNew.PatientEnrollmentData> txCurrList = new ArrayList<>(txCurrPatients);
		
		return paginateAndGenerateSummaryForTxCurr(txCurrList, page, size, totalPatients, dates[0], dates[1],
		    filterCategory);
	}
	
	private Object paginateAndGenerateSummaryForTxCurr(ArrayList<GetTxNew.PatientEnrollmentData> patientList, int page,
	        int size, int totalCount, Date startDate, Date endDate,
	        SSEMRWebServicesController.filterCategory filterCategory) {
		return generateTxCurrSummaryResponse.generateActiveClientsSummaryResponse(patientList, page, size, "totalPatients",
		    totalCount, startDate, endDate, filterCategory,
		    (enrollmentDates) -> GenerateCumulativeSummary.generateCumulativeSummary(enrollmentDates, startDate, endDate));
	}
}
