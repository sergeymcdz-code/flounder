package com.acme.sportplatform.identity.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateUserRequest {
        @Pattern(regexp = "^\\d{11,15}$", message = "Phone must contain 11 to 15 digits")
        private String phone;
        
        @Size(max = 32, message = "Status must be at most 32 characters")
        private String status;

        @Size(max = 128, message = "First name must be at most 128 characters")
        private String firstName;
        
        @Size(max = 128, message = "Last name must be at most 128 characters")
        private String lastName;

        @Size(max = 128, message = "Middle name must be at most 128 characters")
        private String middleName;

        private LocalDate birthDate;
        
        @Size(max = 16, message = "Gender must be at most 16 characters")
        private String gender;

        @Size(max = 128, message = "City must be at most 128 characters")
        private String city;
        
        @Size(max = 8, message = "Country code must be at most 8 characters")
        private String countryCode;

        @Size(max = 255, message = "Club name must be at most 255 characters")
        private String clubName;

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getMiddleName() { return middleName; }
        public void setMiddleName(String middleName) { this.middleName = middleName; }

        public LocalDate getBirthDate() { return birthDate; }
        public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

        public String getClubName() { return clubName; }
        public void setClubName(String clubName) { this.clubName = clubName; }
}