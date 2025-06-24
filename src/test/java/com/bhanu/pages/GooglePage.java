package com.bhanu.pages;

import net.serenitybdd.core.pages.PageObject;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class GooglePage extends PageObject {

   @FindBy (name = "q")
    public WebElement searchBox;
}
