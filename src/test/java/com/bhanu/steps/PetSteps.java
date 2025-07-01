package com.bhanu.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import net.serenitybdd.rest.SerenityRest;
import org.hamcrest.Matchers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class PetSteps {

    private Response response;

    @When("I send a GET request to find pets by status {string}")
    public void getPetsByStatus(String status) {
        response = SerenityRest
                .given()
                .contentType("application/json")
                .get("https://petstore.swagger.io/v2/pet/findByStatus?status=" + status);
    }

    @When("I send a GET request to an invalid endpoint")
    public void getInvalidRequest() {
        response = SerenityRest
                .given()
                .contentType("application/json")
                .get("https://petstore.swagger.io/v2/pet/invalid");
    }

    @Then("the response status should be {int}")
    public void validateStatusCode(int statusCode) {
        response.then().statusCode(statusCode);
    }

    @Then("all pets returned should have status {string}")
    public void validatePetStatuses(String expectedStatus) {
        response.then().body("status", Matchers.everyItem(equalTo(expectedStatus)));
    }

    @Then("the number of pets should be more than {int}")
    public void validatePetCount(int min) {
        int count = response.jsonPath().getList("id").size();
        System.out.println("Total pets returned: " + count);
        assertThat("Pets count is sufficient", count > min);
    }

    @Then("the response should have content-type {string}")
    public void validateContentType(String contentType) {
        response.then().header("Content-Type", Matchers.containsString(contentType));
    }

    @Then("the response should include header {string}")
    public void validateHeaderExists(String headerName) {
        response.then().header(headerName, notNullValue());
    }

    @When("I create a pet with ID {int} and name {string}")
    public void createPet(int id, String name) {
        String body = String.format("""
        {
          "id": %d,
          "name": "%s",
          "status": "available"
        }
        """, id, name);

        response = SerenityRest
                .given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("https://petstore.swagger.io/v2/pet/");
    }

    @Then("the pet should be created successfully")
    public void verifyPetCreated() {
        response.then()
                .statusCode(200)
                .body("name", notNullValue())
                .body("status", equalTo("available"));
    }

    @When("I delete the pet with ID {int}")
    public void deletePet(int id) {
        response = SerenityRest
                .given()
                .contentType("application/json")
                .when()
                .delete("https://petstore.swagger.io/v2/pet/" + id);
    }

    @Then("the pet should be deleted successfully")
    public void verifyPetDeleted() {
        response.then()
                .statusCode(200)
                .body("message", equalTo("99999"));
    }

}

