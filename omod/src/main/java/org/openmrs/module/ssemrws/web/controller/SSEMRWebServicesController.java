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
import java.math.BigInteger;
import java.text.ParseException;
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
	
	public enum filterCategory {
		CHILDREN_ADOLESCENTS,
		PREGNANT_BREASTFEEDING,
		IIT
	};
	
	@PersistenceContext
	private EntityManager entityManager;
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/allClients")
	@ResponseBody
	public Object getAllPatients(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", defaultValue = "0") int page,
	        @RequestParam(value = "size", defaultValue = "50") int size) throws ParseException {
		
		Date startDate = dateTimeFormatter.parse(qStartDate);
		Date endDate = dateTimeFormatter.parse(qEndDate);
		
		if (qStartDate != null && qEndDate != null) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(endDate);
			calendar.add(Calendar.DAY_OF_MONTH, 1);
			endDate = calendar.getTime();
		}
		
		List<Patient> allPatients = Context.getPatientService().getAllPatients(false);
		
		int startIndex = page * size;
		int endIndex = Math.min(startIndex + size, allPatients.size());
		
		if (startIndex >= allPatients.size()) {
			return "Page out of bounds. Please check the page number and size.";
		}
		
		List<Patient> patients = allPatients.subList(startIndex, endIndex);
		
		ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		
		return generatePatientListObj(new HashSet<>(patients), startDate, endDate, filterCategory, true, allPatientsObj);
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
					patientDates.add(patient.getDateCreated());
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
		
		HashSet<Patient> interruptedInTreatment = getInterruptedInTreatmentPatients(startDate, endDate);
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
	
	private HashSet<Patient> getInterruptedInTreatmentPatients(Date startDate, Date endDate) {
		String query = "select fp.patient_id  " + "from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where fp.status = 'Missed' "
		        + "and fp.start_date_time <= :cutoffDate " + "and fp.start_date_time between :startDate and :endDate";
		
		// Calculate the cutoff date (28 days ago from today)
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_YEAR, -28);
		Date cutoffDate = calendar.getTime();
		
		// Execute the query
		List<Integer> resultIds = entityManager.createNativeQuery(query).setParameter("cutoffDate", cutoffDate)
		        .setParameter("startDate", startDate).setParameter("endDate", endDate).getResultList();
		
		HashSet<Patient> interruptedPatients = new HashSet<>();
		for (Integer id : resultIds) {
			Patient patient = Context.getPatientService().getPatient(id);
			if (patient != null) {
				interruptedPatients.add(patient);
			}
		}
		
		return interruptedPatients;
		
	}
	
	// Determine if patient is Interrupted In Treatment
	private boolean determineIfPatientIsIIT(Date startDate, Date endDate) {
		
		return !getInterruptedInTreatmentPatients(startDate, endDate).isEmpty();
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
	
	// Helper method to execute the common query logic
	private Object executeTxCurrQuery(Date startDate, Date endDate, boolean isCountQuery) {
		String baseQuery;
		
		if (isCountQuery) {
			baseQuery = "select count(distinct fp.patient_id) from openmrs.patient_appointment fp "
			        + "join openmrs.person p on fp.patient_id = p.person_id " + "where (fp.start_date_time >= :now "
			        + "or (fp.start_date_time between :startDate and :endDate "
			        + "and date(fp.start_date_time) >= current_date() - interval 28 day))";
		} else {
			baseQuery = "select distinct fp.patient_id from openmrs.patient_appointment fp "
			        + "join openmrs.person p on fp.patient_id = p.person_id " + "where (fp.start_date_time >= :now "
			        + "or (fp.start_date_time between :startDate and :endDate "
			        + "and date(fp.start_date_time) >= current_date() - interval 28 day))";
		}
		
		// Execute the query
		if (isCountQuery) {
			BigInteger totalTxCurr = (BigInteger) entityManager.createNativeQuery(baseQuery).setParameter("now", new Date())
			        .setParameter("startDate", startDate).setParameter("endDate", endDate).getSingleResult();
			return totalTxCurr.intValue();
		} else {
			List<Integer> appointmentResultIds = entityManager.createNativeQuery(baseQuery).setParameter("now", new Date())
			        .setParameter("startDate", startDate).setParameter("endDate", endDate).getResultList();
			return appointmentResultIds;
		}
	}
	
	// Method to fetch the list of Tx Curr patients
	private HashSet<Patient> getTxCurr(Date startDate, Date endDate) {
		HashSet<Patient> patientsWithAppointments = new HashSet<>();
		List<Integer> appointmentResultIds = (List<Integer>) executeTxCurrQuery(startDate, endDate, false);
		
		// Fetch patients using getPatient(id)
		for (Integer patientId : appointmentResultIds) {
			Patient patient = Context.getPatientService().getPatient(patientId);
			if (patient != null) {
				patientsWithAppointments.add(patient);
			}
		}
		
		return patientsWithAppointments;
	}
	
	// Method to count Tx Curr patients
	private int countTxCurr(Date startDate, Date endDate) {
		return (int) executeTxCurrQuery(startDate, endDate, true);
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard/activeClients")
	@ResponseBody
	public Object getActiveClientsEndpoint(HttpServletRequest request, @RequestParam("startDate") String qStartDate,
	        @RequestParam("endDate") String qEndDate,
	        @RequestParam(required = false, value = "filter") filterCategory filterCategory,
	        @RequestParam(value = "page", required = false) Integer page,
	        @RequestParam(value = "size", required = false) Integer size) throws ParseException {
		
		Date endDate = (qEndDate != null) ? dateTimeFormatter.parse(qEndDate) : new Date();
		
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(endDate);
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		Date startDate = (qStartDate != null) ? dateTimeFormatter.parse(qStartDate) : calendar.getTime();
		
		System.out.println("Start Date: " + startDate);
		System.out.println("End Date: " + endDate);
		
		int totalTxCurr = countTxCurr(startDate, endDate);
		
		HashSet<Patient> activeClients = getTxCurr(startDate, endDate);
		
		List<Patient> patientList = new ArrayList<>(activeClients);
		
		int fromIndex = page * size;
		int toIndex = Math.min((page + 1) * size, patientList.size());
		
		if (fromIndex >= patientList.size()) {
			return "Page out of bounds. Please check the page number and size.";
		}
		
		List<Patient> resultPatients = patientList.subList(fromIndex, toIndex);
		
		ObjectNode allPatientsObj = JsonNodeFactory.instance.objectNode();
		allPatientsObj.put("totalTxCurr", totalTxCurr);
		
		return generatePatientListObj(new HashSet<>(resultPatients), startDate, endDate, filterCategory, false,
		    allPatientsObj);
	}
}
