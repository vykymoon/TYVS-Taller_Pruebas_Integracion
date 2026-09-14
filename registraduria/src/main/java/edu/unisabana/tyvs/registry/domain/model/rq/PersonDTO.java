package edu.unisabana.tyvs.registry.domain.model.rq;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
public class PersonDTO {

    @NotBlank(message = "name no puede estar vacio")
    private String name;

    @NotNull(message = "id es obligatorio")
    @Positive(message = "id debe ser un numero positivo")
    private Integer id;

    @NotNull(message = "age es obligatorio")
    private Integer age;

    @NotBlank(message = "gender no puede estar vacio")
    private String gender;

    private boolean alive;

    public PersonDTO() {
    }

    public PersonDTO(String name, Integer id, Integer age, String gender, boolean alive) {
        this.name = name;
        this.id = id;
        this.age = age;
        this.gender = gender;
        this.alive = alive;
    }

    public String getName() {
        return name;
    }

    public Integer getId() {
        return id;
    }

    public Integer getAge() {
        return age;
    }

    public String getGender() {
        return gender;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }
}