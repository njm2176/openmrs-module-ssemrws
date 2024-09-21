package org.openmrs.module.ssemrws.web.dto;

public class AppointmentDTO {
	
	private String name;
	
	private String uuid;
	
	private String appointmentDate;
	
	private String appointmentStatus;
	
	private String contact;
	
	private String alternateContact;
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
	public String getAppointmentDate() {
		return appointmentDate;
	}
	
	public void setAppointmentDate(String appointmentDate) {
		this.appointmentDate = appointmentDate;
	}
	
	public String getAppointmentStatus() {
		return appointmentStatus;
	}
	
	public void setAppointmentStatus(String appointmentStatus) {
		this.appointmentStatus = appointmentStatus;
	}
	
}
