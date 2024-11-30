package org.openmrs.module.ssemrws.web.controller;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.api.context.Context;
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
import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.List;

import static org.openmrs.module.ssemrws.constants.SharedConstants.getVLResults;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class FormsController {
	
	public static final String ADULT_AND_ADOLESCENT_INTAKE_FORM_ENCOUNTERTYPE_UUID = "b645dbdd-7d58-41d4-9b11-eeff023b8ee5";
	
	public static final String PEDIATRIC_INTAKE_FORM_ENCOUNTERTYPE_UUID = "356def6a-fa66-4a78-97d5-b43154064875";
	
	public static final String HIGH_VL_FORM_ENCOUNTERTYPE_UUID = "f7f1c854-69e5-11ee-8c99-0242ac120002";
	
	/**
	 * Gets a list of available/completed forms for a patient
	 * 
	 * @param request
	 * @param patientUuid
	 * @return
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/forms")
	@ResponseBody
	public Object getAllAvailableFormsForVisit(HttpServletRequest request, @RequestParam("patientUuid") String patientUuid) {
		if (StringUtils.isBlank(patientUuid)) {
			return new ResponseEntity<>("You must specify patientUuid in the request!", new HttpHeaders(),
			        HttpStatus.BAD_REQUEST);
		}
		
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		
		if (patient == null) {
			return new ResponseEntity<>("The provided patient was not found in the system!", new HttpHeaders(),
			        HttpStatus.NOT_FOUND);
		}
		
		// Calculate patient age
		LocalDate birthDate = patient.getBirthdate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
		LocalDate currentDate = LocalDate.now();
		
		int years = Period.between(birthDate, currentDate).getYears();
		int months = Period.between(birthDate, currentDate).getMonths();
		int days = Period.between(birthDate, currentDate).getDays();
		
		String ageDisplay;
		if (years >= 1) {
			ageDisplay = years + " year" + (years > 1 ? "s" : "");
		} else if (months >= 1) {
			ageDisplay = months + " month" + (months > 1 ? "s" : "");
		} else {
			long weeks = days / 7;
			ageDisplay = weeks + " week" + (weeks > 1 ? "s" : "");
		}
		
		// Fetch the most recent VL results
		String vlResultString = getVLResults(patient);
		boolean hasHighVL = false;
		if (vlResultString != null) {
			try {
				double vlResult = Double.parseDouble(vlResultString);
				hasHighVL = vlResult >= 1000;
			}
			catch (NumberFormatException e) {
				System.err.println("Unable to parse VL result: " + vlResultString);
			}
		}
		
		List<Visit> activeVisits = Context.getVisitService().getActiveVisitsByPatient(patient);
		if (activeVisits.isEmpty()) {
			return new ResponseEntity<>("The patient has no active visits.", new HttpHeaders(), HttpStatus.NOT_FOUND);
		}
		
		ArrayNode formList = JsonNodeFactory.instance.arrayNode();
		ObjectNode allFormsObj = JsonNodeFactory.instance.objectNode();
		
		// Add patient details to the response
		allFormsObj.put("patientName", patient.getGivenName() + " " + patient.getFamilyName());
		allFormsObj.put("patientUuid", patient.getUuid());
		allFormsObj.put("patientAge", ageDisplay);
		
		// Fetch all published forms
		List<Form> availableForms = Context.getFormService().getPublishedForms();
		
		for (Form form : availableForms) {
			String encounterTypeUuid = (form.getEncounterType() != null) ? form.getEncounterType().getUuid() : null;
			
			// Add all forms to the list
			ObjectNode formObj = createFormObject(form, encounterTypeUuid);
			
			// Attach the last filled date for the form
			if (encounterTypeUuid != null) {
				Encounter lastEncounter = getLastEncounterForType(patient, form.getEncounterType());
				if (lastEncounter != null) {
					formObj.put("lastFilledDate", lastEncounter.getEncounterDatetime().toString());
				} else {
					formObj.put("lastFilledDate", "Never filled");
				}
			} else {
				formObj.put("lastFilledDate", "No encounter type associated");
			}
			
			// Apply criteria for additional inclusion logic
			if (encounterTypeUuid != null) {
				if (encounterTypeUuid.equals(ADULT_AND_ADOLESCENT_INTAKE_FORM_ENCOUNTERTYPE_UUID) && years <= 15) {
					continue;
				}
				if (encounterTypeUuid.equals(PEDIATRIC_INTAKE_FORM_ENCOUNTERTYPE_UUID) && years > 15) {
					continue;
				}
				if (encounterTypeUuid.equals(HIGH_VL_FORM_ENCOUNTERTYPE_UUID) && !hasHighVL) {
					continue;
				}
			}
			
			// Add form to the final list
			formList.add(formObj);
		}
		
		allFormsObj.put("results", formList);
		return allFormsObj.toString();
	}
	
	/**
	 * Helper method to create a JSON representation of a form.
	 * 
	 * @param form
	 * @param encounterTypeUuid
	 * @return
	 */
	private ObjectNode createFormObject(Form form, String encounterTypeUuid) {
		ObjectNode formObj = JsonNodeFactory.instance.objectNode();
		formObj.put("id", form.getId());
		formObj.put("uuid", form.getUuid());
		formObj.put("version", form.getVersion());
		formObj.put("display", form.getName());
		formObj.put("description", form.getDescription());
		
		// Include the detailed encounterType object
		EncounterType encounterType = form.getEncounterType();
		if (encounterType != null) {
			ObjectNode encounterTypeObj = JsonNodeFactory.instance.objectNode();
			encounterTypeObj.put("uuid", encounterType.getUuid());
			encounterTypeObj.put("name", encounterType.getName());
			encounterTypeObj.put("viewPrivilege",
			    String.valueOf(encounterType.getViewPrivilege() != null ? encounterType.getViewPrivilege() : null));
			encounterTypeObj.put("editPrivilege",
			    String.valueOf(encounterType.getEditPrivilege() != null ? encounterType.getEditPrivilege() : null));
			
			formObj.put("encounterType", encounterTypeObj);
		} else {
			formObj.put("encounterType", "No encounter type associated");
		}
		
		return formObj;
	}
	
	/**
	 * Fetches the last encounter for a given patient and encounter type.
	 * 
	 * @param patient The patient
	 * @param encounterType The encounter type
	 * @return The most recent encounter of the given type for the patient, or null if none exist
	 */
	private Encounter getLastEncounterForType(Patient patient, EncounterType encounterType) {
		List<Encounter> encounters = Context.getEncounterService().getEncounters(patient, null, null, null, null,
		    Collections.singletonList(encounterType), null, null, null, false);
		if (!encounters.isEmpty()) {
			// Sort encounters by date, descending
			encounters.sort((e1, e2) -> e2.getEncounterDatetime().compareTo(e1.getEncounterDatetime()));
			return encounters.get(0);
		}
		return null;
	}
}
