package com.tms.thesissystem.domain;

public record User(
        Long id,
        UserRole role,
        String loginId,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String departmentName,
        String program
) {
    public String fullName() {
        return firstName + " " + lastName;
    }
}
