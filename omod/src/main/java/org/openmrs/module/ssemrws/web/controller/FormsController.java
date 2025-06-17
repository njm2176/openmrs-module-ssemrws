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

import java.text.SimpleDateFormat;
import java.time.Period;
import java.util.List;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.web.constants.AllConcepts.*;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ssemr")
public class FormsController {
	
	/**
	 * Gets a list of available/completed forms for a patient
	 * 
	 * @param patientUuid
	 * @return
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/forms")
	@ResponseBody
	public Object getAllAvailableFormsForVisit(@RequestParam("patientUuid") String patientUuid) {
		if (StringUtils.isBlank(patientUuid)) {
			return new ResponseEntity<>("You must specify patientUuid in the request!", HttpStatus.BAD_REQUEST);
		}
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		if (patient == null) {
			return new ResponseEntity<>("The provided patient was not found in the system!", HttpStatus.NOT_FOUND);
		}
		
		Visit latestVisit = getLatestActiveVisit(patient);
		if (latestVisit == null) {
			return new ResponseEntity<>("The patient has no active visits.", HttpStatus.NOT_FOUND);
		}
		
		Period patientAge = calculatePatientAge(patient);
		boolean hasHighVL = hasHighViralLoad(patient);
		
		String jsonResponse = buildJsonResponse(patient, latestVisit, patientAge, hasHighVL);
		
		return new ResponseEntity<>(jsonResponse, new HttpHeaders(), HttpStatus.OK);
	}
	
	/**
	 * Helper method to create a JSON representation of a form. (This is the method you provided)
	 * * @param form
	 * 
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
		
		EncounterType encounterType = form.getEncounterType();
		if (encounterType != null) {
			ObjectNode encounterTypeObj = JsonNodeFactory.instance.objectNode();
			encounterTypeObj.put("uuid", encounterType.getUuid());
			encounterTypeObj.put("name", encounterType.getName());
			encounterTypeObj.put("viewPrivilege", String
			        .valueOf(encounterType.getViewPrivilege() != null ? encounterType.getViewPrivilege().getName() : null));
			encounterTypeObj.put("editPrivilege", String
			        .valueOf(encounterType.getEditPrivilege() != null ? encounterType.getEditPrivilege().getName() : null));
			
			formObj.put("encounterType", encounterTypeObj);
		} else {
			formObj.putNull("encounterType");
		}
		
		return formObj;
	}
	
	/**
	 * Checks if the patient has a high viral load (>= 1000).
	 */
	private boolean hasHighViralLoad(Patient patient) {
		String vlResultString = getVLResultsFromFollowUpForm(patient);
		if (vlResultString != null) {
			try {
				return Double.parseDouble(vlResultString) >= 1000;
			}
			catch (NumberFormatException e) {
				System.err.println("Unable to parse VL result: " + vlResultString);
			}
		}
		return false;
	}
	
	/**
	 * Constructs the main JSON response object.
	 */
	private String buildJsonResponse(Patient patient, Visit latestVisit, Period patientAge, boolean hasHighVL) {
		ObjectNode responseNode = JsonNodeFactory.instance.objectNode();
		
		responseNode.put("patientName", patient.getGivenName() + " " + patient.getFamilyName());
		responseNode.put("patientUuid", patient.getUuid());
		responseNode.put("patientAge", formatAge(patientAge));
		responseNode.put("latestVisitDate",
		    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(latestVisit.getStartDatetime()));
		
		responseNode.put("results", getFilteredFormsAsJson(patient, patientAge, hasHighVL));
		
		return responseNode.toString();
	}
	
	/**
	 * Fetches all published forms, filters them based on patient criteria, and builds a JSON array.
	 */
	private ArrayNode getFilteredFormsAsJson(Patient patient, Period patientAge, boolean hasHighVL) {
		ArrayNode formList = JsonNodeFactory.instance.arrayNode();
		List<Form> allPublishedForms = Context.getFormService().getPublishedForms();
		
		for (Form form : allPublishedForms) {
			if (shouldIncludeForm(form, patientAge.getYears(), hasHighVL)) {
				EncounterType encounterType = form.getEncounterType();
				String encounterTypeUuid = (encounterType != null) ? encounterType.getUuid() : null;
				
				ObjectNode formObj = createFormObject(form, encounterTypeUuid);
				
				if (encounterType != null) {
					Encounter lastEncounter = getLastEncounterForType(patient, encounterType);
					formObj.put("lastFilledDate",
					    lastEncounter != null ? lastEncounter.getEncounterDatetime().toString() : "Never filled");
				} else {
					formObj.put("lastFilledDate", "No encounter type associated");
				}
				
				formList.add(formObj);
			}
		}
		return formList;
	}
	
	/**
	 * Determines if a form should be included based on patient age and VL status.
	 */
	private boolean shouldIncludeForm(Form form, int ageInYears, boolean hasHighVL) {
		String encounterTypeUuid = (form.getEncounterType() != null) ? form.getEncounterType().getUuid() : null;
		if (encounterTypeUuid == null) {
			return true;
		}
		
		if (encounterTypeUuid.equals(ADULT_AND_ADOLESCENT_INTAKE_FORM) && ageInYears <= 15)
			return false;
		if (encounterTypeUuid.equals(PEDIATRIC_INTAKE_FORM) && ageInYears > 15)
			return false;
		if (encounterTypeUuid.equals(HIGH_VL_ENCOUNTERTYPE_UUID) && !hasHighVL)
			return false;
		
		return true;
	}
}
