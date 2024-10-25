package org.openmrs.module.ssemrws.web.dto;

import lombok.Data;

@Data
public class AppointmentDTO {
	
	private String name;
	
	private String uuid;
	
	private String appointmentDate;
	
	private String appointmentStatus;
	
	private String contact;
	
	private String alternateContact;
	
}
