package com.bhanu.steps;

import com.bhanu.pages.AmazonHomePage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.annotations.Managed;

import static net.thucydides.core.webdriver.ThucydidesWebDriverSupport.getDriver;
import static org.hamcrest.MatcherAssert.assertThat;

public class AmazonSearchSteps {


    AmazonHomePage amazon;
    String firstProductTitle = "";

    @Given("the user is on the Amazon home page")
    public void the_user_is_on_the_amazon_home_page() {
        amazon.openAmazonHomePage();
    }

    @When("the user searches for {string}")
    public void the_user_searches_for(String searchTerm) {
        amazon.searchFor(searchTerm);
    }

    @When("the user adds the first product from the search results to the cart")
    public void the_user_adds_first_product_to_cart() {
        amazon.clickFirstSearchResult();
       firstProductTitle = amazon.getFirstProductTitle();

        for (String handle : getDriver().getWindowHandles()) {
            getDriver().switchTo().window(handle);
        }

        amazon.clickAddToCart();
    }

    @When("the user navigates to the Cart page")
    public void the_user_navigates_to_cart_page() {
        amazon.goToCart();
    }

    @Then("the user should see the added product in the Cart")
    public void the_user_should_see_added_product_in_cart() {
        boolean isProductInCart = amazon.isProductInCart(firstProductTitle);
        assertThat("The product should be in the cart", isProductInCart);
    }
}
