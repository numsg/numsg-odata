package com.sg.entity;

import javax.persistence.*;

/**
 * Created by gaoqiang on 2017/4/28.
 */

@Entity(name = "Driver")
public class Driver {
    /**
     * 司机ID
     */
    @Id
    @Column(name = "ID", length = 36, nullable = false)
    private String DriverId;

    /**
     * 编号
     */
    @Column(name = "DRIVERNUMBER", length = 36, nullable = false)
    private String DriverNumber;

    @OneToOne(fetch = FetchType.LAZY,cascade = CascadeType.ALL, optional = false)
    @PrimaryKeyJoinColumn
    private People People;

    public String getDriverId() {
        return DriverId;
    }

    public void setDriverId(String driverId) {
        DriverId = driverId;
    }

    public String getDriverNumber() {
        return DriverNumber;
    }

    public void setDriverNumber(String driverNumber) {
        DriverNumber = driverNumber;
    }

    public People getPeople() {
        return People;
    }

    public void setPeople(People people) {
        People = people;
    }
}
