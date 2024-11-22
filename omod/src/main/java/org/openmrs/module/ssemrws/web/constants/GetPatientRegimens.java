package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.ssemrws.queries.GetInterruptedInTreatment;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;

@Component
public class GetPatientRegimens {
	
	private final GetInterruptedInTreatment getInterruptedInTreatment;
	
	public GetPatientRegimens(GetInterruptedInTreatment getInterruptedInTreatment) {
		this.getInterruptedInTreatment = getInterruptedInTreatment;
	}
	
	public Object getPatientsOnRegimenTreatment(String qStartDate, String qEndDate, List<String> regimenConceptUuids,
	        String activeRegimenConceptUuid) throws ParseException {

		if (qStartDate == null || qStartDate.isEmpty()) {
			throw new IllegalArgumentException("Start date cannot be null or empty");
		}
		if (qEndDate == null || qEndDate.isEmpty()) {
			throw new IllegalArgumentException("End date cannot be null or empty");
		}
		if (regimenConceptUuids == null || regimenConceptUuids.isEmpty()) {
			throw new IllegalArgumentException("Regimen concept UUIDs cannot be null or empty");
		}
		if (activeRegimenConceptUuid == null || activeRegimenConceptUuid.isEmpty()) {
			throw new IllegalArgumentException("Active regimen concept UUID cannot be null or empty");
		}


		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		List<Obs> regimenTreatmentObs = getRegimenTreatmentObservations(dates, regimenConceptUuids,
		    activeRegimenConceptUuid);
		Map<Integer, Obs> latestObsByPatient = getLatestObservationsByPatient(regimenTreatmentObs);
		
		HashSet<Patient> excludedPatients = getExcludedPatients(dates);
		
		Map<String, Integer> regimenCounts = countRegimens(latestObsByPatient, excludedPatients);
		
		return prepareResults(regimenCounts);
	}
	
	private List<Obs> getRegimenTreatmentObservations(Date[] dates, List<String> regimenConceptUuids,
	        String activeRegimenConceptUuid) {
		List<Concept> regimenConcepts = getConceptsByUuids(regimenConceptUuids);
		return Context.getObsService().getObservations(null, null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(activeRegimenConceptUuid)),
		    regimenConcepts, null, null, null, 0, null, dates[0], dates[1], false);
	}
	
	private Map<Integer, Obs> getLatestObservationsByPatient(List<Obs> regimenTreatmentObs) {
		Map<Integer, Obs> latestObsByPatient = new HashMap<>();
		for (Obs obs : regimenTreatmentObs) {
			Integer patientId = obs.getPerson().getPersonId();
			if (!latestObsByPatient.containsKey(patientId)
			        || obs.getObsDatetime().after(latestObsByPatient.get(patientId).getObsDatetime())) {
				latestObsByPatient.put(patientId, obs);
			}
		}
		return latestObsByPatient;
	}
	
	private HashSet<Patient> getExcludedPatients(Date[] dates) {
		HashSet<Patient> deceasedPatients = getDeceasedPatientsByDateRange(dates[0], dates[1]);
		HashSet<Patient> transferredOutPatients = getTransferredOutClients(dates[0], dates[1]);
		HashSet<Patient> iitPatients = getInterruptedInTreatment.getIit(dates[0], dates[1]);
		
		HashSet<Patient> excludedPatients = new HashSet<>();
		excludedPatients.addAll(deceasedPatients);
		excludedPatients.addAll(transferredOutPatients);
		excludedPatients.addAll(iitPatients);
		
		return excludedPatients;
	}
	
	private Map<String, Integer> countRegimens(Map<Integer, Obs> latestObsByPatient, HashSet<Patient> excludedPatients) {
		Map<String, Integer> regimenCounts = new HashMap<>();
		for (Obs obs : latestObsByPatient.values()) {
			Patient patient = Context.getPatientService().getPatient(obs.getPerson().getPersonId());
			if (!excludedPatients.contains(patient)) {
				Concept regimenConcept = obs.getValueCoded();
				if (regimenConcept != null) {
					if(regimenConcept.getName() != null) {
						String conceptName = regimenConcept.getName().getName();
						regimenCounts.put(conceptName, regimenCounts.getOrDefault(conceptName, 0) + 1);
					}
				}
			}
		}
		return regimenCounts;
	}
	
	private Map<String, Object> prepareResults(Map<String, Integer> regimenCounts) {
		Map<String, Object> results = new HashMap<>();
		List<Map<String, Object>> regimenList = new ArrayList<>();
		
		for (Map.Entry<String, Integer> entry : regimenCounts.entrySet()) {
			Map<String, Object> regimenEntry = new HashMap<>();
			regimenEntry.put("text", entry.getKey());
			regimenEntry.put("total", entry.getValue());
			regimenList.add(regimenEntry);
		}
		
		results.put("results", regimenList);
		return results;
	}
}
