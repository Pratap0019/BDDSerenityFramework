package com.bhanu.steps;

import com.bhanu.pages.GooglePage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class GoogleSearchSteps {

    GooglePage googlePage;

    @Given("the user opens the google homepage")
    public void the_user_opens_the_google_homepage() {
        googlePage.getDriver().get("https://www.google.com");
    }

    @When("they enter the search parameter {string}")
    public void they_enter_the_search_parameter(String searchQuery) {
        googlePage.searchBox.sendKeys(searchQuery);
    }

    @Then("the user click on the search button")
    public void the_user_click_on_the_search_button() {
        googlePage.searchBox.submit(); // Press Enter
    }

    @Then("the user should see the search results")
    public void the_user_should_see_the_search_results() {
        // Verify that the search results are displayed
        String pageTitle = googlePage.getDriver().getTitle();
        if (!pageTitle.contains("Google Search")) {
            throw new AssertionError("Search results not displayed as expected.");
        }
    }
}