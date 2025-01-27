package org.openmrs.module.ssemrws.web.constants;

import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.Patient;
import org.openmrs.module.ssemrws.queries.GetDatePatientBecameIIT;
import org.openmrs.module.ssemrws.queries.GetEnrollmentDate;
import org.openmrs.module.ssemrws.queries.GetNextAppointmentDate;
import org.openmrs.module.ssemrws.queries.GetVLDueDate;
import org.openmrs.module.ssemrws.web.controller.SSEMRWebServicesController;
import org.springframework.stereotype.Component;

import java.util.Date;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.constants.SharedConstants.determineIfPatientIsPregnantOrBreastfeeding;

@Component
public class GeneratePatientObject {
	
	private final GetNextAppointmentDate getNextAppointmentDate;
	
	private final GetDatePatientBecameIIT getDatePatientBecameIIT;
	
	private final GetVLDueDate getVLDueDate;
	
	private final GetEnrollmentDate getEnrollmentDate;
	
	public GeneratePatientObject(GetNextAppointmentDate getNextAppointmentDate,
	    GetDatePatientBecameIIT getDatePatientBecameIIT, GetVLDueDate getVLDueDate, GetEnrollmentDate getEnrollmentDate) {
		this.getNextAppointmentDate = getNextAppointmentDate;
		this.getDatePatientBecameIIT = getDatePatientBecameIIT;
		this.getVLDueDate = getVLDueDate;
		this.getEnrollmentDate = getEnrollmentDate;
	}
	
	public ObjectNode generatePatientObject(Date startDate, Date endDate,
	        SSEMRWebServicesController.filterCategory filterCategory, Patient patient) {
		ObjectNode patientObj = JsonNodeFactory.instance.objectNode();
		String artRegimen = getARTRegimen(patient);
		String dateEnrolled = getEnrolmentDate(patient);
		String artInitiationDate = getEnrolmentDate(patient);
		String datePatientDied = getDateOfDeath(patient);
		String datePatientTransferredOut = getDateTransferredOut(patient);
		String dateReturnedToTreatment = getReturnToTreatmentDate(patient);
		String lastRefillDate = getLastRefillDate(patient);
		String artAppointmentDate = getNextAppointmentDate.getNextArtAppointmentDate(patient);
		String iitDate = String.valueOf(getDatePatientBecameIIT.getIitDateForPatient(patient, startDate, endDate));
		String vlDueDate = getVLDueDate.getVLDueDate(patient);
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
		patientObj.put("datePatientDied", datePatientDied);
		patientObj.put("datePatientTransferredOut", datePatientTransferredOut);
		patientObj.put("dateReturnedToTreatment", dateReturnedToTreatment);
		patientObj.put("dateClientBecameIIT", iitDate);
		patientObj.put("vlDueDate", vlDueDate);
		
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
				default:
					return null;
			}
		} else {
			return patientObj;
		}
		return null;
	}
}
