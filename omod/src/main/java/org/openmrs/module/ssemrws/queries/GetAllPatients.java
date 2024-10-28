package org.openmrs.module.ssemrws.queries;

import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;
import static org.openmrs.module.ssemrws.constants.SharedConstants.getPatientIdentifiersArray;
import static org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier.fetchPatientsByIds;

@Component
public class GetAllPatients {
	
	private final GetNextAppointmentDate getNextAppointmentDate;
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public GetAllPatients(GetNextAppointmentDate getNextAppointmentDate) {
		this.getNextAppointmentDate = getNextAppointmentDate;
	}
	
	public HashSet<Patient> getAllPatients(int page, int size) {
		List<Integer> patientIds = executeAllPatientsQuery(page, size);
		
		return fetchPatientsByIds(patientIds);
	}
	
	private List<Integer> executeAllPatientsQuery(int page, int size) {
		String baseQuery = "select distinct p.patient_id from openmrs.patient p where p.voided = 0 order by p.patient_id desc limit :limit offset :offset";
		try {
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("limit", size).setParameter("offset",
			    page * size);
			
			return query.getResultList();
		}
		catch (Exception e) {
			System.err.println("Error executing all patients query: " + e.getMessage());
			throw new RuntimeException("Failed to execute all patients query", e);
		}
	}
	
	public Object allPatientsListObj(HashSet<Patient> allPatients, ObjectNode allPatientsObj) {
		
		// Initialize patient list array
		ArrayNode patientList = JsonNodeFactory.instance.arrayNode();
		
		// Loop through all patients and generate the required patient objects
		for (Patient patient : allPatients) {
			ObjectNode patientObj = generateAllPatientObject(patient);
			patientList.add(patientObj);
		}
		
		// Populate the ObjectNode with the patient list
		allPatientsObj.put("results", patientList);
		
		// Return the object as a JSON string
		return allPatientsObj.toString();
	}
	
	private ObjectNode generateAllPatientObject(Patient patient) {
		ObjectNode patientObj = JsonNodeFactory.instance.objectNode();
		String artRegimen = getARTRegimen(patient);
		String dateEnrolled = getEnrolmentDate(patient);
		String artInitiationDate = getEnrolmentDate(patient);
		String lastRefillDate = getLastRefillDate(patient);
		String artAppointmentDate = getNextAppointmentDate.getNextArtAppointmentDate(patient);
		
		// Calculate age in years based on patient's birthdate and current date
		Date birthdate = patient.getBirthdate();
		Date currentDate = new Date();
		long age = (currentDate.getTime() - birthdate.getTime()) / (1000L * 60 * 60 * 24 * 365);
		
		ArrayNode identifiersArray = getPatientIdentifiersArray(patient);
		
		// Populate common fields
		patientObj.put("name", patient.getPersonName() != null ? patient.getPersonName().toString() : "");
		patientObj.put("uuid", patient.getUuid());
		patientObj.put("sex", patient.getGender());
		patientObj.put("age", age);
		patientObj.put("identifiers", identifiersArray);
		patientObj.put("ARTRegimen", artRegimen);
		patientObj.put("initiationDate", artInitiationDate);
		patientObj.put("dateEnrolled", dateEnrolled);
		patientObj.put("lastRefillDate", lastRefillDate);
		patientObj.put("appointmentDate", artAppointmentDate);
		
		return patientObj;
	}
}
