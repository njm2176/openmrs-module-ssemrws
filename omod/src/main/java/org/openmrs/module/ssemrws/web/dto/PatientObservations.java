package org.openmrs.module.ssemrws.web.dto;

import ca.uhn.hl7v2.model.v23.datatype.ST;
import lombok.Data;

@Data
public class PatientObservations {
	
	private String enrollmentDate;
	
	private String lastRefillDate;
	
	private String dateOfinitiation;
	
	private String arvRegimen;
	
	private Double lastCD4Count;
	
	private String tbStatus;
	
	private String arvRegimenDose;
	
	private String whoClinicalStage;
	
	private String dateVLResultsReceived;
	
	private String chwName;
	
	private String chwPhone;
	
	private String chwAddress;
	
	private String vlResults;
	
	private String vlStatus;
	
	private Double bmi;
	
	private Double muac;
	
	private String appointmentDate;
	
	private String clinicianName;
	
	private String vlEligibility;
	
	private String vlDueDate;
}
