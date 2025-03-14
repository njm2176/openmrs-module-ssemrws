package org.openmrs.module.ssemrws.web.dto;

import lombok.Data;

import java.util.List;

@Data
public class PatientObservations {
	
	private String enrollmentDate;
	
	private String lastRefillDate;
	
	private String dateOfinitiation;
	
	private String arvRegimen;
	
	private Double lastCD4Count;
	
	private String cd4Done;
	
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
	
	private String lastVisitDate;
	
	private String tbNumber;
	
	private String iitRecurrence;
	
	// Add a list for General family member observations
	private List<FamilyMemberObservation> familyMembers;
	
	@Data
	public static class FamilyMemberObservation {
		
		private String name;
		
		private Double age;
		
		private String sex;
		
		private String hivStatus;
		
		private String artNumber;
	}
	
	// Add a list for Close/Index family member observations (Wife/Husband, Children
	// under 12 years old)
	private List<IndexFamilyMemberObservation> indexFamilyMembers;
	
	@Data
	public static class IndexFamilyMemberObservation {
		
		private String name;
		
		private String age;
		
		private String sex;
		
		private String relationship;
		
		private String hivStatusKnown;
		
		private String hivStatus;
		
		private String phone;
		
		private String uniqueArtNumber;
	}
}
