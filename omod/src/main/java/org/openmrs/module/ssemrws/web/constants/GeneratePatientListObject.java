package org.openmrs.module.ssemrws.web.constants;

import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.controller.SSEMRWebServicesController;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.openmrs.module.ssemrws.web.constants.GenerateSummary.*;

@Component
public class GeneratePatientListObject {
	
	private final GeneratePatientObject generatePatientObject;
	
	public GeneratePatientListObject(GeneratePatientObject generatePatientObject) {
		this.generatePatientObject = generatePatientObject;
	}
	
	/**
	 * Generates a summary of patient data within a specified date range, grouped by year, month, and
	 * week.
	 * 
	 * @param allPatients A set of all patients to be considered for the summary.
	 * @param startDate The start date of the range for which to generate the summary.
	 * @param endDate The end date of the range for which to generate the summary.
	 * @param filterCategory The category to filter patients.
	 * @param allPatientsObj The object to store all patient details.
	 * @return A JSON string representing the summary of patient data.
	 */
	public Object generatePatientListObj(HashSet<Patient> allPatients, Date startDate, Date endDate,
	        SSEMRWebServicesController.filterCategory filterCategory, ObjectNode allPatientsObj) {
		ArrayNode patientList = JsonNodeFactory.instance.arrayNode();
		
		List<Date> patientDates = new ArrayList<>();
		Calendar startCal = Calendar.getInstance();
		startCal.setTime(startDate);
		Calendar endCal = Calendar.getInstance();
		endCal.setTime(endDate);
		
		for (Patient patient : allPatients) {
			ObjectNode patientObj = generatePatientObject.generatePatientObject(startDate, endDate, filterCategory, patient);
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
		
		summary.get("groupYear").forEach(groupYear::put);
		
		groupingObj.put("groupYear", groupYear);
		
		allPatientsObj.put("pageSize", allPatients.size());
		allPatientsObj.put("results", patientList);
		allPatientsObj.put("summary", groupingObj);
		
		return allPatientsObj.toString();
	}
}
