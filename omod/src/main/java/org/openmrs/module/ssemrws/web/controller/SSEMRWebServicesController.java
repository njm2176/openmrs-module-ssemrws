/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.ssemrws.web.controller;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.queries.*;
import org.openmrs.module.ssemrws.service.FacilityDashboardService;
import org.openmrs.module.ssemrws.web.constants.GeneratePatientListObject;
import org.openmrs.module.ssemrws.web.constants.GenerateSummary;
import org.openmrs.module.ssemrws.web.constants.GenerateSummaryResponse;
import org.openmrs.module.ssemrws.web.constants.VlEligibilityResult;
import org.openmrs.module.ssemrws.web.dto.PatientObservations;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.web.constants.AllConcepts.*;
import static org.openmrs.module.ssemrws.web.constants.RegimenConcepts.*;
import static org.openmrs.module.ssemrws.web.constants.ViralLoadCascade.getViralLoadCascade;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class SSEMRWebServicesController {
	
	private final GetNextAppointmentDate getNextAppointmentDate;
	
	private final GeneratePatientListObject getPatientListObjectList;
	
	private final GetInterruptedInTreatment getInterruptedInTreatment;
	
	private final GetMissedAppointments getMissedAppointments;
	
	private final GetTxCurrQueries getTxCurr;
	
	private final GenerateSummaryResponse getSummaryResponse;
	
	private final GetDueForVL getDueForVl;
	
	private final GetOnAppointment getOnAppoinment;
	
	private final GetAllPatients getAllPatients;
	
	public SSEMRWebServicesController(GetNextAppointmentDate getNextAppointmentDate,
	    GeneratePatientListObject getPatientListObjectList, GetInterruptedInTreatment getInterruptedInTreatment,
	    GetMissedAppointments getMissedAppointments, GetTxCurrQueries getTxCurr, GenerateSummaryResponse getSummaryResponse,
	    GetDueForVL getDueForVl, GetOnAppointment getOnAppoinment, GetAllPatients getAllPatients) {
		this.getNextAppointmentDate = getNextAppointmentDate;
		this.getPatientListObjectList = getPatientListObjectList;
		this.getInterruptedInTreatment = getInterruptedInTreatment;
		this.getMissedAppointments = getMissedAppointments;
		this.getTxCurr = getTxCurr;
		this.getSummaryResponse = getSummaryResponse;
		this.getDueForVl = getDueForVl;
		this.getOnAppoinment = getOnAppoinment;
		this.getAllPatients = getAllPatients;
	}
	
	public enum filterCategory {
		CHILDREN_ADOLESCENTS,
		PREGNANT_BREASTFEEDING
	};
	
	@PersistenceContext
	private EntityManager entityManager;
	
	private Object fetchAndPaginatePatients(List<Patient> patientList, int page, int size, String totalKey, int totalCount,
	        Date startDate, Date endDate, filterCategory filterCategory) {
		
		if (page < 0 || size <= 0) {
			return "Invalid page or size value. Page must be >= 0 and size must be > 0.";
		}
		
		int fromIndex = page * size;
		if (fromIndex >= patientList.size()) {
			return "Page out of bounds. Please check the page number and size.";
		}
		int toIndex = Math.min((page + 1) * size, patientList.size());
		
		List<Patient> paginatedPatients = patientList.subList(fromIndex, toIndex);
		
		ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		allPatientsObj.put(totalKey, totalCount);
		
		return getPatientListObjectList.generatePatientListObj(new HashSet<>(paginatedPatients), startDate, endDate,
		    filterCategory, allPatientsObj);
	}

	/**
	 * Retrieves all patients from the system, applying pagination and filtering options.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/allClients")
	@ResponseBody
	public Object getAllPatients(HttpServletRequest request,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(required = false, value = "page") Integer page,
	        @RequestParam(required = false, value = "size") Integer size) {
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> allClients = getAllPatients.getAllPatients(page, size);
		
		if (allClients.isEmpty()) {
			return "No Patients found.";
		}
		
		ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		
		return getAllPatients.allPatientsListObj(allClients, allPatientsObj);
	}
	
	/**
	 * Handles the HTTP GET request to retrieve patients who have experienced an interruption in their
	 * treatment. This method filters encounters based on ART treatment interruption encounter types and
	 * aggregates patients who have had such encounters within the specified date range. It aims to
	 * identify patients who might need follow-up or intervention due to treatment interruption.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/interruptedInTreatment")
	@ResponseBody
	public Object getPatientsInterruptedInTreatment(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> interruptedInTreatmentPatients = getInterruptedInTreatment.getIit(dates[0], dates[1]);
		
		int totalPatients = interruptedInTreatmentPatients.size();
		
		List<Patient> iitList = new ArrayList<>(interruptedInTreatmentPatients);
		
		return fetchAndPaginatePatients(iitList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory);
	}
	
	/**
	 * Returns a list of patients on appointment.
	 * 
	 * @return A JSON representation of the list of patients on appointment, including summary
	 *         information about each patient.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/onAppointment")
	@ResponseBody
	public Object getPatientsOnAppointment(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> onAppointment = getOnAppoinment.getOnAppoinment(startDate, endDate);
		
		int totalPatients = onAppointment.size();
		
		List<Patient> onAppoinmentList = new ArrayList<>(onAppointment);
		
		return fetchAndPaginatePatients(onAppoinmentList, page, size, "totalPatients", totalPatients, startDate, endDate,
		    filterCategory);
	}
	
	/**
	 * Returns a list of patients who missed an appointment.
	 * 
	 * @return A JSON representation of the list of patients who missed an appointment, including
	 *         summary information about each patient.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/missedAppointment")
	@ResponseBody
	public Object getPatientsMissedAppointment(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> missedAppointment = getMissedAppointments.getMissedAppointment(startDate, endDate);
		
		int totalPatients = missedAppointment.size();
		
		List<Patient> missedAppoinmentList = new ArrayList<>(missedAppointment);
		
		return fetchAndPaginatePatients(missedAppoinmentList, page, size, "totalPatients", totalPatients, startDate, endDate,
		    filterCategory);
	}
	
	/**
	 * Handles the HTTP GET request to retrieve patients who have returned to treatment after an
	 * interruption. This method filters encounters based on ART treatment interruption encounter types
	 * and aggregates patients who have returned to treatment within the specified date range.
	 * 
	 * @param request The HttpServletRequest object, providing request information for HTTP servlets.
	 * @return A JSON representation of the list of patients who have returned to treatment, including
	 *         summary information about each patient.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/returnedToTreatment")
	@ResponseBody
	public Object getPatientsReturnedToTreatment(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> rttPatients = getReturnToTreatmentPatients(dates[0], dates[1]);
		
		int totalPatients = rttPatients.size();
		
		List<Patient> rttList = new ArrayList<>(rttPatients);
		
		return fetchAndPaginatePatients(rttList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory);
	}

	/**
	 * Handles the HTTP GET request for retrieving the list of patients who are due for viral load testing.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/dueForVl")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getPatientsDueForVl(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> dueForVlClients = getDueForVl.getDueForVl(dates[0], dates[1]);
		
		int totalPatients = dueForVlClients.size();
		
		List<Patient> dueForVlList = new ArrayList<>(dueForVlClients);
		
		return paginateAndGenerateSummary(dueForVlList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}
	
	/**
	 * This method handles the HTTP GET request for retrieving the list of patients who have been
	 * transferred out.
	 * 
	 * @param request The HTTP request object.
	 * @param qStartDate The start date for the transferred out patients in the format "yyyy-MM-dd".
	 * @param qEndDate The end date for the transferred out patients in the format "yyyy-MM-dd".
	 * @param filterCategory The filter category for the transferred out patients.
	 * @return A JSON object containing the list of transferred out patients.
	 * @throws ParseException If the start or end date cannot be parsed.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/transferredOut")
	@ResponseBody
	public Object getTransferredOutPatients(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> transferredOutPatients = getTransferredOutClients(dates[0], dates[1]);
		// Filter out patients who have an upcoming appointment
		transferredOutPatients.removeIf(patient -> {
			String nextAppointmentDate = getNextAppointmentDate.getNextAppointmentDateByUuid(patient.getUuid());
			return !nextAppointmentDate.equals("No Upcoming Appointments");
		});
		
		int totalPatients = transferredOutPatients.size();
		
		List<Patient> transferOutList = new ArrayList<>(transferredOutPatients);
		
		return fetchAndPaginatePatients(transferOutList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory);
	}

	/**
	 * This method handles the HTTP GET request for retrieving the list of patients who are deceased.
	 *
	 * @param request The HTTP request object, providing request information for HTTP servlets.
	 * @param qStartDate The start date for the patients in the format "yyyy-MM-dd".
	 * @param qEndDate The end date for the patients in the format "yyyy-MM-dd".
	 * @param filterCategory The filter category for the patients.
	 * @param page The page number for pagination.
	 * @param size The number of patients per page.
	 * @return A JSON object containing the list of deceased patients.
	 * @throws ParseException If the start or end date cannot be parsed.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/deceased")
	@ResponseBody
	public Object getDeceasedPatients(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(dates[0], dates[1]);
		
		// Filter out patients who have an upcoming appointment
		deceasedPatients.removeIf(patient -> {
			String nextAppointmentDate = getNextAppointmentDate.getNextAppointmentDateByUuid(patient.getUuid());
			return !nextAppointmentDate.equals("No Upcoming Appointments");
		});
		
		int totalPatients = deceasedPatients.size();
		
		List<Patient> deceasedList = new ArrayList<>(deceasedPatients);
		
		return fetchAndPaginatePatients(deceasedList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory);
	}
	
	/**
	 * Handles the HTTP GET request to retrieve patients with high viral load values within a specified
	 * date range. This method filters patients based on their viral load observations, identifying
	 * those with values above a predefined threshold.
	 * 
	 * @return A JSON representation of the list of patients with high viral load, including summary
	 *         information about each patient.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/highVl")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getPatientsOnHighVl(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> highVLPatients = getPatientsWithHighVL(dates[0], dates[1]);
		
		int totalPatients = highVLPatients.size();
		
		List<Patient> highVlList = new ArrayList<>(highVLPatients);
		
		return fetchAndPaginatePatients(highVlList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory);
	}

	/**
	 * Retrieves patients on adult regimen treatment within a specified date range.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/adultRegimenTreatment")
	@ResponseBody
	public Object getPatientsOnAdultRegimenTreatment(HttpServletRequest request,
	        @RequestParam("startDate") String qStartDate, @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		return getPatientsOnRegimenTreatment(qStartDate, qEndDate,
		    Arrays.asList(regimen_1A, regimen_1B, regimen_1C, regimen_1D, regimen_1E, regimen_1F, regimen_1G, regimen_1H,
		        regimen_1J, regimen_2A, regimen_2B, regimen_2C, regimen_2D, regimen_2E, regimen_2F, regimen_2G, regimen_2H,
		        regimen_2I, regimen_2J, regimen_2K),
		    ACTIVE_REGIMEN_CONCEPT_UUID);
	}
	
	@Autowired
	FacilityDashboardService facilityDashboardService;

	/**
	 * Retrieves patients on children regimen treatment within a specified date range.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/childRegimenTreatment")
	@ResponseBody
	public Object getPatientsOnChildRegimenTreatment(HttpServletRequest request,
	        @RequestParam("startDate") String qStartDate, @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		return facilityDashboardService.getPatientsOnChildRegimenTreatment(qStartDate, qEndDate,
		    Arrays.asList(regimen_4A, regimen_4B, regimen_4C, regimen_4D, regimen_4E, regimen_4F, regimen_4G, regimen_4H,
		        regimen_4I, regimen_4J, regimen_4K, regimen_4L, regimen_5A, regimen_5B, regimen_5C, regimen_5D, regimen_5E,
		        regimen_5F, regimen_5G, regimen_5H, regimen_5I, regimen_5J),
		    ACTIVE_REGIMEN_CONCEPT_UUID);
	}
	
	/**
	 * Retrieves a list of patients under the care of community programs within a specified date range.
	 * 
	 * @return An Object representing the list of patients under the care of community programs within
	 *         the specified date range.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/underCareOfCommunityProgrammes")
	@ResponseBody
	public Object getPatientsUnderCareOfCommunityProgrammes(HttpServletRequest request,
	        @RequestParam("startDate") String qStartDate, @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		EncounterType communityLinkageEncounterType = Context.getEncounterService()
		        .getEncounterTypeByUuid(COMMUNITY_LINKAGE_ENCOUNTER_UUID);
		EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, dates[0], dates[1], null,
		        null, Collections.singletonList(communityLinkageEncounterType), null, null, null, false);
		List<Encounter> encounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
		
		HashSet<Patient> underCareOfCommunityPatients = encounters.stream().map(Encounter::getPatient).collect(HashSet::new,
		    HashSet::add, HashSet::addAll);
		
		int totalPatients = underCareOfCommunityPatients.size();
		
		List<Patient> underCareList = new ArrayList<>(underCareOfCommunityPatients);
		
		return paginateAndGenerateSummary(underCareList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}

	/**
	 * Retrieves patients with Viral Load Sample collections within a specified date range.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadSamplesCollected")
	@ResponseBody
	public String getViralLoadSamplesCollected(HttpServletRequest request,
	        @RequestParam(value = "startDate") String qStartDate, @RequestParam(value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) {
		try {
			SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
			Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
			
			EncounterType viralLoadEncounterType = Context.getEncounterService()
			        .getEncounterTypeByUuid(VL_LAB_REQUEST_ENCOUNTER_TYPE);
			EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, dates[0], dates[1],
			        null, null, Collections.singletonList(viralLoadEncounterType), null, null, null, false);
			List<Encounter> viralLoadSampleEncounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
			
			Concept sampleCollectionDateConcept = Context.getConceptService().getConceptByUuid(SAMPLE_COLLECTION_DATE_UUID);
			List<Obs> sampleCollectionDateObs = Context.getObsService().getObservations(null, viralLoadSampleEncounters,
			    Collections.singletonList(sampleCollectionDateConcept), null, null, null, null, null, null, dates[0],
			    dates[1], false);
			
			// Generate the summary data
			Object summaryData = generateDashboardSummaryFromObs(dates[0], dates[1], sampleCollectionDateObs,
			    filterCategory);
			
			// Convert the summary data to JSON format
			ObjectMapper objectMapper = new ObjectMapper();
			String jsonResponse = objectMapper.writeValueAsString(summaryData);
			
			return jsonResponse;
			
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Retrieves patients with Viral Load results within a specified date range.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadResults")
	// gets all visit forms for a patient
	@ResponseBody
	public Object getViralLoadResults(HttpServletRequest request, @RequestParam(value = "startDate") String qStartDate,
	        @RequestParam(value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) {
		try {
			SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
			Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
			
			EncounterType viralLoadEncounterType = Context.getEncounterService()
			        .getEncounterTypeByUuid(FOLLOW_UP_FORM_ENCOUNTER_TYPE);
			if (viralLoadEncounterType == null) {
				throw new RuntimeException("Encounter type not found: " + FOLLOW_UP_FORM_ENCOUNTER_TYPE);
			}
			
			EncounterSearchCriteria encounterSearchCriteria = new EncounterSearchCriteria(null, null, dates[0], dates[1],
			        null, null, Collections.singletonList(viralLoadEncounterType), null, null, null, false);
			List<Encounter> viralLoadSampleEncounters = Context.getEncounterService().getEncounters(encounterSearchCriteria);
			if (viralLoadSampleEncounters == null || viralLoadSampleEncounters.isEmpty()) {
				throw new RuntimeException("No encounters found for criteria");
			}
			
			Concept viralLoadResultConcept = Context.getConceptService().getConceptByUuid(VIRAL_LOAD_RESULTS_UUID);
			if (viralLoadResultConcept == null) {
				throw new RuntimeException("Concept not found: " + VIRAL_LOAD_RESULTS_UUID);
			}
			
			List<Obs> viralLoadResultObs = Context.getObsService().getObservations(null, viralLoadSampleEncounters,
			    Collections.singletonList(viralLoadResultConcept), null, null, null, null, null, null, dates[0], dates[1],
			    false);
			if (viralLoadResultObs == null || viralLoadResultObs.isEmpty()) {
				throw new RuntimeException("No observations found for the given criteria");
			}
			
			// Generate the summary data
			Map<String, Map<String, Integer>> summaryData = generateDashboardSummaryFromObs(dates[0], dates[1],
			    viralLoadResultObs, filterCategory);
			if (summaryData == null || summaryData.isEmpty()) {
				throw new RuntimeException("Failed to generate summary data");
			}
			
			// Convert the summary data to JSON format
			ObjectMapper objectMapper = new ObjectMapper();
			String jsonResponse = objectMapper.writeValueAsString(summaryData);
			
			return jsonResponse;
			
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error occurred while processing viral load results", e);
		}
	}
	
	/**
	 * Retrieves Clients with viral load coverage data
	 * 
	 * @return JSON representation of the list of patients with viral load coverage data
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadCoverage")
	@ResponseBody
	public Object getViralLoadCoverage(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> viralLoadCoveragePatients = new HashSet<>();
		
		List<Obs> viralLoadObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(VIRAL_LOAD_CONCEPT_UUID)), null, null,
		    null, null, null, null, dates[0], dates[1], false);
		
		for (Obs obs : viralLoadObs) {
			Patient patient = Context.getPatientService().getPatient(obs.getPersonId());
			if (patient != null) {
				viralLoadCoveragePatients.add(patient);
			}
		}
		
		int totalPatients = viralLoadCoveragePatients.size();
		
		if (viralLoadCoveragePatients.isEmpty()) {
			return "No Clients with VL Coverage found for the given date range";
		}
		
		List<Patient> underVLCoverageList = new ArrayList<>(viralLoadCoveragePatients);
		
		return paginateAndGenerateSummary(underVLCoverageList, page, size, "totalPatients", totalPatients, dates[0],
		    dates[1], filterCategory, false);
	}
	
	/**
	 * Handles the HTTP GET request to retrieve patients with Suppressed viral load values. This method
	 * filters patients based on their viral load observations, identifying those with values below a
	 * predefined threshold.
	 * 
	 * @return A JSON representation of the list of patients with Suppressed viral load
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadSuppression")
	@ResponseBody
	public Object getViralLoadSuppression(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		// Date format and range parsing
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		// Set default values for pagination
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		HashSet<Patient> viralLoadSuppressedPatients = new HashSet<>();
		
		List<Obs> viralLoadSuppressedPatientsObs = Context.getObsService().getObservations(null, null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(VIRAL_LOAD_CONCEPT_UUID)), null, null,
		    null, null, null, null, dates[0], dates[1], false);
		
		for (Obs obs : viralLoadSuppressedPatientsObs) {
			if (obs.getValueNumeric() != null && obs.getValueNumeric() < THRESHOLD) {
				Patient patient = Context.getPatientService().getPatient(obs.getPersonId());
				if (patient != null) {
					viralLoadSuppressedPatients.add(patient);
				}
			}
		}
		
		int totalPatients = viralLoadSuppressedPatients.size();
		
		if (viralLoadSuppressedPatients.isEmpty()) {
			return "No Clients with Suppressed VL found for the given date range.";
		}
		
		List<Patient> vlSuppressedList = new ArrayList<>(viralLoadSuppressedPatients);
		
		return paginateAndGenerateSummary(vlSuppressedList, page, size, "totalPatients", totalPatients, dates[0], dates[1],
		    filterCategory, false);
	}
	
	/**
	 * This method handles the viral load cascade endpoint for the ART dashboard. It retrieves the
	 * necessary data from the database and calculates the viral load cascade.
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/viralLoadCascade")
	@ResponseBody
	public Object viralLoadCascade(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		return getViralLoadCascade(qStartDate, qEndDate,
		    Arrays.asList(FIRST_EAC_SESSION, SECOND_EAC_SESSION, THIRD_EAC_SESSION, EXTENDED_EAC_CONCEPT_UUID,
		        REAPEAT_VL_COLLECTION, REPEAT_VL_RESULTS, HIGH_VL_ENCOUNTERTYPE_UUID, ACTIVE_REGIMEN_CONCEPT_UUID),
		    EAC_SESSION_CONCEPT_UUID);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/waterfallAnalysis")
	@ResponseBody
	public Object getWaterfallAnalysis(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		return getWaterfallAnalysisChart(qStartDate, qEndDate);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/obs")
	@ResponseBody
	public ResponseEntity<Object> getPatientObs(HttpServletRequest request, @RequestParam("patientUuid") String patientUuid,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory) throws ParseException {
		
		if (StringUtils.isBlank(patientUuid)) {
			return buildErrorResponse("You must specify patientUuid in the request!", HttpStatus.BAD_REQUEST);
		}
		
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		
		if (patient == null) {
			return buildErrorResponse("The provided patient was not found in the system!", HttpStatus.NOT_FOUND);
		}
		
		PatientObservations observations = getPatientObservations(patient);
		List<Map<String, String>> identifiersList = getIdentifiersList(patient);
		
		String formattedBirthDate = formatBirthdate(patient.getBirthdate());
		long age = calculateAge(patient.getBirthdate());
		
		Map<String, Object> responseMap = buildResponseMap(patient, age, formattedBirthDate, identifiersList, observations);
		
		return new ResponseEntity<>(responseMap, new HttpHeaders(), HttpStatus.OK);
	}
	
	private Object paginateAndGenerateSummary(List<Patient> patientList, int page, int size, String totalKey, int totalCount,
	        Date startDate, Date endDate, filterCategory filterCategory, boolean includeClinicalStatus) {
		return getSummaryResponse.generateSummaryResponse(patientList, page, size, totalKey, totalCount, startDate, endDate,
		    filterCategory, GenerateSummary::generateSummary);
	}
	
	private PatientObservations getPatientObservations(Patient patient) {
		PatientObservations observations = new PatientObservations();
		
		observations.setEnrollmentDate(getEnrolmentDate(patient));
		observations.setDateOfinitiation(getEnrolmentDate(patient));
		observations.setLastRefillDate(getLastRefillDate(patient));
		observations.setArvRegimen(getARTRegimen(patient));
		observations.setLastCD4Count(getLastCD4Count(patient));
		observations.setTbStatus(getTbStatus(patient));
		observations.setArvRegimenDose(getARVRegimenDose(patient));
		observations.setWhoClinicalStage(getWHOClinicalStage(patient));
		observations.setDateVLResultsReceived(getDateVLResultsReceived(patient));
		observations.setChwName(getCHWName(patient));
		observations.setChwPhone(getCHWPhone(patient));
		observations.setChwAddress(getCHWAddress(patient));
		observations.setVlResults(getVLResults(patient));
		observations.setVlStatus(getVLStatus(patient));
		observations.setBmi(getBMI(patient));
		observations.setMuac(getMUAC(patient));
		observations.setAppointmentDate(getNextAppointmentDate.getNextAppointmentDate(patient.getUuid()));
		observations.setClinicianName(getClinicianName(patient));
		observations.setLastVisitDate(getLastVisitDate(patient));
		observations.setTbNumber(getTbNumber(patient));
		observations.setFamilyMembers(getFamilyMemberObservations(patient));
		observations.setIndexFamilyMembers(getIndexFamilyMemberObservations(patient));
		
		VlEligibilityResult eligibilityResult = getDueForVl.isPatientDueForVl(patient.getUuid());
		
		if (eligibilityResult.isEligible()) {
			observations.setVlEligibility("Eligible");
			
			if (eligibilityResult.getVlDueDate() != null) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
				String formattedDate = dateFormat.format(eligibilityResult.getVlDueDate());
				observations.setVlDueDate(formattedDate);
			}
		} else {
			observations.setVlEligibility("Not Eligible");
			observations.setVlDueDate(null);
		}
		
		return observations;
	}
	
	private Object getWaterfallAnalysisChart(String qStartDate, String qEndDate) throws ParseException {
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		// Get all active clients for the entire period
		HashSet<Patient> activeClientsEntirePeriod = getTxCurr.getTxCurr(dates[0], dates[1]);
		
		// Get active clients for the last 30 days
		Calendar last30DaysCal = Calendar.getInstance();
		last30DaysCal.setTime(dates[1]);
		last30DaysCal.add(Calendar.DAY_OF_MONTH, -30);
		Date last30DaysStartDate = last30DaysCal.getTime();
		HashSet<Patient> activeClientsLast30Days = getTxCurr.getTxCurr(last30DaysStartDate, dates[1]);
		
		// Exclude active clients from the last 30 days
		activeClientsEntirePeriod.removeAll(activeClientsLast30Days);
		
		// TX_CURR is the total number of active clients excluding the last 30 days
		int txCurrFirstTwoMonths = activeClientsEntirePeriod.size();
		
		// Get active clients for the third month
		HashSet<Patient> activeClientsThirdMonth = getTxCurr.getTxCurr(dates[0], dates[1]);
		
		// TX_NEW is the new clients in the third month, excluding those from the first
		// two months
		HashSet<Patient> newClientsThirdMonth = new HashSet<>(activeClientsThirdMonth);
		newClientsThirdMonth.removeAll(activeClientsEntirePeriod);
		int txNewThirdMonth = newClientsThirdMonth.size();
		
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
		
		// Potential TX_CURR
		int potentialTxCurr = txNewThirdMonth + txCurrFirstTwoMonths + transferInCurrentQuarter + txRttCurrentQuarter;
		
		// CALCULATED TX_CURR
		
		// Prepare the results
		List<Map<String, Object>> waterfallAnalysisList = new ArrayList<>();
		waterfallAnalysisList.add(createResultMap("TX_CURR", txCurrFirstTwoMonths));
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
		
		// Combine the results
		Map<String, Object> results = new HashMap<>();
		results.put("results", waterfallAnalysisList);
		return results;
	}
	
	private HashSet<Patient> filterInterruptedInTreatmentPatients(HashSet<Patient> patients, int months, boolean moreThan) {
		HashSet<Patient> filteredPatients = new HashSet<>();
		LocalDate currentDate = LocalDate.now();
		
		for (Patient patient : patients) {
			Date enrollmentDate = getInitiationDate(patient);
			if (enrollmentDate != null) {
				LocalDate enrollmentLocalDate = enrollmentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
				long monthsOnTreatment = ChronoUnit.MONTHS.between(enrollmentLocalDate, currentDate);
				
				if ((moreThan && monthsOnTreatment >= months) || (!moreThan && monthsOnTreatment < months)) {
					filteredPatients.add(patient);
				}
			}
		}
		
		return filteredPatients;
	}
}
