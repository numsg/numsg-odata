package com.sg.entity;

import jdk.nashorn.internal.ir.annotations.Ignore;

import javax.persistence.*;
import java.util.List;

/**
 * Created by gaoqiang on 2017/4/27.
 */
@Entity(name = "PEOPLE")
public class People {
    /**
     * 人员ID
     */
    @Id
    @Column(name = "ID", length = 36, nullable = false)
    
    private String PersonId;

    /**
     * 警情编号
     */
    @Column(name = "P_NAME", length = 128, nullable = false)
    private String PersonName;

    /**
     * 警情编号
     */
    @Column(name = "P_GENDER", length = 4, nullable = false)
    private Gender  gender;

    @OneToMany(mappedBy = "People")
    private List<Car> Cars;

    @OneToOne(mappedBy = "People")
    private Driver Driver;

    public String getPersonId() {
        return PersonId;
    }

    public String getPersonName() {
        return PersonName;
    }

    public void setPersonName(String personName) {
        PersonName = personName;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }


    public List<Car> getCars() {
        return Cars;
    }

    public void setCars(List<Car> cars) {
        Cars = cars;
    }

    public Driver getDriver() {
        return Driver;
    }

    public void setDriver(Driver driver) {
        Driver = driver;
    }
}
