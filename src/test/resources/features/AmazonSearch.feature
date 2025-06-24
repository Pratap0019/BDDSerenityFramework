@testing
Feature: Search and add product to cart on Amazon

  Scenario: Search, Add and verify whether product is added in cart
    Given the user is on the Amazon home page
    When the user searches for "iPhone"
    And the user adds the first product from the search results to the cart
    And the user navigates to the Cart page
    Then the user should see the added product in the Cart
