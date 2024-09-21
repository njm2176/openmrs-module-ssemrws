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
import javax.persistence.Query;
import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.web.dto.PatientObservations;
import org.openmrs.module.webservices.rest.web.RestConstants;
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

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class SSEMRWebServicesController {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	public static SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("dd-MM-yyyy");
	
	public enum filterCategory {
		CHILDREN_ADOLESCENTS,
		PREGNANT_BREASTFEEDING,
		IIT
	};
	
	@PersistenceContext
	private EntityManager entityManager;
	
	private Object fetchAndPaginatePatients(List<Patient> patientList, int page, int size, String totalKey, int totalCount,
	        Date startDate, Date endDate, SSEMRWebServicesController.filterCategory filterCategory,
	        boolean includeClinicalStatus) {
		
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
		
		return generatePatientListObj(new HashSet<>(paginatedPatients), startDate, endDate, filterCategory,
		    includeClinicalStatus, allPatientsObj);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/allClients")
	@ResponseBody
	public Object getAllPatients(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(required = false, value = "page") Integer page,
	        @RequestParam(required = false, value = "size") Integer size) throws ParseException {
		
		DateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		if (qStartDate != null && qEndDate != null) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(endDate);
			calendar.add(Calendar.DAY_OF_MONTH, 1);
			endDate = calendar.getTime();
		}
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		List<Patient> allPatients = Context.getPatientService().getAllPatients(false);
		if (allPatients == null || allPatients.isEmpty()) {
			return "No Clients found for the given date range.";
		}
		
		return fetchAndPaginatePatients(allPatients, page, size, "totalPatients", allPatients.size(), startDate, endDate,
		    filterCategory, true);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/activeClients")
	@ResponseBody
	public Object getActiveClientsEndpoint(HttpServletRequest request,
	        @RequestParam(required = false, value = "startDate") String qStartDate,
	        @RequestParam(required = false, value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(required = false, value = "page") Integer page,
	        @RequestParam(required = false, value = "size") Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		int totalTxCurr = countTxCurr(dates[0], dates[1]);
		
		HashSet<Patient> activeClients = getTxCurr(dates[0], dates[1]);
		if (activeClients.isEmpty()) {
			return "No Active Clients found for the given date range.";
		}
		
		List<Patient> patientList = new ArrayList<>(activeClients);
		
		return fetchAndPaginatePatients(patientList, page, size, "totalTxCurr", totalTxCurr, dates[0], dates[1],
		    filterCategory, false);
	}
	
	// Refactored method for newly enrolled patients
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/newClients")
	@ResponseBody
	public Object getNewPatients(HttpServletRequest request,
	        @RequestParam(required = false, value = "startDate") String qStartDate,
	        @RequestParam(required = false, value = "endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 15;
		
		int totalTxNew = countTxNew(dates[0], dates[1]);
		
		HashSet<Patient> enrolledPatients = getNewlyEnrolledPatients(dates[0], dates[1]);
		if (enrolledPatients.isEmpty()) {
			return "No Newly Enrolled clients found for the given date range.";
		}
		
		List<Patient> txNewList = new ArrayList<>(enrolledPatients);
		
		return fetchAndPaginatePatients(txNewList, page, size, "totalTxNew", totalTxNew, dates[0], dates[1], filterCategory,
		    false);
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
		
		int totalIit = countIit(dates[0], dates[1]);
		
		HashSet<Patient> interruptedInTreatmentPatients = getIit(dates[0], dates[1]);
		
		if (interruptedInTreatmentPatients.isEmpty()) {
			return "No IIT Clients found for the given date range.";
		}
		
		List<Patient> iitList = new ArrayList<>(interruptedInTreatmentPatients);
		
		return fetchAndPaginatePatients(iitList, page, size, "totalIit", totalIit, dates[0], dates[1], filterCategory,
		    false);
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
		
		int totalOnAppointment = countOnAppoinment(startDate, endDate);
		
		HashSet<Patient> onAppointment = getOnAppoinment(startDate, endDate);
		
		if (onAppointment.isEmpty()) {
			return "No Clients on Appointment found for the given date range.";
		}
		
		List<Patient> onAppoinmentList = new ArrayList<>(onAppointment);
		
		return fetchAndPaginatePatients(onAppoinmentList, page, size, "totalOnAppointment", totalOnAppointment, startDate,
		    endDate, filterCategory, false);
	}
	
	/**
	 * Generates a summary of patient data within a specified date range, grouped by year, month, and
	 * week.
	 * 
	 * @param allPatients A set of all patients to be considered for the summary.
	 * @param startDate The start date of the range for which to generate the summary.
	 * @param endDate The end date of the range for which to generate the summary.
	 * @param filterCategory The category to filter patients.
	 * @param includeClinicalStatus A flag to include clinical status.
	 * @param allPatientsObj The object to store all patient details.
	 * @return A JSON string representing the summary of patient data.
	 */
	public Object generatePatientListObj(HashSet<Patient> allPatients, Date startDate, Date endDate,
	        filterCategory filterCategory, Boolean includeClinicalStatus, ObjectNode allPatientsObj) {
		ArrayNode patientList = JsonNodeFactory.instance.arrayNode();
		
		List<Date> patientDates = new ArrayList<>();
		Calendar startCal = Calendar.getInstance();
		startCal.setTime(startDate);
		Calendar endCal = Calendar.getInstance();
		endCal.setTime(endDate);
		
		for (Patient patient : allPatients) {
			ObjectNode patientObj = generatePatientObject(startDate, endDate, filterCategory, patient,
			    includeClinicalStatus);
			if (patientObj != null) {
				patientList.add(patientObj);
				
				Calendar patientCal = Calendar.getInstance();
				patientCal.setTime(patient.getDateCreated());
				
				if (!patientCal.before(startCal) && !patientCal.after(endCal)) {
					if (patient.getDateCreated() != null && !patientCal.before(startCal) && !patientCal.after(endCal)) {
						patientDates.add(patient.getDateCreated());
					}
				} else {
					System.out.println("Patient date out of range");
				}
			}
		}
		
		Map<String, Map<String, Integer>> summary = generateSummary(patientDates);
		
		ObjectNode groupingObj = JsonNodeFactory.instance.objectNode();
		ObjectNode groupYear = JsonNodeFactory.instance.objectNode();
		ObjectNode groupMonth = JsonNodeFactory.instance.objectNode();
		ObjectNode groupWeek = JsonNodeFactory.instance.objectNode();
		
		summary.get("groupYear").forEach(groupYear::put);
		summary.get("groupMonth").forEach(groupMonth::put);
		summary.get("groupWeek").forEach(groupWeek::put);
		
		groupingObj.put("groupYear", groupYear);
		groupingObj.put("groupMonth", groupMonth);
		groupingObj.put("groupWeek", groupWeek);
		
		allPatientsObj.put("totalPatients", allPatients.size());
		allPatientsObj.put("results", patientList);
		allPatientsObj.put("summary", groupingObj);
		
		return allPatientsObj.toString();
	}
	
	private Map<String, Map<String, Integer>> generateSummary(List<Date> dates) {
		String[] months = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov",
		        "Dec" };
		String[] days = new String[] { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
		
		Map<String, Integer> monthlySummary = new HashMap<>();
		Map<String, Integer> weeklySummary = new HashMap<>();
		Map<String, Integer> dailySummary = new HashMap<>();
		
		for (Date date : dates) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			
			String month = months[calendar.get(Calendar.MONTH)];
			monthlySummary.put(month, monthlySummary.getOrDefault(month, 0) + 1);
			
			int week = calendar.get(Calendar.WEEK_OF_MONTH);
			String weekOfTheMonth = String.format("%s_Week%s", month, week);
			weeklySummary.put(weekOfTheMonth, weeklySummary.getOrDefault(weekOfTheMonth, 0) + 1);
			
			int day = calendar.get(Calendar.DAY_OF_WEEK);
			String dayInWeek = String.format("%s_%s", month, days[day - 1]);
			dailySummary.put(dayInWeek, dailySummary.getOrDefault(dayInWeek, 0) + 1);
		}
		
		// Sorting the summaries
		Map<String, Integer> sortedMonthlySummary = monthlySummary.entrySet().stream()
		        .sorted(Comparator.comparingInt(e -> Arrays.asList(months).indexOf(e.getKey())))
		        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		Map<String, Integer> sortedWeeklySummary = weeklySummary.entrySet().stream().sorted((e1, e2) -> {
			String[] parts1 = e1.getKey().split("_Week");
			String[] parts2 = e2.getKey().split("_Week");
			int monthCompare = Arrays.asList(months).indexOf(parts1[0]) - Arrays.asList(months).indexOf(parts2[0]);
			if (monthCompare != 0) {
				return monthCompare;
			} else {
				return Integer.parseInt(parts1[1]) - Integer.parseInt(parts2[1]);
			}
		}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		Map<String, Integer> sortedDailySummary = dailySummary.entrySet().stream().sorted((e1, e2) -> {
			String[] parts1 = e1.getKey().split("_");
			String[] parts2 = e2.getKey().split("_");
			int monthCompare = Arrays.asList(months).indexOf(parts1[0]) - Arrays.asList(months).indexOf(parts2[0]);
			if (monthCompare != 0) {
				return monthCompare;
			} else {
				return Arrays.asList(days).indexOf(parts1[1]) - Arrays.asList(days).indexOf(parts2[1]);
			}
		}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		Map<String, Map<String, Integer>> summary = new HashMap<>();
		summary.put("groupYear", sortedMonthlySummary);
		summary.put("groupMonth", sortedWeeklySummary);
		summary.put("groupWeek", sortedDailySummary);
		
		return summary;
	}
	
	private ObjectNode generatePatientObject(Date startDate, Date endDate, filterCategory filterCategory, Patient patient,
	        boolean includeClinicalStatus) {
		ObjectNode patientObj = JsonNodeFactory.instance.objectNode();
		String artRegimen = getARTRegimen(patient);
		String dateEnrolled = getEnrolmentDate(patient);
		String artInitiationDate = getEnrolmentDate(patient);
		String lastRefillDate = getLastRefillDate(patient);
		String artAppointmentDate = getNextArtAppointmentDate(patient);
		String contact = patient.getAttribute("Client Telephone Number") != null
		        ? String.valueOf(patient.getAttribute("Client Telephone Number"))
		        : "";
		String alternateContact = patient.getAttribute("AltTelephoneNo") != null
		        ? String.valueOf(patient.getAttribute("AltTelephoneNo"))
		        : "";
		
		// Calculate age in years based on patient's birthdate and current date
		Date birthdate = patient.getBirthdate();
		Date currentDate = new Date();
		long age = (currentDate.getTime() - birthdate.getTime()) / (1000L * 60 * 60 * 24 * 365);
		
		ArrayNode identifiersArray = getPatientIdentifiersArray(patient);
		
		String fullAddress = getPatientFullAddress(patient);
		
		// Populate common fields
		patientObj.put("name", patient.getPersonName() != null ? patient.getPersonName().toString() : "");
		patientObj.put("uuid", patient.getUuid());
		patientObj.put("sex", patient.getGender());
		patientObj.put("age", age);
		patientObj.put("identifiers", identifiersArray);
		patientObj.put("address", fullAddress);
		patientObj.put("contact", contact);
		patientObj.put("alternateContact", alternateContact);
		patientObj.put("childOrAdolescent", age <= 19);
		patientObj.put("ARTRegimen", artRegimen);
		patientObj.put("initiationDate", artInitiationDate);
		patientObj.put("dateEnrolled", dateEnrolled);
		patientObj.put("lastRefillDate", lastRefillDate);
		patientObj.put("appointmentDate", artAppointmentDate);
		
		// Add clinical status if needed
		if (includeClinicalStatus) {
			ClinicalStatus clinicalStatus = determineClinicalStatus(patient, startDate, endDate);
			patientObj.put("clinicalStatus", clinicalStatus.toString());
		}
		
		// Check filter category and return only the matching patients
		if (filterCategory != null) {
			switch (filterCategory) {
				case CHILDREN_ADOLESCENTS:
					if (age <= 19) {
						return patientObj;
					}
					break;
				case PREGNANT_BREASTFEEDING:
					if (determineIfPatientIsPregnantOrBreastfeeding(patient, endDate)) {
						return patientObj;
					}
					break;
				case IIT:
					if (determineIfPatientIsIIT(startDate, endDate)) {
						return patientObj;
					}
					break;
				default:
					return null;
			}
		} else {
			return patientObj;
		}
		return null;
	}
	
	private ClinicalStatus determineClinicalStatus(Patient patient, Date startDate, Date endDate) {
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(startDate, endDate);
		if (deceasedPatients.contains(patient)) {
			return ClinicalStatus.DIED;
		}
		
		HashSet<Patient> activeClients = getTxCurr(startDate, endDate);
		if (activeClients.contains(patient)) {
			return ClinicalStatus.ACTIVE;
		}
		
		HashSet<Patient> transferredOutPatients = getTransferredOutPatients(startDate, endDate);
		if (transferredOutPatients.contains(patient)) {
			return ClinicalStatus.TRANSFERRED_OUT;
		}
		
		HashSet<Patient> interruptedInTreatment = getIit(startDate, endDate);
		if (interruptedInTreatment.contains(patient)) {
			return ClinicalStatus.INTERRUPTED_IN_TREATMENT;
		}
		
		return ClinicalStatus.ACTIVE;
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
		observations.setAppointmentDate(getNextAppointmentDate(patient.getUuid()));
		observations.setClinicianName(getClinicianName(patient));
		
		return observations;
	}
	
	private String getNextArtAppointmentDate(Patient patient) {
		return getNextAppointmentDateByUuid(patient.getUuid());
	}
	
	private String getNextAppointmentDate(String patientUuid) {
		return getNextAppointmentDateByUuid(patientUuid);
	}
	
	// Private method to reduce repetition
	private String getNextAppointmentDateByUuid(String patientUuid) {
		if (patientUuid == null || patientUuid.trim().isEmpty()) {
			return "Invalid patient UUID";
		}
		
		if (entityManager == null) {
			throw new IllegalStateException("EntityManager is not initialized!");
		}
		
		Date now = new Date();
		
		String query = "select fp.start_date_time " + "from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where p.uuid = :patientUuid "
		        + "and fp.start_date_time >= :now " + "order by fp.start_date_time asc";
		
		List<Date> results = entityManager.createNativeQuery(query).setParameter("patientUuid", patientUuid)
		        .setParameter("now", now).getResultList();
		
		if (results != null && !results.isEmpty()) {
			return dateTimeFormatter.format(results.get(0));
		} else {
			return "No Upcoming Appointments";
		}
	}
	
	// Method to fetch the list of IIT patients
	private HashSet<Patient> getIit(Date startDate, Date endDate) {
		List<Integer> iitIds = (List<Integer>) executePatientQuery(startDate, endDate, false, "Missed", true);
		return fetchPatientsByIds(iitIds);
	}
	
	// Method to count IIT patients
	private int countIit(Date startDate, Date endDate) {
		return (int) executePatientQuery(startDate, endDate, true, "Missed", true);
	}
	
	// Method to fetch the list of IIT patients
	private HashSet<Patient> getOnAppoinment(Date startDate, Date endDate) {
		List<Integer> iitIds = (List<Integer>) executePatientQuery(startDate, endDate, false, "Scheduled", false);
		return fetchPatientsByIds(iitIds);
	}
	
	// Method to count On Appoinment patients
	private int countOnAppoinment(Date startDate, Date endDate) {
		return (int) executePatientQuery(startDate, endDate, true, "Scheduled", false);
	}
	
	// Determine if patient is Interrupted In Treatment
	private boolean determineIfPatientIsIIT(Date startDate, Date endDate) {
		
		return !getIit(startDate, endDate).isEmpty();
	}
	
	private HashSet<Patient> getPatientsWithMissedAppointment(Date startDate, Date endDate) {
		// SQL query to get missed appointments within the past 28 days
		String query = "select fp.patient_id " + "from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where fp.status = 'Missed' "
		        + "and fp.start_date_time between :startDate and :endDate";
		
		// Execute the query and get a list of patient IDs
		List<Integer> resultIds = entityManager.createNativeQuery(query).setParameter("startDate", startDate)
		        .setParameter("endDate", endDate).getResultList();
		
		// Initialize a HashSet to store unique Patient objects
		HashSet<Patient> PatientsWithMissedAppointment = new HashSet<>();
		
		// Fetch the Patient object for each ID and add to the set
		for (Integer id : resultIds) {
			Patient patient = Context.getPatientService().getPatient(id);
			if (patient != null) {
				PatientsWithMissedAppointment.add(patient);
			}
		}
		
		return PatientsWithMissedAppointment;
	}
	
	// Method to fetch the list of TxCurr patients
	private HashSet<Patient> getTxCurr(Date startDate, Date endDate) {
		List<Integer> patientIds = (List<Integer>) executeTxCurrQuery(startDate, endDate, false);
		return fetchPatientsByIds(patientIds);
	}
	
	// Method to count TxCurr patients
	private int countTxCurr(Date startDate, Date endDate) {
		return (int) executeTxCurrQuery(startDate, endDate, true);
	}
	
	private HashSet<Patient> getFilteredEnrolledPatients(Date startDate, Date endDate) {
		// Get observations for date of enrollment
		List<Obs> enrollmentObs = getObservationsByDateRange(null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(DATE_OF_ENROLLMENT_UUID)), startDate,
		    endDate);
		HashSet<Patient> enrolledClients = extractPatientsFromObservations(enrollmentObs);
		
		// Combine all the patients
		HashSet<Patient> enrolledPatients = new HashSet<>(enrolledClients);
		
		// Filter out transferred-in, deceased, and transferred-out patients
		HashSet<Patient> transferredInPatients = getTransferredInPatients(startDate, endDate);
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(startDate, endDate);
		HashSet<Patient> transferredOutPatients = getTransferredOutPatients(startDate, endDate);
		HashSet<Patient> iitPatients = getIit(startDate, endDate);
		
		enrolledPatients.removeAll(transferredInPatients);
		enrolledPatients.removeAll(transferredOutPatients);
		enrolledPatients.removeAll(deceasedPatients);
		enrolledPatients.removeAll(iitPatients);
		
		return enrolledPatients;
	}
	
	// Method to get newly enrolled patients
	private HashSet<Patient> getNewlyEnrolledPatients(Date startDate, Date endDate) {
		return getFilteredEnrolledPatients(startDate, endDate);
	}
	
	// Method to calculate total TxNew patients
	private int countTxNew(Date startDate, Date endDate) {
		return getFilteredEnrolledPatients(startDate, endDate).size();
	}
	
	private Object executeTxCurrQuery(Date startDate, Date endDate, boolean isCountQuery) {
		String selectClause = isCountQuery ? "count(distinct fp.patient_id)" : "distinct fp.patient_id";
		String baseQuery = "select " + selectClause + " from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where (fp.start_date_time >= :now "
		        + "or (fp.start_date_time between :startDate and :endDate "
		        + "and date(fp.start_date_time) >= current_date() - interval 28 day))";
		
		try {
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("now", new Date())
			        .setParameter("startDate", startDate).setParameter("endDate", endDate);
			
			if (isCountQuery) {
				BigInteger totalTxCurr = (BigInteger) query.getSingleResult();
				return totalTxCurr.intValue();
			} else {
				return query.getResultList();
			}
		}
		catch (Exception e) {
			System.err.println("Error executing TxCurr query: " + e.getMessage());
			throw new RuntimeException("Failed to execute TxCurr query", e);
		}
	}
	
	private Object executePatientQuery(Date startDate, Date endDate, boolean isCountQuery, String status,
	        boolean useCutoffDate) {
		String baseQuery = getQueryString(isCountQuery, status, useCutoffDate);
		
		try {
			// Create and configure the query
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("startDate", startDate)
			        .setParameter("endDate", endDate);
			
			// Set the status parameter if required
			if (status != null) {
				query.setParameter("status", status);
			}
			
			// Set the cutoff date if required
			if (useCutoffDate) {
				// Calculate the cutoff date (28 days ago from today)
				Calendar calendar = Calendar.getInstance();
				calendar.add(Calendar.DAY_OF_YEAR, -28);
				Date cutoffDate = calendar.getTime();
				query.setParameter("cutoffDate", cutoffDate);
			}
			
			// Execute the query based on `isCountQuery`
			if (isCountQuery) {
				BigInteger totalCount = (BigInteger) query.getSingleResult();
				return totalCount.intValue();
			} else {
				return query.getResultList();
			}
		}
		catch (Exception e) {
			// Log the error and rethrow a runtime exception
			System.err.println("Error executing patient query: " + e.getMessage());
			throw new RuntimeException("Failed to execute patient query", e);
		}
	}
	
	private static String getQueryString(boolean isCountQuery, String status, boolean useCutoffDate) {
		String selectClause = isCountQuery ? "count(distinct fp.patient_id)" : "distinct fp.patient_id";
		
		// Start constructing the query
		String baseQuery = "select " + selectClause + " from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id "
		        + (status != null ? "where fp.status = :status " : "where 1=1 ")
		        + (useCutoffDate ? "and fp.start_date_time <= :cutoffDate " : "")
		        + "and fp.start_date_time between :startDate and :endDate";
		return baseQuery;
	}
	
	private HashSet<Patient> fetchPatientsByIds(List<Integer> patientIds) {
		HashSet<Patient> patients = new HashSet<>();
		for (Integer patientId : patientIds) {
			Patient patient = Context.getPatientService().getPatient(patientId);
			if (patient != null) {
				patients.add(patient);
			}
		}
		return patients;
	}
}
