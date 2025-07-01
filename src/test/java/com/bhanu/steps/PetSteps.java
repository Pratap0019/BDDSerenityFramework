package com.bhanu.steps;

import io.restassured.response.Response;
import net.serenitybdd.rest.SerenityRest;
import org.hamcrest.Matchers;

import static org.hamcrest.MatcherAssert.assertThat;

public class PetSteps {

    private Response response;

    @io.cucumber.java.en.When("I send a GET request to find pets by status {string}")
    public void getPetsByStatus(String status) {
        response = SerenityRest
                .given()
                .contentType("application/json")
                .get("https://petstore.swagger.io/v2/pet/findByStatus?status=" + status);
    }

    @io.cucumber.java.en.When("I send a GET request to an invalid endpoint")
    public void getInvalidRequest() {
        response = SerenityRest
                .given()
                .contentType("application/json")
                .get("https://petstore.swagger.io/v2/pet/invalid");
    }

    @io.cucumber.java.en.Then("the response status should be {int}")
    public void validateStatusCode(int statusCode) {
        response.then().statusCode(statusCode);
    }

    @io.cucumber.java.en.Then("all pets returned should have status {string}")
    public void validatePetStatuses(String expectedStatus) {
        response.then().body("status", Matchers.everyItem(Matchers.equalTo(expectedStatus)));
    }

    @io.cucumber.java.en.Then("the number of pets should be more than {int}")
    public void validatePetCount(int min) {
        int count = response.jsonPath().getList("id").size();
        System.out.println("Total pets returned: " + count);
        assertThat("Pets count is sufficient", count > min);
    }

    @io.cucumber.java.en.Then("the response should have content-type {string}")
    public void validateContentType(String contentType) {
        response.then().header("Content-Type", Matchers.containsString(contentType));
    }

    @io.cucumber.java.en.Then("the response should include header {string}")
    public void validateHeaderExists(String headerName) {
        response.then().header(headerName, Matchers.notNullValue());
    }
}

