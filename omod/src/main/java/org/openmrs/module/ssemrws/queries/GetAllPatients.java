package org.openmrs.module.ssemrws.queries;

import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

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
	
	/**
	 * A generic helper method to build a list of patient JSON objects. It abstracts away the looping
	 * and JSON construction logic.
	 * 
	 * @param patients The set of patients to process.
	 * @param containerNode The ObjectNode to which the results will be added.
	 * @param patientJsonGenerator A function that takes a Patient and returns an ObjectNode.
	 * @return The containerNode serialized to a String.
	 */
	private String buildPatientList(List<Patient> patients, ObjectNode containerNode,
	        Function<Patient, ObjectNode> patientJsonGenerator) {
		ArrayNode patientList = JsonNodeFactory.instance.arrayNode();
		
		for (Patient patient : patients) {
			ObjectNode patientObj = patientJsonGenerator.apply(patient);
			patientList.add(patientObj);
		}
		
		containerNode.put("results", patientList);
		return containerNode.toString();
	}
	
	public String allPatientsListObj(List<Patient> allPatients, ObjectNode allPatientsObj) {
		return buildPatientList(allPatients, allPatientsObj, this::generateAllPatientObject);
	}
	
	public String filteredPatientsListObj(List<Patient> allPatients, ObjectNode allPatientsObj) {
		return buildPatientList(allPatients, allPatientsObj, this::generateFilteredPatientObject);
	}
	
	private ObjectNode createBasePatientObject(Patient patient) {
		ObjectNode patientObj = JsonNodeFactory.instance.objectNode();
		
		LocalDate birthDate = patient.getBirthdate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		int age = Period.between(birthDate, LocalDate.now()).getYears();
		int patientId = patient.getPatientId();
		
		ArrayNode identifiersArray = getPatientIdentifiersArray(patient);
		
		patientObj.put("name", patient.getPersonName() != null ? patient.getPersonName().toString() : "");
		patientObj.put("uuid", patient.getUuid());
		patientObj.put("patientId", patientId);
		patientObj.put("sex", patient.getGender());
		patientObj.put("age", age);
		patientObj.put("identifiers", identifiersArray);
		
		return patientObj;
	}
	
	/**
	 * Generates a complete JSON object for a patient, including all ART details.
	 */
	public ObjectNode generateAllPatientObject(Patient patient) {
		ObjectNode patientObj = createBasePatientObject(patient);
		
		String enrolmentDate = getEnrolmentDate(patient);
		
		patientObj.put("ARTRegimen", getARTRegimen(patient));
		patientObj.put("initiationDate", enrolmentDate);
		patientObj.put("dateEnrolled", enrolmentDate);
		patientObj.put("lastRefillDate", getLastRefillDate(patient));
		patientObj.put("appointmentDate", getNextAppointmentDate.getNextArtAppointmentDate(patient));
		
		return patientObj;
	}
	
	/**
	 * Generates a filtered or summary JSON object for a patient.
	 */
	public ObjectNode generateFilteredPatientObject(Patient patient) {
		return createBasePatientObject(patient);
	}
}
