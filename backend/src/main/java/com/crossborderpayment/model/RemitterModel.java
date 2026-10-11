package com.crossborderpayment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class RemitterModel extends BaseModel{
    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String familyName;

    @Column(nullable = false, unique = true)
    private String emailAddr;

    public RemitterModel(){
    }

    public RemitterModel(Long id, String firstName, String familyName, String emailAddr){
        super(id);
        this.firstName = firstName;
        this.familyName = familyName;
        this.emailAddr = emailAddr;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getEmailAddr() {
        return emailAddr;
    }

    public void setEmailAddr(String emailAddr) {
        this.emailAddr = emailAddr;
    }
}
