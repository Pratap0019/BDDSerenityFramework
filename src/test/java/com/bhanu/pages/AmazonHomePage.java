package com.bhanu.pages;

import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.core.annotations.findby.FindBy;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

public class AmazonHomePage extends PageObject {

    @FindBy(xpath = "//button[@type='submit']")
    WebElement continueButton;

    @FindBy(name = "field-keywords")
    WebElement searchBox;

    @FindBy(xpath = "//a/h2/span")
    List<WebElement> searchItemTitles;

    @FindBy(id = "productTitle")
    WebElement productTitle;

    @FindBy(name = "submit.add-to-cart")
    WebElement addToCartButton;

    @FindBy(id = "nav-cart")
    WebElement cartButton;

    @FindBy(xpath = "//li//h4/span/span")
    List <WebElement> cartItemTitle;


    public void openAmazonHomePage() {
        this.openUrl("https://www.amazon.com/");
        getDriver().manage().window().maximize();
    }

    public void searchFor(String searchTerm) {
        continueButton.click();
        searchBox.clear();
        searchBox.sendKeys(searchTerm);
        searchBox.submit();
    }

    public void clickFirstSearchResult() {
        if (!searchItemTitles.isEmpty()) {
            WebElement firstResult = searchItemTitles.get(0);
            waitForCondition().until(ExpectedConditions.elementToBeClickable(firstResult)).click();
        } else {
            throw new RuntimeException("No search results found.");
        }
    }

    public String getFirstProductTitle() {
        return productTitle.getText();
    }

    public void clickAddToCart() {
        waitForCondition().until(ExpectedConditions.elementToBeClickable(addToCartButton)).click();
    }

    public void goToCart() {
        cartButton.click();
    }

    public boolean isProductInCart(String expectedTitle) {
        return cartItemTitle.stream().anyMatch(item -> item.getText().contains(expectedTitle));
    }
}

