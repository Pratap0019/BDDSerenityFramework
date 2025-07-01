@testingAPI
Feature: Petstore API GET

  Scenario: Get pets by status - available
    When I send a GET request to find pets by status "available"
    Then the response status should be 200
    And all pets returned should have status "available"
    And the number of pets should be more than 5

  Scenario: Verify header information
    When I send a GET request to find pets by status "available"
    Then the response should have content-type "application/json"
    And the response should include header "Access-Control-Allow-Methods"

  Scenario: Invalid endpoint test
    When I send a GET request to an invalid endpoint
    Then the response status should be 404
