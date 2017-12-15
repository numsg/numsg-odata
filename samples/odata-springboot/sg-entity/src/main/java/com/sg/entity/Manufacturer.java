package com.sg.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.List;

/**
 * Created by gaoqiang on 2017/4/25.
 */
@Entity(name = "MANUFACTURER")
public class Manufacturer {

    @Id
    @Column
    private int Id;

    @Column
    private String Name;

    @OneToMany(mappedBy = "Manufacturer")
    private List<Car> Cars;

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public List<Car> getCars() {
        return Cars;
    }

    public void setCars(List<Car> cars) {
        Cars = cars;
    }

    public int getId() {
        return Id;
    }

    public void setId(int id) {
        Id = id;
    }
}