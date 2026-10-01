package com.medchart.ehr.dto;

import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.MaritalStatus;
import lombok.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientDTO {

    private Long id;
    private String mrn;

    @NotBlank(message = "First name is required")
    private String firstName;

    private String middleName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private Gender gender;
    private MaritalStatus maritalStatus;

    private String email;
    private String phoneHome;
    private String phoneMobile;
    private String phoneWork;

    private AddressDTO address;
    private AddressDTO mailingAddress;

    private String preferredLanguage;
    private String ethnicity;
    private String race;
    private String religion;

    private List<PatientIdentifierDTO> identifiers;
    private List<EmergencyContactDTO> emergencyContacts;

    private Boolean active;
    private Boolean deceased;
    private LocalDateTime deceasedDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getFullName() {
        if (middleName != null && !middleName.isBlank()) {
            return firstName + " " + middleName + " " + lastName;
        }
        return firstName + " " + lastName;
    }

    public Integer getAge() {
        if (dateOfBirth == null) return null;
        return java.time.Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}
