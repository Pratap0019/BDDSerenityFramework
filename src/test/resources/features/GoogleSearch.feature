Feature: Google search functionality

  Scenario: Validate google search
    Given the user opens the google homepage
    When they enter the search parameter "Iphone"
    And the user click on the search button
    Then the user should see the search results
