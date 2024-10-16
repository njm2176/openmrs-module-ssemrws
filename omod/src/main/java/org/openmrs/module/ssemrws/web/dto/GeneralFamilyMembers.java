package org.openmrs.module.ssemrws.web.dto;

public class GeneralFamilyMembers {
	
	private String familyMemberName;
	
	private String familyMemberRelationship;
	
	private String familyMemberAge;
	
	private String familyMemberSex;
	
	public void setFamilyMemberName(String familyMemberName) {
		this.familyMemberName = familyMemberName;
	}
	
	public String getFamilyMemberName() {
		return familyMemberName;
	}
	
	public void setFamilyMemberRelationship(String familyMemberRelationship) {
		this.familyMemberRelationship = familyMemberRelationship;
	}
	
	public String getFamilyMemberRelationship() {
		return familyMemberRelationship;
	}
	
	public void setFamilyMemberAge(String familyMemberAge) {
		this.familyMemberAge = familyMemberAge;
	}
	
	public String getFamilyMemberAge() {
		return familyMemberAge;
	}
	
	public void setFamilyMemberSex(String familyMemberSex) {
		this.familyMemberSex = familyMemberSex;
	}
	
	public String getFamilyMemberSex() {
		return familyMemberSex;
	}
}
