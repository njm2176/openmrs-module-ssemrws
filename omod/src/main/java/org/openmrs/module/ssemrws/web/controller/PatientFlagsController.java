package org.openmrs.module.ssemrws.web.controller;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.constants.SharedConstants;
import org.openmrs.module.ssemrws.web.constants.DeterminePatientFlags;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import static org.openmrs.module.ssemrws.constants.SharedConstants.buildErrorResponse;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class PatientFlagsController {
	
	private final DeterminePatientFlags determinePatientFlags;
	
	public PatientFlagsController(DeterminePatientFlags determinePatientFlags) {
		this.determinePatientFlags = determinePatientFlags;
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/flags")
	@ResponseBody
	public ResponseEntity<Object> getPatientFlags(HttpServletRequest request,
	        @RequestParam("patientUuid") String patientUuid,
	        @RequestParam(required = false, value = "filter") SSEMRWebServicesController.filterCategory filterCategory)
	        throws ParseException {
		
		// Define the dynamic date range
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date startDate = new SimpleDateFormat("yyyy-MM-dd").parse("1970-01-01");
		Date endDate = new Date();
		
		if (StringUtils.isBlank(patientUuid)) {
			return buildErrorResponse("You must specify patientUuid in the request!", HttpStatus.BAD_REQUEST);
		}
		
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		
		if (patient == null) {
			return buildErrorResponse("The provided patient was not found in the system!", HttpStatus.NOT_FOUND);
		}
		
		List<Flags> flags = determinePatientFlags.determinePatientFlags(patient, startDate, endDate);
		
		// Build the response map with dynamic flags
		Map<String, Object> responseMap = new HashMap<>();
		responseMap.put("results", flags.stream().map(Enum::name).collect(Collectors.toList()));
		
		return new ResponseEntity<>(responseMap, new HttpHeaders(), HttpStatus.OK);
	}
}
